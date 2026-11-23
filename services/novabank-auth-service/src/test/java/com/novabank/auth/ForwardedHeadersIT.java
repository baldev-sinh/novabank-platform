package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.util.Arrays;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * Rate limits key on the client IP, so behind the ingress Tomcat must take it from X-Forwarded-For
 * sent by an internal proxy, and ignore it from anyone else (RemoteIpValve). Boot 4.2 renamed the
 * setting (server.forward-headers-strategy: native became server.request.header.forwarded: strategy
 * web-server, naming x-forwarded); this checks the real server still installs the valve.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      "spring.mail.host=localhost"
    })
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class ForwardedHeadersIT {

  @Autowired private ServletWebServerApplicationContext context;

  @Test
  @DisplayName("Tomcat reads the client IP from X-Forwarded-For, from internal proxies only")
  void remoteIpValveIsInstalled() {
    TomcatWebServer server = (TomcatWebServer) context.getWebServer();
    Valve[] valves = server.getTomcat().getEngine().getPipeline().getValves();

    RemoteIpValve valve =
        Arrays.stream(valves)
            .filter(RemoteIpValve.class::isInstance)
            .map(RemoteIpValve.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No RemoteIpValve: forwarded headers ignored"));
    assertThat(valve.getRemoteIpHeader()).isEqualToIgnoringCase("X-Forwarded-For");
    // Only private and loopback addresses count as proxies, so a client can't fake its IP.
    assertThat(valve.getInternalProxies()).contains("10").contains("192");
  }
}
