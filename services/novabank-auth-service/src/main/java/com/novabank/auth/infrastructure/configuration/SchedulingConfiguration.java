package com.novabank.auth.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the service's own {@code @Scheduled} jobs (verification token purge, rate limit bucket
 * cleanup). Declared here so they don't depend on another feature, such as the outbox, turning
 * scheduling on.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {}
