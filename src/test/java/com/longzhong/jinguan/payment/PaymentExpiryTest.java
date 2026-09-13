package com.longzhong.jinguan.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 支付超时双保险回归:懒过期守门(资金正确性)+ 游标批量收敛(状态及时性/积压消化)。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-expiry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "jinguan.payment.order-ttl-minutes=60",
    })
class PaymentExpiryTest {

  @Autowired MockPaymentProvider provider;
  @Autowired MockOrderRepository orders;
  @Autowired UserRepository users;
  @Autowired MockMvc mvc;
  @Autowired PaymentExpiryScheduler scheduler;

  private long seedUser(String name) {
    return users.save(UserEntity.create(name, "hash", "USER")).getId();
  }

  private static void login(long uid, String username) {
    var principal = new com.longzhong.jinguan.security.JwtService.LoginUser(uid, username, "USER");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList(principal.authority())));
  }

  private static String orderIdOf(PaymentProvider.CreatedOrder created) {
    String url = created.payUrl(); // /payments/mock/{id}/pay
    return url.substring("/payments/mock/".length(), url.length() - "/pay".length());
  }

  @Test
  @Transactional
  void expiredOrder_callbackRejected_guardRegardlessOfScheduler() throws Exception {
    long uid = seedUser("exp-u1");
    login(uid, "exp-u1");
    // 直接过期形态(expires_at 已过去):守门不依赖扫描任务是否执行过
    String orderId = "po-exp-past";
    orders.save(MockOrderEntity.create(orderId, uid, 500000, "CNY", Instant.now().minusSeconds(1)));

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("订单已过期: " + orderId));
    assertEquals(
        "CREATED", orders.findById(orderId).orElseThrow().getStatus(), "守门拒绝不改变状态(收敛归扫描任务)");
  }

  @Test
  @Transactional
  void expired_convergedOrder_callbackStillRejected() throws Exception {
    long uid = seedUser("exp-u1b");
    login(uid, "exp-u1b");
    String orderId = "po-exp-converged";
    var order = MockOrderEntity.create(orderId, uid, 500000, "CNY", Instant.now().minusSeconds(1));
    orders.save(order);
    // 模拟扫描已收敛为 EXPIRED:迟到的回调不得把终态订单翻成 PAID(守门漏洞钉子)
    orders.expireIfCreated(java.util.List.of(orderId));

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("订单已过期: " + orderId));
    assertEquals(
        "EXPIRED",
        orders.findById(orderId).orElseThrow().getStatus(),
        "终态不可逆:EXPIRED 不得被回调翻成 PAID");
  }

  @Test
  @Transactional
  void unexpiredOrder_payable() throws Exception {
    long uid = seedUser("exp-u2");
    login(uid, "exp-u2");
    var created = provider.createOrder("po-exp-2", uid, 500000, "CNY");
    mvc.perform(post("/payments/mock/{id}/pay", orderIdOf(created)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  @Transactional
  void scheduler_convergesOverdueCreated_toExpired() {
    long uid = seedUser("exp-u3");
    orders.save(
        MockOrderEntity.create("po-exp-3", uid, 500000, "CNY", Instant.now().minusSeconds(1)));
    orders.save(
        MockOrderEntity.create("po-live-3", uid, 500000, "CNY", Instant.now().plusSeconds(3600)));

    scheduler.expireOverdueOrders(500);

    assertEquals("EXPIRED", orders.findById("po-exp-3").orElseThrow().getStatus());
    assertEquals("CREATED", orders.findById("po-live-3").orElseThrow().getStatus(), "未过期订单不受扫描影响");
    assertEquals(0, scheduler.backlog());
  }

  @Test
  @Transactional
  void batchLimit_capsSingleRound_backlogDrainsAcrossRounds() {
    long uid = seedUser("exp-u4");
    Instant past = Instant.now().minusSeconds(1);
    for (int i = 0; i < 5; i++) {
      orders.save(MockOrderEntity.create("po-batch-" + i, uid, 100000, "CNY", past));
    }
    // 单轮上限 2:第一轮恰好 2 笔(满批信号),剩余积压留给下轮
    var first = orders.findOverdue("CREATED", Instant.now(), PageRequest.of(0, 2));
    assertEquals(2, first.size(), "单轮应受 batch 上限约束");
    orders.expireIfCreated(first.stream().map(MockOrderEntity::getOrderId).toList());
    assertEquals(3, orders.countByStatusAndExpiresAtBefore("CREATED", Instant.now()), "满批后应留积压给下轮");

    // 多轮推进:batch=2 直至收敛完成(2+2+1)
    scheduler.expireOverdueOrders(2);
    assertEquals(0, scheduler.backlog());
    orders.findAll().stream()
        .filter(o -> o.getOrderId().startsWith("po-batch-"))
        .forEach(o -> assertEquals("EXPIRED", o.getStatus()));
  }
}
