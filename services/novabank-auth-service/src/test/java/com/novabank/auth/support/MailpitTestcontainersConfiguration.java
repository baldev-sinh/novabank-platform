package com.novabank.auth.support;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A real SMTP server (Mailpit, same image as compose.yaml) that keeps every email, plus a small
 * client for its REST API so tests can read what was sent.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MailpitTestcontainersConfiguration {

  static final int SMTP_PORT = 1025;
  static final int HTTP_PORT = 8025;

  @Bean
  GenericContainer<?> mailpit() {
    return new GenericContainer<>("axllent/mailpit:v1.33.1")
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

    /** Plain-text bodies of all emails sent to this address, newest first. */
    public List<String> textsTo(String address) {
      String query = URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8);
      List<String> ids = JsonPath.read(get("/api/v1/search?query=" + query), "$.messages[*].ID");
      return ids.stream()
          .map(id -> JsonPath.<String>read(get("/api/v1/message/" + id), "$.Text"))
          .toList();
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
      } catch (java.io.IOException ex) {
        throw new IllegalStateException(ex);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(ex);
      }
    }
  }
}
