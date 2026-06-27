package com.novabank.auth.domain.valueobject.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void shouldGenerateRandomUserId() {
        UserId userId = UserId.random();

        assertThat(userId).isNotNull();
        assertThat(userId.value()).isNotNull();

        UserId first = UserId.random();
        UserId second = UserId.random();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldCreateUserIdFromValidUuidString() {
        UUID uuid = UUID.randomUUID();

        UserId userId = UserId.from(uuid.toString());

        assertThat(userId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldThrowExceptionWhenUuidIsNull() {
        assertThatThrownBy(() -> new UserId(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("UserId cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenUuidStringIsInvalid() {
        assertThatThrownBy(() -> UserId.from("invalid-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeEqualWhenUnderlyingUuidIsSame() {
        UUID uuid = UUID.randomUUID();

        UserId first = new UserId(uuid);
        UserId second = new UserId(uuid);

        assertThat(first)
            .isEqualTo(second)
            .hasSameHashCodeAs(second);
    }

}
