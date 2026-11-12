package com.novabank.auth.presentation.rest.jwks;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the public signing key so other services can verify NovaBank access tokens. */
@Tag(name = "Keys")
@RestController
public class JwksController {

  private final Map<String, Object> publicJwks;

  public JwksController(RSAKey jwtSigningKey) {
    // toPublicJWK() strips the private part; only the public key ever leaves this service.
    this.publicJwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
  }

  @GetMapping("/.well-known/jwks.json")
  @Operation(summary = "JSON Web Key Set used to verify access tokens")
  public ResponseEntity<Map<String, Object>> jwks() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
        .body(publicJwks);
  }
}
