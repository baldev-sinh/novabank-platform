package com.novabank.auth.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EmailAddressTest {


    @Test
    @DisplayName("Should create an EmailAddress from a valid email")
    void shouldCreateEmailAddress(){
        EmailAddress email = EmailAddress.of("baldev@example.com");

        assertThat(email.value()).isEqualTo("baldev@example.com");
    }

    @Test
    @DisplayName("Should trim leading and trailing white spaces")
    void shouldTrimWhitespace(){
        EmailAddress email = EmailAddress.of("      baldev@example.com    ");

        assertThat(email.value()).isEqualTo("baldev@example.com");
    }

    @Test
    @DisplayName("Should convert email to lowercase")
    void shouldConvertEmailToLowercase(){
        EmailAddress email = EmailAddress.of("BALDEV@EXAMPLE.COM");

        assertThat(email.value()).isEqualTo("baldev@example.com");
    }

    @Test
    @DisplayName("Should trim leading/trailing whitespaces and convert email to lowercase")
    void shouldTrimWhitespaceAndConvertEmailToLowercase(){
        EmailAddress email = EmailAddress.of("  BALDEV@EXAMPLE.COM  ");

        assertThat(email.value()).isEqualTo("baldev@example.com");
    }

    @Test
    @DisplayName("Should reject null email address")
    void shouldRejectNullEmail(){
        assertThatThrownBy(() -> EmailAddress.of(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("value cannot be null");
    }

    @Test
    @DisplayName("Should reject blank email address")
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> EmailAddress.of("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email address cannot be blank");
    }

    @Test
    @DisplayName("Should reject an invalid email address")
    void shouldRejectInvalidEmail() {
        assertThatThrownBy(() -> EmailAddress.of("invalid-email"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email address format is invalid");
    }

    @Test
    @DisplayName("Should support value equality for equivalent email addresses")
    void shouldSupportValueEquality() {
        EmailAddress first = EmailAddress.of(" BALDEV@EXAMPLE.COM ");
        EmailAddress second = EmailAddress.of("baldev@example.com");

        assertThat(first)
            .isEqualTo(second)
            .hasSameHashCodeAs(second);
    }

}
