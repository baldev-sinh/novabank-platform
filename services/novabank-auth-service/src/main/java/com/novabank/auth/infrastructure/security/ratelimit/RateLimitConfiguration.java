package com.novabank.auth.infrastructure.security.ratelimit;

import static io.github.bucket4j.distributed.ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax;

import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import io.github.bucket4j.postgresql.PostgreSQLSelectForUpdateBasedProxyManager;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bucket4j with PostgreSQL ({@code SELECT ... FOR UPDATE} per check), in the auth database
 * (https://bucket4j.com/8.22.0/toc.html#bucket4j-postgresql).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "security.rate-limit.enabled", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
@EnableScheduling
public class RateLimitConfiguration {

  /** Just before Spring Security's filter chain (order -100), after the tracing filter. */
  static final int FILTER_ORDER = -101;

  private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitConfiguration.class);

  @Bean
  PostgreSQLSelectForUpdateBasedProxyManager<String> rateLimitBuckets(DataSource dataSource) {
    return Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
        .primaryKeyMapper(PrimaryKeyMapper.STRING)
        .table("rate_limit_buckets")
        // A row may be deleted once its bucket would be full again (it then behaves as new).
        .expirationAfterWrite(basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
        .build();
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      PostgreSQLSelectForUpdateBasedProxyManager<String> rateLimitBuckets,
      JsonMapper jsonMapper,
      RateLimitProperties properties) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(
            new RateLimitFilter(rateLimitBuckets, jsonMapper, properties.rules()));
    registration.setOrder(FILTER_ORDER);
    return registration;
  }

  @Bean
  ExpiredBucketCleaner expiredBucketCleaner(
      PostgreSQLSelectForUpdateBasedProxyManager<String> rateLimitBuckets) {
    return new ExpiredBucketCleaner(rateLimitBuckets);
  }

  /** Relational databases have no TTL: expired buckets are deleted in small batches. */
  static class ExpiredBucketCleaner {

    private static final int BATCH_SIZE = 1000;

    private final PostgreSQLSelectForUpdateBasedProxyManager<String> buckets;

    ExpiredBucketCleaner(PostgreSQLSelectForUpdateBasedProxyManager<String> buckets) {
      this.buckets = buckets;
    }

    @Scheduled(fixedDelayString = "${security.rate-limit.cleanup-interval:10m}")
    void removeExpired() {
      int removed;
      int total = 0;
      do {
        removed = buckets.removeExpired(BATCH_SIZE);
        total += removed;
      } while (removed == BATCH_SIZE);
      if (total > 0) {
        LOGGER.debug("Removed {} expired rate limit buckets", total);
      }
    }
  }
}
