package com.novabank.auth.infrastructure.security.ratelimit;

import com.novabank.platform.web.ProblemTraceId;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Token bucket per (endpoint, client IP), stored in PostgreSQL so every instance shares the same
 * budget. Over the limit: 429 with {@code Retry-After} (RFC 6585, RFC 9110).
 *
 * <p>The client IP is {@link HttpServletRequest#getRemoteAddr()}: behind a proxy it is only correct
 * with {@code server.forward-headers-strategy=native}, which trusts {@code X-Forwarded-For} from
 * internal proxies only (a client can't fake its IP from outside).
 */
public class RateLimitFilter extends OncePerRequestFilter {

  static final URI PROBLEM_TYPE = URI.create("urn:novabank:problem:rate-limited");

  private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

  private final ProxyManager<String> buckets;
  private final JsonMapper jsonMapper;
  private final Map<String, LimitedPath> byPath;

  public RateLimitFilter(
      ProxyManager<String> buckets, JsonMapper jsonMapper, List<RateLimitProperties.Rule> rules) {
    this.buckets = buckets;
    this.jsonMapper = jsonMapper;
    this.byPath =
        rules.stream()
            .map(LimitedPath::of)
            .collect(Collectors.toUnmodifiableMap(LimitedPath::path, Function.identity()));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod())
        || !byPath.containsKey(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    LimitedPath limited = byPath.get(request.getRequestURI());
    ConsumptionProbe probe;
    try {
      probe =
          buckets
              .getProxy(
                  limited.name() + "|" + clientKey(request.getRemoteAddr()), limited::configuration)
              .tryConsumeAndReturnRemaining(1);
    } catch (RuntimeException ex) {
      // Fail open: the limiter's store being down must not take sign-in down with it. Lockout
      // per account still applies.
      LOGGER.warn("Rate limit check failed for {}, allowing the request", limited.name(), ex);
      chain.doFilter(request, response);
      return;
    }
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    reject(request, response, probe);
  }

  /**
   * The client's address, or its /64 network for IPv6: one subscriber usually gets a whole /64, so
   * keying single IPv6 addresses would let a client rotate through 2^64 of them.
   */
  static String clientKey(String remoteAddr) {
    if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
      return String.valueOf(remoteAddr);
    }
    try {
      // An IP literal: InetAddress parses it without any DNS lookup.
      byte[] address = InetAddress.getByName(remoteAddr).getAddress();
      if (address.length != 16) {
        return remoteAddr;
      }
      Arrays.fill(address, 8, 16, (byte) 0);
      return InetAddress.getByAddress(address).getHostAddress() + "/64";
    } catch (UnknownHostException ex) {
      return remoteAddr;
    }
  }

  private void reject(
      HttpServletRequest request, HttpServletResponse response, ConsumptionProbe probe)
      throws IOException {
    long retryAfterSeconds =
        Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() - 1) + 1);

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
    problem.setType(PROBLEM_TYPE);
    problem.setInstance(URI.create(request.getRequestURI()));
    ProblemTraceId.addTo(problem);

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }

  private record LimitedPath(String name, String path, BucketConfiguration configuration) {

    static LimitedPath of(RateLimitProperties.Rule rule) {
      BucketConfiguration configuration =
          BucketConfiguration.builder()
              .addLimit(limit ->
                  limit.capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()))
              .build();
      return new LimitedPath(rule.name(), rule.path(), configuration);
    }
  }
}
