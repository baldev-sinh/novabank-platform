package com.novabank.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.domain.valueobject.identifier.RoleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    @DisplayName("Should create a role with a generated identifier")
    void shouldCreateRole() {
        Role role = Role.create(RoleName.ADMIN);

        assertThat(role).isNotNull();
        assertThat(role.id()).isNotNull();
        assertThat(role.name()).isEqualTo(RoleName.ADMIN);
    }


    @Test
    @DisplayName("Should reject null RoleId")
    void shouldRejectNullRoleId() {
        assertThatThrownBy(() -> Role.create(null, RoleName.ADMIN))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("RoleId cannot be null");
    }

    @Test
    @DisplayName("Should reject null RoleName")
    void shouldRejectNullRoleName() {
        assertThatThrownBy(() -> Role.create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("RoleName cannot be null");
    }

    @Test
    @DisplayName("Should return role identifier")
    void shouldReturnRoleId() {
        Role role = Role.create(RoleName.ADMIN);

        assertThat(role.id()).isNotNull();
    }

    @Test
    @DisplayName("Should return role name")
    void shouldReturnRoleName() {
        Role role = Role.create(RoleName.SUPPORT_AGENT);

        assertThat(role.name()).isEqualTo(RoleName.SUPPORT_AGENT);
    }

    @Test
    @DisplayName("Should be equal when role identifiers are the same")
    void shouldBeEqualWhenRoleIdsAreSame() {
        RoleId roleId = RoleId.random();

        Role first = Role.create(roleId, RoleName.ADMIN);
        Role second = Role.create(roleId, RoleName.SUPPORT_AGENT);

        assertThat(first)
            .isEqualTo(second)
            .hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("Should not be equal when role identifiers are different")
    void shouldNotBeEqualWhenRoleIdsAreDifferent() {
        Role first = Role.create(RoleName.ADMIN);
        Role second = Role.create(RoleName.ADMIN);

        assertThat(first).isNotEqualTo(second);
    }

}
