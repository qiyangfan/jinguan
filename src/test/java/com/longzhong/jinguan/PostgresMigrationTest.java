package com.longzhong.jinguan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.longzhong.jinguan.user.UserEntity;
import com.longzhong.jinguan.user.UserRepository;
import com.longzhong.jinguan.wallet.LedgerEntryRepository;
import com.longzhong.jinguan.wallet.WalletService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 在真 PostgreSQL 上验证 Flyway 全量迁移与 JPA 映射；无 Docker 时自动跳过。 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

  private static final String SQL_MIGRATION_GLOB = "classpath*:db/migration/**/V*__*.sql";
  private static final String JAVA_MIGRATION_GLOB = "classpath*:db/migration/V*.class";
  private static final String TOPUP_REASON = WalletService.REASON_MANUAL_TOPUP;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport::jdbcUrl);
    registry.add("spring.datasource.username", PostgresContainerSupport::username);
    registry.add("spring.datasource.password", PostgresContainerSupport::password);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired WalletService wallet;
  @Autowired UserRepository users;
  @Autowired LedgerEntryRepository ledger;

  @Test
  void flywayAppliesEveryMigrationOnRealPostgres() throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    int expected =
        resolver.getResources(SQL_MIGRATION_GLOB).length
            + resolver.getResources(JAVA_MIGRATION_GLOB).length;
    Integer applied =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
    assertEquals(expected, applied, "迁移文件数与成功应用数须一致");

    List<String> versions =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL "
                + "ORDER BY installed_rank",
            String.class);
    assertEquals(expected, versions.size(), "每个版本都应出现在迁移历史中");
    assertEquals("1", versions.getFirst(), "迁移历史须从 V1 开始");
  }

  @Test
  void ledgerAppendWorksOnRealPostgres() {
    long userId = users.save(UserEntity.create("pg-migration-user", "hash", "USER")).getId();
    long amount = 12_345L;

    wallet.credit(userId, amount, TOPUP_REASON, "pg-migration-seed", "pg", null);

    assertEquals(amount, wallet.balance(userId), "余额须等于唯一一笔入账");
    assertTrue(ledger.findByRefId("pg-migration-seed").isPresent(), "分录须可回读");
  }

  @Test
  void ledgerRejectsUpdateAndDeleteAtDatabaseLevel() {
    long userId = users.save(UserEntity.create("pg-append-user", "hash", "USER")).getId();
    wallet.credit(userId, 10L, TOPUP_REASON, "pg-append-only", null, null);
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM ledger_entries WHERE ref_id = ?", Long.class, "pg-append-only");

    assertThrows(
        DataAccessException.class,
        () -> jdbc.update("UPDATE ledger_entries SET amount_credits = 0 WHERE id = ?", id),
        "数据库必须拒绝账本 UPDATE");
    assertThrows(
        DataAccessException.class,
        () -> jdbc.update("DELETE FROM ledger_entries WHERE id = ?", id),
        "数据库必须拒绝账本 DELETE");
  }
}
