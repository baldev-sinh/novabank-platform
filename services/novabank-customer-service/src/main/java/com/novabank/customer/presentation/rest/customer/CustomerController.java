package com.novabank.customer.presentation.rest.customer;

import com.novabank.customer.application.command.RegisterCustomerCommand;
import com.novabank.customer.application.response.CustomerDetailsResponse;
import com.novabank.customer.application.response.RegisterCustomerResponse;
import com.novabank.customer.application.usecase.GetCustomerByUserIdUseCase;
import com.novabank.customer.application.usecase.RegisterCustomerUseCase;
import com.novabank.customer.presentation.rest.customer.request.RegisterCustomerRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CustomerController {

  private final RegisterCustomerUseCase registerCustomer;
  private final GetCustomerByUserIdUseCase getCustomerByUserId;

  /**
   * Creates the caller's customer profile. A second call for the same user returns 409, so a
   * retried request can never create two profiles.
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegisterCustomerResponse> register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterCustomerRequest request) {
    RegisterCustomerResponse response =
        registerCustomer.register(
            new RegisterCustomerCommand(
                userId(jwt),
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                request.phone()));
    return ResponseEntity.created(URI.create("/api/v1/customers/me")).body(response);
  }

  /** The caller's own customer profile; 404 if they haven't created one yet. */
  @GetMapping("/me")
  public CustomerDetailsResponse me(@AuthenticationPrincipal Jwt jwt) {
    return getCustomerByUserId.getByUserId(userId(jwt));
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
