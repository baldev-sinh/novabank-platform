package com.novabank.auth.presentation.rest.admin;

import com.novabank.auth.application.response.StaffUserResponse;
import com.novabank.auth.application.usecase.StaffAdministrationUseCase;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.presentation.rest.admin.request.FindUserRequest;
import com.novabank.auth.presentation.rest.admin.response.StaffUserApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (SecurityConfiguration). Roles apply from the user's next sign-in. */
@Tag(name = "Staff administration")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class StaffAdministrationController {

  private final StaffAdministrationUseCase staffAdministration;

  @PostMapping(
      value = "/lookup",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Find a user by email, to get their id")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "User found"),
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator"),
    @ApiResponse(responseCode = "404", description = "No user with that email")
  })
  public StaffUserApiResponse findUser(@Valid @RequestBody FindUserRequest request) {
    StaffUserResponse user = staffAdministration.findByEmail(request.email());
    return new StaffUserApiResponse(
        user.userId(), user.email(), user.status(), user.roles().stream().sorted().toList());
  }

  @PutMapping("/{userId}/roles/{role}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Grant a staff role (idempotent)")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "The user has the role"),
    @ApiResponse(responseCode = "400", description = "Unknown role, or CUSTOMER"),
    @ApiResponse(responseCode = "404", description = "No such user"),
    @ApiResponse(responseCode = "409", description = "The user isn't active")
  })
  public void grantRole(@PathVariable UUID userId, @PathVariable RoleName role) {
    staffAdministration.grantRole(userId, role);
  }

  @DeleteMapping("/{userId}/roles/{role}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke a staff role (idempotent)")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "The user doesn't have the role"),
    @ApiResponse(responseCode = "400", description = "Unknown role, or CUSTOMER"),
    @ApiResponse(responseCode = "404", description = "No such user"),
    @ApiResponse(responseCode = "409", description = "Administrators can't revoke their own ADMIN")
  })
  public void revokeRole(@PathVariable UUID userId, @PathVariable RoleName role) {
    staffAdministration.revokeRole(userId, role);
  }
}
