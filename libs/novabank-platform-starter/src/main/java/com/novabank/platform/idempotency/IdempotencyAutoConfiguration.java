package com.novabank.platform.idempotency;

import com.novabank.platform.time.ClockAutoConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** {@link IdempotentRequests} for services with a database. */
@AutoConfiguration(
    after = {
      JdbcClientAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      ClockAutoConfiguration.class
    })
@ConditionalOnClass(JdbcClient.class)
public class IdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class)
  IdempotentRequests idempotentRequests(JdbcClient jdbcClient, JsonMapper jsonMapper, Clock clock) {
    return new IdempotentRequests(jdbcClient, jsonMapper, clock);
  }
}
