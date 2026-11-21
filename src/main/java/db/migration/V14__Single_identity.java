package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 单一身份数据模型:人类主键改为虎符 sub(principals.subject),删除本地 users 与密码。
 *
 * <p>零存量重建:任何账本/订单/API key/refresh/用量/吊销行存在时拒绝迁移,必须先显式绑定 subject, 避免静默丢失归属。外键名因数据库自动生成,统一经 JDBC
 * 元数据发现后删除,保持 H2/PostgreSQL 一致。
 */
@SuppressWarnings({
  "PMD.ClassNamingConventions",
  "PMD.CloseResource",
  "PMD.AvoidInstantiatingObjectsInLoops"
}) // Flyway 版本前缀是框架约定;Connection 由 Flyway 管理;COUNT 必返回一行
public class V14__Single_identity extends BaseJavaMigration {

  private static final String FK_SUBJECT_TEMPLATE =
      " FOREIGN KEY (subject) REFERENCES principals (subject)";
  private static final String USERS_TABLE = "users";
  private static final List<String> LEGACY_TABLES =
      List.of(
          "ledger_entries",
          "mock_payment_orders",
          "api_key_bindings",
          "refresh_sessions",
          "usage_records",
          "key_revocations");

  @Override
  public void migrate(Context context) throws SQLException {
    Connection connection = context.getConnection();
    assertZeroStock(connection);
    dropForeignKeysToUsers(connection);
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE principals (
              subject      VARCHAR(64) PRIMARY KEY,
              username     VARCHAR(64) NOT NULL UNIQUE,
              role         VARCHAR(16) NOT NULL DEFAULT 'USER',
              disabled     BOOLEAN NOT NULL DEFAULT FALSE,
              suspended_at TIMESTAMP NULL,
              created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
          """);
      statement.execute("DROP INDEX IF EXISTS idx_ledger_user");
      statement.execute("DROP INDEX IF EXISTS idx_usage_records_user");
      statement.execute("DROP INDEX IF EXISTS idx_usage_records_candidate");
      statement.execute("DROP INDEX IF EXISTS idx_usage_records_user_settle");
      statement.execute("DROP INDEX IF EXISTS idx_refresh_sessions_user");

      statement.execute("ALTER TABLE ledger_entries ADD COLUMN subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE ledger_entries ADD COLUMN operator_subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE ledger_entries DROP COLUMN user_id");
      statement.execute("ALTER TABLE ledger_entries DROP COLUMN operator_user_id");
      statement.execute("ALTER TABLE ledger_entries ALTER COLUMN subject SET NOT NULL");
      statement.execute(
          "ALTER TABLE ledger_entries ADD CONSTRAINT fk_ledger_subject" + FK_SUBJECT_TEMPLATE);
      statement.execute("CREATE INDEX idx_ledger_subject ON ledger_entries (subject, id)");

      statement.execute("ALTER TABLE mock_payment_orders ADD COLUMN subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE mock_payment_orders DROP COLUMN user_id");
      statement.execute("ALTER TABLE mock_payment_orders ALTER COLUMN subject SET NOT NULL");
      statement.execute(
          "ALTER TABLE mock_payment_orders ADD CONSTRAINT fk_order_subject" + FK_SUBJECT_TEMPLATE);

      statement.execute("ALTER TABLE api_key_bindings ADD COLUMN subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE api_key_bindings DROP COLUMN user_id");
      statement.execute("ALTER TABLE api_key_bindings ALTER COLUMN subject SET NOT NULL");
      statement.execute(
          "ALTER TABLE api_key_bindings ADD CONSTRAINT fk_key_binding_subject"
              + FK_SUBJECT_TEMPLATE);

      statement.execute("ALTER TABLE refresh_sessions ADD COLUMN subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE refresh_sessions DROP COLUMN user_id");
      statement.execute("ALTER TABLE refresh_sessions ALTER COLUMN subject SET NOT NULL");
      statement.execute(
          "ALTER TABLE refresh_sessions ADD CONSTRAINT fk_refresh_subject" + FK_SUBJECT_TEMPLATE);
      statement.execute("CREATE INDEX idx_refresh_sessions_subject ON refresh_sessions (subject)");

      statement.execute("ALTER TABLE usage_records ADD COLUMN subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE usage_records DROP COLUMN user_id");
      statement.execute(
          "ALTER TABLE usage_records ADD CONSTRAINT fk_usage_subject" + FK_SUBJECT_TEMPLATE);
      statement.execute("CREATE INDEX idx_usage_records_subject ON usage_records (subject, id)");
      statement.execute(
          "CREATE INDEX idx_usage_records_candidate"
              + " ON usage_records (final_outcome, subject)");
      statement.execute(
          "CREATE INDEX idx_usage_records_subject_settle"
              + " ON usage_records (subject, last_settlement_attempt_at)");

      statement.execute("ALTER TABLE key_revocations ADD COLUMN operator_subject VARCHAR(64) NULL");
      statement.execute("ALTER TABLE key_revocations DROP COLUMN operator_user_id");

      statement.execute("DROP TABLE identity_bootstrap");
      statement.execute("DROP TABLE users");
    }
  }

  private void assertZeroStock(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      for (String table : LEGACY_TABLES) {
        try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
          if (!rows.next()) {
            throw new FlywayException("无法读取 " + table + " 行数");
          }
          if (rows.getLong(1) > 0) {
            throw new FlywayException("检测到 " + table + " 存量行;单一身份迁移要求零存量,请先显式绑定 subject");
          }
        }
      }
    }
  }

  private void dropForeignKeysToUsers(Connection connection) throws SQLException {
    DatabaseMetaData meta = connection.getMetaData();
    boolean upper = meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
    for (String table : LEGACY_TABLES) {
      List<String> constraints = new ArrayList<>();
      String metadataTable = upper ? table.toUpperCase(Locale.ROOT) : table;
      try (ResultSet keys = meta.getImportedKeys(null, null, metadataTable)) {
        while (keys.next()) {
          if (USERS_TABLE.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
            constraints.add(keys.getString("FK_NAME"));
          }
        }
      }
      try (Statement statement = connection.createStatement()) {
        for (String constraint : constraints) {
          statement.execute(
              "ALTER TABLE " + table + " DROP CONSTRAINT " + quote(connection, constraint));
        }
      }
    }
  }

  private String quote(Connection connection, String identifier) throws SQLException {
    String quote = connection.getMetaData().getIdentifierQuoteString();
    if (quote == null || quote.isBlank()) {
      return identifier;
    }
    return quote + identifier + quote;
  }
}
