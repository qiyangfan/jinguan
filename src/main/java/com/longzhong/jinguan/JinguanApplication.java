package com.longzhong.jinguan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 锦官控制面启动类。 */
@SpringBootApplication
@EnableScheduling
public class JinguanApplication {

  public static void main(String[] args) {
    if (args.length > 0 && "break-glass".equals(args[0])) {
      System.exit(BreakGlassCli.run());
    }
    SpringApplication.run(JinguanApplication.class, args);
  }
}
