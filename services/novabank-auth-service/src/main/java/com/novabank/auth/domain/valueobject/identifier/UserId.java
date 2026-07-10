package com.novabank.auth.domain.valueobject.identifier;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId{
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static UserId random(){
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(String value){
        return new UserId(UUID.fromString(value));
    }

    public static UserId of(UUID uuid){
        return new UserId(uuid);
    }

    @Override
    public String toString(){
        return value.toString();
    }

}
