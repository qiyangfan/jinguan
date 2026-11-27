package com.longzhong.jinguan.alerts;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 邮件通道:独立认证来自 spring.mail 配置;未配置邮件服务器时不可用。 */
@Component
public class EmailAlertChannel {

  private final AlertProperties properties;
  private final ObjectProvider<JavaMailSender> mailSender;

  public EmailAlertChannel(AlertProperties properties, ObjectProvider<JavaMailSender> mailSender) {
    this.properties = properties;
    this.mailSender = mailSender;
  }

  public boolean enabled() {
    return properties.emailConfigured();
  }

  void deliver(AlertEventEntity event) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      throw new IllegalStateException("邮件通道已启用但未配置 spring.mail.host");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(properties.emailTo().toArray(String[]::new));
    message.setSubject("[jinguan][" + event.getSeverity() + "] " + event.getType());
    message.setText(
        "id: "
            + event.getId()
            + "\ntype: "
            + event.getType()
            + "\nseverity: "
            + event.getSeverity()
            + "\nsource: "
            + event.getSource()
            + "\nservice: "
            + event.getServiceId()
            + "\ntenant: "
            + event.getTenantKey()
            + "\nobserved_at: "
            + event.getObservedAt()
            + "\nrunbook: "
            + event.getRunbook()
            + "\npayload: "
            + event.getPayloadJson());
    sender.send(message);
  }
}
