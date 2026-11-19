package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.common.IdempotencyConflictException;
import com.longzhong.jinguan.common.InsufficientFundsException;
import com.longzhong.jinguan.ingest.ApiKeyBindingEntity;
import com.longzhong.jinguan.ingest.ApiKeyBindingRepository;
import com.longzhong.jinguan.ingest.IngestService;
import com.longzhong.jinguan.ingest.IngestUsageRow;
import com.longzhong.jinguan.payment.PaymentProvider;
import com.longzhong.jinguan.payment.PaymentService;
import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import com.longzhong.jinguan.wallet.WalletService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 在真 PostgreSQL 上验证资金与幂等关键路径；无 Docker 时自动跳过。 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "jinguan.settlement.initial-delay-ms=3600000",
      "jinguan.settlement.fixed-delay-ms=3600000",
    })
class PostgresBusinessFlowTest {

  private static final long SEED = 1_000_000L;
  private static final long RACE_TIMEOUT_SECONDS = 10;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport::jdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport::username);
    registry.add("spring.datasource.password", PostgresContainerSupport::password);
  }

  @Autowired IngestService ingest;
  @Autowired PaymentService payments;
  @Autowired WalletService wallet;
  @Autowired ApiKeyBindingRepository bindings;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private record Outcome<T>(T value, Throwable error) {}

  @Test
  void ingestReplayAndConcurrentDuplicateDebitOnce() throws Exception {
    String suffix = suffix();
    long userId = newUser("pg-ingest-" + suffix);
    String key = "pg-key-" + suffix;
    bindings.save(new ApiKeyBindingEntity(key, userId));
    wallet.credit(
        userId, SEED, WalletService.REASON_MANUAL_TOPUP, "pg-seed-ingest-" + suffix, null, null);

    IngestUsageRow replayRow = row("pg-usage-" + suffix, key, 100_000L);
    IngestService.IngestResult first = ingest.accept(List.of(replayRow));
    IngestService.IngestResult replay = ingest.accept(List.of(replayRow));

    IngestUsageRow raceRow = row("pg-race-" + suffix, key, 150_000L);
    List<Outcome<IngestService.IngestResult>> raced =
        runTogether(() -> ingest.accept(List.of(raceRow)), () -> ingest.accept(List.of(raceRow)));
    assertTrue(raced.stream().allMatch(outcome -> outcome.error() == null), "并发重投不得抛错");
    int accepted = raced.stream().mapToInt(outcome -> outcome.value().accepted()).sum();
    int replayed = raced.stream().mapToInt(outcome -> outcome.value().replayed()).sum();

    assertEquals(
        List.of(1, 0, 0, 1),
        List.of(first.accepted(), first.replayed(), replay.accepted(), replay.replayed()),
        "顺次重投:首投 accepted,重投 replayed");
    assertEquals(List.of(1, 1), List.of(accepted, replayed), "并发重投:恰一次扣款、一次重放");
    assertEquals(SEED - 250_000L, wallet.balance(userId), "余额只扣两笔用量");
    assertEquals(
        List.of(1L, 1L),
        List.of(ledgerEntries("usage:" + replayRow.ID()), ledgerEntries("usage:" + raceRow.ID())),
        "同一 usage 幂等键恰一分录");
  }

  @Test
  void ledgerConcurrencyKeepsNonNegativeBalanceAndAdjudicatesSharedRef() throws Exception {
    String suffix = suffix();
    long spentUser = newUser("pg-ledger-a-" + suffix);
    wallet.credit(
        spentUser, 100L, WalletService.REASON_MANUAL_TOPUP, "pg-seed-a-" + suffix, null, null);
    List<Outcome<WalletService.EntryResult>> debits =
        runTogether(
            () ->
                wallet.debit(
                    spentUser, 80L, WalletService.REASON_USAGE, "pg-debit-a-" + suffix, null, null),
            () ->
                wallet.debit(
                    spentUser,
                    80L,
                    WalletService.REASON_USAGE,
                    "pg-debit-b-" + suffix,
                    null,
                    null));
    long applied = debits.stream().filter(outcome -> outcome.error() == null).count();
    long rejected =
        debits.stream()
            .filter(outcome -> outcome.error() instanceof InsufficientFundsException)
            .count();

    long userB = newUser("pg-ledger-b-" + suffix);
    long userC = newUser("pg-ledger-c-" + suffix);
    wallet.credit(
        userB, 100L, WalletService.REASON_MANUAL_TOPUP, "pg-seed-b-" + suffix, null, null);
    wallet.credit(
        userC, 100L, WalletService.REASON_MANUAL_TOPUP, "pg-seed-c-" + suffix, null, null);
    String sharedRef = "pg-shared-" + suffix;
    List<Outcome<WalletService.EntryResult>> shared =
        runTogether(
            () -> wallet.debit(userB, 50L, WalletService.REASON_USAGE, sharedRef, null, null),
            () -> wallet.debit(userC, 50L, WalletService.REASON_USAGE, sharedRef, null, null));
    long sharedApplied = shared.stream().filter(outcome -> outcome.error() == null).count();
    long conflicts =
        shared.stream()
            .filter(outcome -> outcome.error() instanceof IdempotencyConflictException)
            .count();

    assertEquals(List.of(1L, 1L), List.of(applied, rejected), "同用户并发扣减恰一成一拒");
    assertEquals(20L, wallet.balance(spentUser), "非负余额:100 - 80");
    assertEquals(List.of(1L, 1L), List.of(sharedApplied, conflicts), "跨用户同 refId 恰一成冲突");
    assertEquals(1L, ledgerEntries(sharedRef), "唯一键胜方恰一分录");
    long balanceB = wallet.balance(userB);
    long balanceC = wallet.balance(userC);
    assertTrue(
        (balanceB == 50L && balanceC == 100L) || (balanceB == 100L && balanceC == 50L),
        "仅成功方扣款: b=" + balanceB + " c=" + balanceC);
  }

  @Test
  void duplicatePaymentCallbackCreditsOnce() throws Exception {
    String suffix = suffix();
    long userId = newUser("pg-pay-" + suffix);
    var order = payments.createOrder("mock", userId, 500L, "CNY");
    String orderId = String.valueOf(order.get("orderId"));
    PaymentProvider.CallbackPayload callback =
        new PaymentProvider.CallbackPayload(orderId, "mock-" + orderId, "PAID", "mock-signature");
    var callbacks =
        runTogether(
            () -> payments.handleCallback("mock", orderId, callback),
            () -> payments.handleCallback("mock", orderId, callback));
    assertTrue(callbacks.stream().allMatch(outcome -> outcome.error() == null), "并发回调不得抛错");
    List<Boolean> replayed =
        callbacks.stream()
            .map(outcome -> Boolean.TRUE.equals(outcome.value().get("replayed")))
            .sorted()
            .toList();

    assertEquals(List.of(false, true), replayed, "并发回调恰一次置付、一次重放");
    assertEquals(500L, wallet.balance(userId), "支付金额只入账一次");
    assertEquals(1L, ledgerEntries("pay:" + orderId), "支付幂等键恰一分录");
  }

  private long newUser(String username) {
    return users.save(UserEntity.create(username, "hash", "USER")).getId();
  }

  private static String suffix() {
    return Long.toHexString(System.nanoTime());
  }

  private long ledgerEntries(String refId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE ref_id = ?", Long.class, refId);
    return count == null ? 0L : count;
  }

  private static IngestUsageRow row(String id, String keyId, long cost) {
    return new IngestUsageRow(
        id,
        "req-" + id,
        "t1",
        keyId,
        "deepseek",
        "deepseek-chat",
        "deepseek-v4-pro",
        false,
        "ok",
        null,
        10,
        20,
        0,
        0,
        false,
        cost,
        "CNY",
        120L,
        300L,
        "2026-10-02T00:00:00Z");
  }

  /** 两个任务在同一闸门放行,收集返回值或异常,避免测试线程吞掉失败。 */
  private static <T> List<Outcome<T>> runTogether(Callable<T> one, Callable<T> two)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<Outcome<T>> first = CompletableFuture.supplyAsync(() -> runAfter(start, one));
    CompletableFuture<Outcome<T>> second =
        CompletableFuture.supplyAsync(() -> runAfter(start, two));
    start.countDown();
    return List.of(
        first.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        second.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  private static <T> Outcome<T> runAfter(CountDownLatch start, Callable<T> task) {
    try {
      start.await();
      return new Outcome<>(task.call(), null);
    } catch (InterruptedException e) {
      throw new IllegalStateException("并发等待被中断", e);
    } catch (Exception e) {
      return new Outcome<>(null, e);
    }
  }
}
