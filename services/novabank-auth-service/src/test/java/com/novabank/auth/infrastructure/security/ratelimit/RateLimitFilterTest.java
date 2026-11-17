package com.novabank.auth.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

  @SuppressWarnings("unchecked")
  private final ProxyManager<String> buckets = mock(ProxyManager.class);

  private final RateLimitFilter filter =
      new RateLimitFilter(
          buckets,
          JsonMapper.builder().build(),
          List.of(
              new RateLimitProperties.Rule(
                  "login", "/api/v1/auth/login", 10, Duration.ofMinutes(1))));

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "203.0.113.7, 203.0.113.7",
    "2001:db8:abcd:12:1:2:3:4, 2001:db8:abcd:12:0:0:0:0/64",
    "2001:db8:abcd:12:ffff:ffff:ffff:ffff, 2001:db8:abcd:12:0:0:0:0/64",
    "0:0:0:0:0:0:0:1, 0:0:0:0:0:0:0:0/64"
  })
  @DisplayName("IPv4 clients are keyed by address, IPv6 clients by their /64 network")
  void clientKey(String remoteAddr, String expected) {
    assertThat(RateLimitFilter.clientKey(remoteAddr)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Requests to other paths or with other methods are not limited")
  void onlyLimitedEndpoints() throws Exception {
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/auth/login"),
        new MockHttpServletResponse(),
        chain);
    filter.doFilter(
        new MockHttpServletRequest("POST", "/api/v1/auth/me"),
        new MockHttpServletResponse(),
        chain);

    verifyNoInteractions(buckets);
  }

  @Test
  @DisplayName("If the bucket store fails, the request is let through (fail open)")
  void failsOpen() throws Exception {
    when(buckets.getProxy(anyString(), any())).thenThrow(new IllegalStateException("db down"));
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
