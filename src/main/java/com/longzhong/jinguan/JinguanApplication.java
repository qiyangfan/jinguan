package com.longzhong.jinguan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 支付订单过期收敛扫描(PaymentExpiryScheduler)
public class JinguanApplication {

  public static void main(String[] args) {
    SpringApplication.run(JinguanApplication.class, args);
  }
}
