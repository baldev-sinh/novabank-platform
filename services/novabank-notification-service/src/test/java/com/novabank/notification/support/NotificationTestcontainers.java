package com.novabank.notification.support;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL, Kafka (with the source topics, which other services own) and Mailpit, the same images
 * as compose.yaml, plus a small client for Mailpit's REST API.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NotificationTestcontainers {

  static final int SMTP_PORT = 1025;
  static final int HTTP_PORT = 8025;

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18.6-trixie");
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  KafkaAdmin.NewTopics sourceTopics() {
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name("auth.user-events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("account.events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("payment.events.v1").partitions(3).replicas(1).build());
  }

  @Bean
  GenericContainer<?> mailpit() {
    return new GenericContainer<>("axllent/mailpit:v1.34.0")
        .withExposedPorts(SMTP_PORT, HTTP_PORT)
        .waitingFor(Wait.forHttp("/readyz").forPort(HTTP_PORT));
  }

  @Bean
  DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpit) {
    return registry -> {
      registry.add("spring.mail.host", mailpit::getHost);
      registry.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP_PORT));
      registry.add("spring.mail.properties[mail.smtp.auth]", () -> "false");
      registry.add("spring.mail.properties[mail.smtp.starttls.enable]", () -> "false");
      registry.add("spring.mail.properties[mail.smtp.starttls.required]", () -> "false");
    };
  }

  @Bean
  Mailbox mailbox(GenericContainer<?> mailpit) {
    return new Mailbox(
        URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(HTTP_PORT)));
  }

  /** Reads emails caught by Mailpit. */
  public static final class Mailbox {

    private final URI baseUri;
    private final HttpClient http = HttpClient.newHttpClient();

    Mailbox(URI baseUri) {
      this.baseUri = baseUri;
    }

    /** Subjects of all emails sent to this address. */
    public List<String> subjectsTo(String address) {
      return JsonPath.read(search(address), "$.messages[*].Subject");
    }

    /** Plain-text bodies of all emails sent to this address. */
    public List<String> textsTo(String address) {
      List<String> ids = JsonPath.read(search(address), "$.messages[*].ID");
      return ids.stream()
          .map(id -> JsonPath.<String>read(get("/api/v1/message/" + id), "$.Text"))
          .toList();
    }

    private String search(String address) {
      return get(
          "/api/v1/search?query="
              + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
    }

    private String get(String path) {
      try {
        HttpResponse<String> response =
            http.send(
                HttpRequest.newBuilder(baseUri.resolve(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          throw new IllegalStateException("Mailpit " + path + " -> " + response.statusCode());
        }
        return response.body();
      } catch (IOException ex) {
        throw new IllegalStateException(ex);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(ex);
      }
    }
  }
}
