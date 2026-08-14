package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.response.CurrentUserResponse;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private static final String EMAIL = "baldev@example.com";

  @Mock private CurrentUserProvider currentUserProvider;

  private GetCurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new GetCurrentUserService(currentUserProvider);
  }

  @Test
  @DisplayName("Should return current authenticated user")
  void shouldReturnCurrentAuthenticatedUser() {

    JwtUser user = new JwtUser(USER_ID, EMAIL, EnumSet.of(RoleName.CUSTOMER));

    when(currentUserProvider.getCurrentUser()).thenReturn(user);

    CurrentUserResponse response = service.getCurrentUser();

    assertThat(response).isNotNull();
    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.email()).isEqualTo(EMAIL);
    assertThat(response.roles()).containsExactly(RoleName.CUSTOMER);

    verify(currentUserProvider).getCurrentUser();

    verifyNoMoreInteractions(currentUserProvider);
  }

  @Test
  @DisplayName("Should preserve all user roles")
  void shouldPreserveAllUserRoles() {

    JwtUser user = new JwtUser(USER_ID, EMAIL, EnumSet.of(RoleName.CUSTOMER, RoleName.ADMIN));

    when(currentUserProvider.getCurrentUser()).thenReturn(user);

    CurrentUserResponse response = service.getCurrentUser();

    assertThat(response.roles()).containsExactlyInAnyOrder(RoleName.CUSTOMER, RoleName.ADMIN);

    verify(currentUserProvider).getCurrentUser();

    verifyNoMoreInteractions(currentUserProvider);
  }

  @Test
  @DisplayName("Should propagate current user provider failure")
  void shouldPropagateCurrentUserProviderFailure() {

    when(currentUserProvider.getCurrentUser())
        .thenThrow(new IllegalStateException("No authenticated user found"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getCurrentUser())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No authenticated user found");

    verify(currentUserProvider).getCurrentUser();

    verifyNoMoreInteractions(currentUserProvider);
  }
}
