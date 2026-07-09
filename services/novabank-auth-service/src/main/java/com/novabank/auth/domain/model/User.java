package com.novabank.auth.domain.model;

import com.novabank.auth.domain.exception.DomainException;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate Root representing a user within the NovaBank authentication domain.
 *
 * <p>The {@code User} aggregate is responsible for protecting the consistency
 * of user identity and lifecycle. It owns business rules related to:
 *
 * <ul>
 *     <li>User registration</li>
 *     <li>Lifecycle transitions</li>
 *     <li>Password management</li>
 *     <li>Role assignment</li>
 * </ul>
 *
 * <p>Instances are created through the static factory methods:
 * <ul>
 *     <li>{@link #register(EmailAddress, PasswordHash)} for new users.</li>
 *     <li>{@link #restore(UserId, EmailAddress, PasswordHash, UserStatus, Set, Instant, Instant)}
 *     for reconstructing persisted users.</li>
 * </ul>
 *
 * <p>The constructor remains private to ensure that all instances are created
 * through controlled factory methods.
 */
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
        Objects.requireNonNull(roles, "Roles cannot be null");

        if (roles.isEmpty()) {
            throw new DomainException(
                "User must have at least one role."
            );
        }

        this.roles = EnumSet.copyOf(roles);
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    /**
     * Registers a new user.
     *
     * <p>This factory creates a brand-new {@code User} aggregate by applying
     * the business rules for registration.
     *
     * <p>The newly registered user:
     * <ul>
     *     <li>Receives a new {@link UserId}.</li>
     *     <li>Starts in the {@link UserStatus#PENDING_VERIFICATION} state.</li>
     *     <li>Is assigned the {@link RoleName#CUSTOMER} role.</li>
     *     <li>Initializes creation and update timestamps.</li>
     * </ul>
     *
     * @param email user's email address
     * @param passwordHash hashed password
     * @return newly registered user
     */
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

    /**
     * Reconstructs an existing {@code User} aggregate from persisted state.
     *
     * <p>This factory is intended for use by the persistence layer when restoring
     * an aggregate from storage. Unlike {@link #register(EmailAddress, PasswordHash)},
     * This factory reconstructs an existing aggregate from persisted state without reapplying business rules.
     *
     * @param id persisted user identifier
     * @param email persisted email address
     * @param passwordHash persisted password hash
     * @param status persisted lifecycle status
     * @param roles persisted roles
     * @param createdAt persisted creation timestamp
     * @param updatedAt persisted last modification timestamp
     * @return reconstructed {@code User} aggregate
     */
    public static User restore(
        UserId id,
        EmailAddress email,
        PasswordHash passwordHash,
        UserStatus status,
        Set<RoleName> roles,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new User(
            id,
            email,
            passwordHash,
            status,
            roles,
            createdAt,
            updatedAt
        );
    }

    /**
     * Activates a user after successful verification.
     *
     * <p>Only users in the {@link UserStatus#PENDING_VERIFICATION}
     * state may be activated.
     *
     * @throws DomainException if the current state does not allow activation
     */
    public void activate(){
        if (status != UserStatus.PENDING_VERIFICATION) {
            throw new DomainException(
                "Only users pending verification can be activated."
            );
        }

        status = UserStatus.ACTIVE;
        touch();
    }

    /**
     * Locks the user account.
     *
     * <p>A locked user cannot authenticate until unlocked.
     *
     * @throws DomainException if the account is already locked or disabled
     */
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

    /**
     * Unlocks a previously locked user account.
     *
     * <p>The user returns to the {@link UserStatus#ACTIVE} state.
     *
     * @throws DomainException if the user is not currently locked
     */
    public void unlock(){
        /*
         * Business rule:
         * Currently a locked account is restored to ACTIVE.
         * This may evolve if additional lifecycle states are introduced.
         */
        if(!(this.status == UserStatus.LOCKED)){
            throw new DomainException("Only locked users can be unlocked.");
        }
        this.status = UserStatus.ACTIVE;
        touch();
    }

    /**
     * Permanently disables the user account.
     *
     * <p>Disabled accounts cannot authenticate or be locked.
     *
     * @throws DomainException if the account is already disabled
     */
    public void disable(){
        if(this.status == UserStatus.DISABLED){
            throw new DomainException("User account is already disabled.");
        }
        this.status = UserStatus.DISABLED;
        touch();
    }

    /**
     * Replaces the user's password hash.
     *
     * <p>The new password hash must differ from the current one.
     *
     * @param passwordHash new password hash
     * @throws DomainException if the supplied password is identical to the current password
     */
    public void changePassword(PasswordHash passwordHash){
        Objects.requireNonNull(passwordHash, "PasswordHash cannot be null");

        if(this.passwordHash.equals(passwordHash)){
            throw new DomainException("New password must be different from the current password.");
        }

        this.passwordHash = passwordHash;
        touch();
    }

    /**
     * Assigns a role to the user.
     *
     * @param roleName role to assign
     * @throws DomainException if the user already has the role
     */
    public void assignRole(RoleName roleName){
        Objects.requireNonNull(roleName, "Role cannot be null");

        if (!roles.add(roleName)) {
            throw new DomainException(
                "User already has role " + roleName + '.'
            );
        }

        touch();
    }

    /**
     * Removes a role from the user.
     *
     * <p>A user must always have at least one assigned role.
     *
     * @param roleName role to remove
     * @throws DomainException if the role is not assigned
     * @throws DomainException if removing the last remaining role
     */
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

    /**
     * Updates the last modification timestamp.
     */
    private void touch() {
        updatedAt = Instant.now();
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
