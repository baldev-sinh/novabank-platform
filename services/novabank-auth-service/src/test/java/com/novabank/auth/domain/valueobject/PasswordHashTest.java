package com.novabank.auth.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    @DisplayName("Should create a password hash from a valid value")
    void shouldCreatePasswordHash(){
        PasswordHash passwordHash = PasswordHash.of("hashed-password-value");

        assertThat(passwordHash.value()).isEqualTo("hashed-password-value");
    }

    @Test
    @DisplayName("Should trim surrounding whitespace")
    void shouldTrim(){
        PasswordHash passwordHash = PasswordHash.of("     hashed-password-value    ");

        assertThat(passwordHash.value()).isEqualTo("hashed-password-value");
    }

    @Test
    @DisplayName("Should reject null password hash")
    void shouldRejectNull(){
        assertThatThrownBy(() -> PasswordHash.of(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("value cannot be null");
    }

    @Test
    @DisplayName("Should reject blank password hash")
    void shouldRejectBlank(){
        assertThatThrownBy(() -> PasswordHash.of(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Password hash cannot be blank");
    }

    @Test
    @DisplayName("Should support value equality for equivalent password hash")
    void shouldSupportValueEquality() {
        PasswordHash first = PasswordHash.of("hashed-password-value");
        PasswordHash second = PasswordHash.of("hashed-password-value");

        assertThat(first)
            .isEqualTo(second)
            .hasSameHashCodeAs(second);
    }

}
