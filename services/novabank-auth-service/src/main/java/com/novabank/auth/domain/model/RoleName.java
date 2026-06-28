package com.novabank.auth.domain.model;

/**
 * Represents the business roles that may be assigned to users within the
 * NovaBank authentication domain.
 *
 * <p>A role determines the level of access granted to a user and forms the
 * foundation for authorization across the platform.
 *
 * <p>The permissions associated with each role are defined elsewhere in the
 * authorization model and are intentionally not represented by this enum.
 */
public enum RoleName {

    /**
     * System administrator with unrestricted administrative privileges.
     *
     * <p>Administrators are responsible for platform configuration,
     * user administration, and operational management.
     */
    ADMIN,

    /**
     * Operations team member responsible for day-to-day banking operations.
     *
     * <p>Operations users perform administrative and operational tasks that
     * support the execution of banking processes.
     */
    OPERATIONS,

    /**
     * Compliance officer responsible for regulatory oversight.
     *
     * <p>Compliance officers review customer activity, perform regulatory
     * checks, and ensure adherence to banking compliance requirements.
     */
    COMPLIANCE_OFFICER,

    /**
     * Customer support representative.
     *
     * <p>Support agents assist customers with account-related issues,
     * troubleshooting, and operational requests according to business rules.
     */
    SUPPORT_AGENT,

    /**
     * Standard banking customer.
     *
     * <p>Customers may authenticate and access banking services such as
     * account management, payments, and profile maintenance.
     */
    CUSTOMER

}
