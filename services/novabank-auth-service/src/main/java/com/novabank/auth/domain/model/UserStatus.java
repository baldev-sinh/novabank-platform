package com.novabank.auth.domain.model;

/**
 * Represents the lifecycle state of a user account within the authentication domain.
 *
 * <p>The user's status determines whether they are permitted to authenticate
 * and what administrative actions may be performed on the account.
 *
 * <p>State transitions are enforced by the {@code User} aggregate and are
 * intentionally not implemented within this enum.
 */
public enum UserStatus {

    /**
     * User has successfully registered but has not yet verified their email address.
     *
     * <p>The user cannot authenticate until email verification is completed.
     */
    PENDING_VERIFICATION,

    /**
     * User account is fully active.
     *
     * <p>The user has completed all registration requirements and is permitted
     * to authenticate and access protected resources.
     */
    ACTIVE,

    /**
     * User account has been temporarily locked.
     *
     * <p>This typically occurs after repeated failed authentication attempts or
     * through an administrative action. The account may be unlocked according
     * to business rules.
     */
    LOCKED,

    /**
     * User account has been disabled by an administrator.
     *
     * <p>A disabled account cannot authenticate until it is explicitly
     * re-enabled through an administrative action.
     */
    DISABLED
}
