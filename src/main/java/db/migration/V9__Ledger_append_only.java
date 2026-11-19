package db.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 账本 append-only 的 PostgreSQL 数据库级兜底；其他数据库由仓储收窄与纪律测试兜底。 */
@SuppressWarnings("PMD.ClassNamingConventions") // Flyway 版本前缀是框架约定
public class V9__Ledger_append_only extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws SQLException {
    if (!"PostgreSQL"
        .equalsIgnoreCase(context.getConnection().getMetaData().getDatabaseProductName())) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE FUNCTION reject_ledger_mutation() RETURNS trigger
          LANGUAGE plpgsql AS $$
          BEGIN
            RAISE EXCEPTION 'ledger_entries is append-only (attempted % on id %)',
              TG_OP, COALESCE(OLD.id, -1);
          END;
          $$
          """);
      statement.execute(
          """
          CREATE TRIGGER ledger_entries_append_only
          BEFORE UPDATE OR DELETE ON ledger_entries
          FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation()
          """);
    }
  }
}
