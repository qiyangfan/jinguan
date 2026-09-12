package com.longzhong.jinguan.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.ingest.ApiKeyBindingEntity;
import com.longzhong.jinguan.ingest.ApiKeyBindingRepository;
import com.longzhong.jinguan.ingest.IngestService;
import com.longzhong.jinguan.ingest.IngestUsageRow;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 资金并发纪律回归:条带锁内含完整事务,扣账竞态不得超扣;ingest 单行失败不拖批。 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jinguan-conc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
    })
class WalletConcurrencyTest {

  @Autowired WalletService wallet;
  @Autowired UserRepository users;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired IngestService ingest;

  @Test
  void concurrent_debits_never_overdraw() throws Exception {
    var user = users.save(UserEntity.create("conc-u1", "hash", "USER"));
    long uid = user.getId();
    wallet.credit(uid, 100, WalletService.REASON_MANUAL_TOPUP, "conc-seed", null);

    int threads = 20, perDebit = 30; // 余额只够 3 笔
    var latch = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(threads);
    var success = new AtomicInteger();
    var rejected = new AtomicInteger();
    var futures = new java.util.ArrayList<Future<?>>();

    for (int i = 0; i < threads; i++) {
      final String ref = "conc-d-" + i;
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                try {
                  wallet.debit(uid, perDebit, WalletService.REASON_USAGE, ref, null);
                  success.incrementAndGet();
                } catch (InsufficientFundsException e) {
                  rejected.incrementAndGet();
                }
                return null;
              }));
    }
    latch.countDown();
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();

    assertEquals(3, success.get(), "成功笔数应恰为余额可覆盖的笔数");
    assertEquals(threads - 3, rejected.get(), "其余应被非负不变量拒绝");
    assertEquals(10, wallet.balance(uid), "终态余额 = 100 - 3×30");
    assertEquals(threads, success.get() + rejected.get(), "每笔恰好一种结局(无丢失)");
  }

  @Test
  void ingest_insufficientRow_skipped_notPoison() {
    var user = users.save(UserEntity.create("conc-u2", "hash", "USER"));
    bindings.save(new ApiKeyBindingEntity("mk-poor", user.getId()));

    var row =
        new IngestUsageRow(
            "poison-1",
            "r-1",
            "t1",
            "mk-poor",
            "deepseek",
            "deepseek-chat",
            "deepseek-chat",
            false,
            "ok",
            "",
            10,
            5,
            0,
            0,
            false,
            999999,
            "CNY",
            null,
            100,
            "2026-09-12T00:00:00Z");
    var result = ingest.accept(List.of(row));

    assertEquals(1, result.skippedInsufficient());
    assertEquals(0, result.accepted());
    // 毒丸行不阻断后续行:同批内随后一条正常行应被接受
    var good =
        new IngestUsageRow(
            "ok-1",
            "r-2",
            "t1",
            "mk-poor",
            "deepseek",
            "deepseek-chat",
            "deepseek-chat",
            false,
            "ok",
            "",
            1,
            1,
            0,
            0,
            false,
            1,
            "CNY",
            null,
            50,
            "2026-09-12T00:00:01Z");
    // 先手工入账 1μ 保证扣账可行
    wallet.credit(user.getId(), 1, WalletService.REASON_MANUAL_TOPUP, "conc-seed-2", null);
    var result2 = ingest.accept(List.of(row, good));
    assertEquals(1, result2.skippedInsufficient());
    assertEquals(1, result2.accepted());
  }
}
