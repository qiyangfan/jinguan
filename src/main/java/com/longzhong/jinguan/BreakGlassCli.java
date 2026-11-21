package com.longzhong.jinguan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.longzhong.jinguan.security.BreakGlassService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 运维 CLI:在无 Web 模式下连接数据库并铸造短时 ADMIN access token。
 *
 * <p>凭据只从 `JINGUAN_BREAKGLASS_TOKEN` 读取，默认关闭；token 只输出到 stdout 一次，审计落库。
 */
@SuppressWarnings("PMD.SystemPrintln") // CLI 的契约就是 stdout 输出 token、stderr 输出错误
public final class BreakGlassCli {

  private BreakGlassCli() {}

  /** 执行 CLI；返回进程退出码。 */
  public static int run() {
    ((LoggerContext) LoggerFactory.getILoggerFactory())
        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        .setLevel(Level.OFF);
    SpringApplication app = new SpringApplication(JinguanApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.setBannerMode(Banner.Mode.OFF);
    try (ConfigurableApplicationContext context =
        app.run(
            "--spring.main.web-application-type=none",
            "--spring.task.scheduling.enabled=false",
            "--logging.config=classpath:logback-breakglass.xml")) {
      BreakGlassService service = context.getBean(BreakGlassService.class);
      if (!service.enabled()) {
        System.err.println("break-glass 未启用或凭据未配置");
        return 2;
      }
      String token = service.issue(System.getenv("JINGUAN_BREAKGLASS_TOKEN"), "cli");
      System.out.println(token);
      return 0;
    } catch (RuntimeException e) {
      System.err.println(
          "break-glass 签发失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      return 1;
    }
  }
}
