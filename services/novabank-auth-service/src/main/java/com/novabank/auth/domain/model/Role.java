package com.novabank.auth.domain.model;

import com.novabank.auth.domain.valueobject.identifier.RoleId;
import java.util.Objects;

public final class Role {

    private final RoleId id;

    private final RoleName name;

    private Role(RoleId id, RoleName name){
        this.id = Objects.requireNonNull(id, "RoleId cannot be null");
        this.name = Objects.requireNonNull(name, "RoleName cannot be null");
    }

    public static Role create(RoleName name){
        return new Role(RoleId.random(), name);
    }

    public static Role create(RoleId id, RoleName name){
        return new Role(id, name);
    }

    public RoleId id() {
        return id;
    }

    public RoleName name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Role role)) {
            return false;
        }

        return id.equals(role.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Role{" +
            "id=" + id +
            ", name=" + name +
            '}';
    }
}
