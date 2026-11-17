package com.novabank.platform.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Problem Details additions for servlet web services. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseBodyAdvice.class)
public class WebProblemAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ProblemTraceIdAdvice problemTraceIdAdvice() {
    return new ProblemTraceIdAdvice();
  }
}
