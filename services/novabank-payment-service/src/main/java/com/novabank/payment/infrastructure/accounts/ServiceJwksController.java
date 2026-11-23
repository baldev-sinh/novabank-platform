package com.novabank.payment.infrastructure.accounts;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public half of this service's identity key. Auth fetches it to check the client assertions
 * this service signs (private_key_jwt). Public by design: a public key is safe to publish.
 */
@RestController
class ServiceJwksController {

  private final Map<String, Object> jwks;

  ServiceJwksController(RSAKey serviceSigningKey) {
    this.jwks = new JWKSet(serviceSigningKey.toPublicJWK()).toJSONObject();
  }

  @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  Map<String, Object> jwks() {
    return jwks;
  }
}
