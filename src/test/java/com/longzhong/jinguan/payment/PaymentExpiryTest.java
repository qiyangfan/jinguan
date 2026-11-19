package com.longzhong.jinguan.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 验证过期回调守门和限批扫描收敛。 */
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

  private static final String CURRENCY = "CNY";
  private static final String STATUS_CREATED = "CREATED";
  private static final String STATUS_EXPIRED = "EXPIRED";
  private static final String STATUS_PAID = "PAID";

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
  void expiredOrderCallbackRejectedGuardRegardlessOfScheduler() throws Exception {
    long uid = seedUser("exp-u1");
    login(uid, "exp-u1");
    // 回调守门不依赖扫描任务是否运行。
    String orderId = "po-exp-past";
    orders.save(
        MockOrderEntity.create(orderId, uid, 500_000, CURRENCY, Instant.now().minusSeconds(1)));

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("订单已过期: " + orderId));
    assertEquals(
        STATUS_CREATED, orders.findById(orderId).orElseThrow().getStatus(), "守门拒绝不改变状态(收敛归扫描任务)");
  }

  @Test
  @Transactional
  void expiredConvergedOrderCallbackStillRejected() throws Exception {
    long uid = seedUser("exp-u1b");
    login(uid, "exp-u1b");
    String orderId = "po-exp-converged";
    var order =
        MockOrderEntity.create(orderId, uid, 500_000, CURRENCY, Instant.now().minusSeconds(1));
    orders.save(order);
    // 已过期订单不能被迟到回调改为已支付。
    orders.expireIfCreated(List.of(orderId));

    mvc.perform(post("/payments/mock/{id}/pay", orderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("订单已过期: " + orderId));
    assertEquals(
        STATUS_EXPIRED,
        orders.findById(orderId).orElseThrow().getStatus(),
        "终态不可逆:EXPIRED 不得被回调翻成 PAID");
  }

  @Test
  @Transactional
  void unexpiredOrderPayable() throws Exception {
    long uid = seedUser("exp-u2");
    login(uid, "exp-u2");
    var created = provider.createOrder("po-exp-2", uid, 500_000, CURRENCY);
    MvcResult paid =
        mvc.perform(post("/payments/mock/{id}/pay", orderIdOf(created)))
            .andExpect(jsonPath("$.status").value(STATUS_PAID))
            .andReturn();
    assertEquals(200, paid.getResponse().getStatus(), "未过期订单必须可支付");
  }

  @Test
  @Transactional
  void schedulerConvergesOverdueCreatedToExpired() {
    long uid = seedUser("exp-u3");
    orders.save(
        MockOrderEntity.create("po-exp-3", uid, 500_000, CURRENCY, Instant.now().minusSeconds(1)));
    orders.save(
        MockOrderEntity.create(
            "po-live-3", uid, 500_000, CURRENCY, Instant.now().plusSeconds(3_600)));

    scheduler.expireOverdueOrders(500);

    assertEquals(
        STATUS_EXPIRED,
        orders.findById("po-exp-3").orElseThrow().getStatus(),
        "过期订单应被扫描置为 EXPIRED");
    assertEquals(
        STATUS_CREATED, orders.findById("po-live-3").orElseThrow().getStatus(), "未过期订单不受扫描影响");
    assertEquals(0, scheduler.backlog(), "本轮扫描后积压应清零");
  }

  @Test
  @Transactional
  void batchLimitCapsSingleRoundBacklogDrainsAcrossRounds() {
    long uid = seedUser("exp-u4");
    Instant past = Instant.now().minusSeconds(1);
    for (int i = 0; i < 5; i++) {
      orders.save(MockOrderEntity.create("po-batch-" + i, uid, 100_000, CURRENCY, past));
    }
    // 单轮处理两笔，剩余积压留待下轮。
    var first = orders.findOverdue(STATUS_CREATED, Instant.now(), PageRequest.of(0, 2));
    assertEquals(2, first.size(), "单轮应受 batch 上限约束");
    orders.expireIfCreated(first.stream().map(MockOrderEntity::getOrderId).toList());
    assertEquals(
        3, orders.countByStatusAndExpiresAtBefore(STATUS_CREATED, Instant.now()), "满批后应留积压给下轮");

    // 多轮扫描直至积压清零。
    scheduler.expireOverdueOrders(2);
    assertEquals(0, scheduler.backlog(), "本轮扫描后积压应清零");
    orders.findAll().stream()
        .filter(o -> o.getOrderId().startsWith("po-batch-"))
        .forEach(o -> assertEquals(STATUS_EXPIRED, o.getStatus(), "积压订单最终应全部过期"));
  }
}
