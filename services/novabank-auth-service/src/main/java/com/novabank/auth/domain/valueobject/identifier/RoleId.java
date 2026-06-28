package com.novabank.auth.domain.valueobject.identifier;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static RoleId random(){
        return new RoleId(UUID.randomUUID());
    }

    public static RoleId from(String value){
        return new RoleId(UUID.fromString(value));
    }

    @Override
    public String toString(){
        return value.toString();
    }

}
