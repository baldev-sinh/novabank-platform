package com.novabank.auth.infrastructure.notification;

import com.novabank.auth.application.verification.EmailVerificationSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Emails are sent on Boot's auto-configured task executor (spring.task.execution.*). */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class NotificationConfiguration {

  @Bean
  EmailVerificationSettings emailVerificationSettings(EmailVerificationProperties properties) {
    return new EmailVerificationSettings(properties.tokenTtl(), properties.resendCooldown());
  }
}
