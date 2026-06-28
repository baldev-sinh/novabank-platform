package com.novabank.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class UserStatusTest {


    @Test
    @DisplayName("Should contain all the expected user status values")
    void shouldContainAllExpectedUserStatusValues(){
        assertThat(UserStatus.values())
            .containsExactly(
                UserStatus.PENDING_VERIFICATION,
                UserStatus.ACTIVE,
                UserStatus.LOCKED,
                UserStatus.DISABLED
            );
    }

    @Test
    @DisplayName("Should return enum by name")
    void shouldReturnEnumByName(){
        assertThat(UserStatus.valueOf("ACTIVE"))
            .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should expose correct enum names")
    void shouldExposeCorrectEnumNames() {
        assertThat(UserStatus.ACTIVE.name())
            .isEqualTo("ACTIVE");

        assertThat(UserStatus.LOCKED.name())
            .isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("Should throw exception for unknown status")
    void shouldThrowExceptionForUnknownStatus() {
        assertThatThrownBy(() -> UserStatus.valueOf("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
