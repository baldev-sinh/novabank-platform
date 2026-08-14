package com.novabank.customer.domain.valueobject.identifier;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(
            value,
            "CustomerId cannot be null"
        );
    }

    public static CustomerId random() {
        return new CustomerId(UUID.randomUUID());
    }
}
