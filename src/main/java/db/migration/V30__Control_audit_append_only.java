package db.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 控制面审计 append-only 的 PostgreSQL 数据库级兜底;其他数据库由仓储收窄与纪律测试兜底。 */
@SuppressWarnings("PMD.ClassNamingConventions") // Flyway 版本前缀是框架约定
public class V30__Control_audit_append_only extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws SQLException {
    if (!"PostgreSQL"
        .equalsIgnoreCase(context.getConnection().getMetaData().getDatabaseProductName())) {
      return;
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE FUNCTION reject_control_audit_update() RETURNS trigger
          LANGUAGE plpgsql AS $$
          BEGIN
            RAISE EXCEPTION 'control_audit_log is append-only (attempted % on id %)',
              TG_OP, COALESCE(OLD.id, 'unknown');
          END;
          $$
          """);
      statement.execute(
          """
          CREATE TRIGGER control_audit_log_no_update
          BEFORE UPDATE ON control_audit_log
          FOR EACH ROW EXECUTE FUNCTION reject_control_audit_update()
          """);
    }
  }
}
