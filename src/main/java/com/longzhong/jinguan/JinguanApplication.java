package com.longzhong.jinguan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 锦官控制面启动类。 */
@SpringBootApplication
@EnableScheduling
public class JinguanApplication {

  public static void main(String[] args) {
    SpringApplication.run(JinguanApplication.class, args);
  }
}
