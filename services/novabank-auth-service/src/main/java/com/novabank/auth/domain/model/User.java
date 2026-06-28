package com.novabank.auth.domain.model;

import com.novabank.auth.domain.exception.DomainException;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class User {

    private final UserId id;
    private final EmailAddress email;
    private PasswordHash passwordHash;
    private UserStatus status;
    private final Set<RoleName> roles;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(
        UserId id,
        EmailAddress email,
        PasswordHash passwordHash,
        UserStatus status,
        Set<RoleName> roles,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "UserId cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "PasswordHash cannot be null");
        this.status = Objects.requireNonNull(status, "UserStatus cannot be null");
        Objects.requireNonNull(roles);

        if (roles.isEmpty()) {
            throw new DomainException(
                "User must have at least one role."
            );
        }

        this.roles = EnumSet.copyOf(roles);
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    public static User register(EmailAddress email, PasswordHash passwordHash){
        Instant now = Instant.now();
        return new User(
            UserId.random(),
            email,
            passwordHash,
            UserStatus.PENDING_VERIFICATION,
            EnumSet.of(RoleName.CUSTOMER),
            now,
            now
        );
    }

    public void activate(){
        if (status != UserStatus.PENDING_VERIFICATION) {
            throw new DomainException(
                "Only users pending verification can be activated."
            );
        }

        status = UserStatus.ACTIVE;
        touch();
    }

    public void lock(){
        if (this.status == UserStatus.LOCKED) {
            throw new DomainException("User account is already locked.");
        }

        if (this.status == UserStatus.DISABLED) {
            throw new DomainException("Disabled user accounts cannot be locked.");
        }

        this.status = UserStatus.LOCKED;
        touch();

    }

    public void unlock(){
        /**
         * TODO - Unlock target state may evolve buisness requirement.
         */
        if(!(this.status == UserStatus.LOCKED)){
            throw new DomainException("Only locked users can be unlocked.");
        }
        this.status = UserStatus.ACTIVE;
        touch();
    }

    public void disable(){
        if(this.status == UserStatus.DISABLED){
            throw new DomainException("User account is already disabled.");
        }
        this.status = UserStatus.DISABLED;
        touch();
    }

    public void changePassword(PasswordHash passwordHash){
        Objects.requireNonNull(passwordHash, "PasswordHash cannot be null");

        if(this.passwordHash.equals(passwordHash)){
            throw new DomainException("New password must be different from the current password.");
        }

        this.passwordHash = passwordHash;
        touch();
    }

    public void assignRole(RoleName roleName){
        Objects.requireNonNull(roleName, "Role cannot be null");

        if (!roles.add(roleName)) {
            throw new DomainException(
                "User already has role " + roleName + '.'
            );
        }

        touch();
    }

    public void removeRole(RoleName roleName){
        Objects.requireNonNull(roleName, "RoleName cannot be null");

        if(!this.roles.contains(roleName)){
            throw new DomainException(
                "User does not have role " + roleName + '.'
            );
        }

        if(this.roles.size() == 1) {
            throw new DomainException(
                "User must have at least one assigned role."
            );
        }

        this.roles.remove(roleName);
        touch();
    }

    public UserId id(){
        return this.id;
    }

    public EmailAddress email(){
        return this.email;
    }

    public PasswordHash passwordHash(){
        return this.passwordHash;
    }

    public UserStatus status(){
        return this.status;
    }

    public Set<RoleName> roles(){
        return Set.copyOf(this.roles);
    }

    public Instant createdAt(){
        return this.createdAt;
    }

    public Instant updatedAt(){
        return this.updatedAt;
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof User other)) {
            return false;
        }

        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User{" +
            "id=" + id +
            ", email=" + email +
            ", status=" + status +
            ", roles=" + roles +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }
}
