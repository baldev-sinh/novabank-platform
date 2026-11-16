package com.novabank.account.infrastructure.configuration;

import com.novabank.account.application.settings.AccountSettings;
import jakarta.validation.constraints.NotEmpty;
import java.util.Currency;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @param supportedCurrencies ISO 4217 codes the bank offers accounts in, e.g. INR,EUR
 */
@Validated
@ConfigurationProperties(prefix = "accounts")
public record AccountProperties(@NotEmpty Set<Currency> supportedCurrencies) {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AccountProperties.class)
  static class SettingsConfiguration {

    @Bean
    AccountSettings accountSettings(AccountProperties properties) {
      return new AccountSettings(properties.supportedCurrencies());
    }
  }
}
