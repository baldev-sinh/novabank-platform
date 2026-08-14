package com.novabank.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final UserId USER_ID =
        new UserId(UUID.randomUUID());

    private static final String FIRST_NAME =
        "Baldev";

    private static final String LAST_NAME =
        "Parmar";

    private static final LocalDate DATE_OF_BIRTH =
        LocalDate.of(2001, 1, 15);

    private static final String PHONE =
        "+919876543210";

    @Test
    @DisplayName("Should register customer")
    void shouldRegisterCustomer() {

        Customer customer = createCustomer();

        assertThat(customer).isNotNull();
        assertThat(customer.id()).isNotNull();
        assertThat(customer.userId()).isEqualTo(USER_ID);
        assertThat(customer.firstName()).isEqualTo(FIRST_NAME);
        assertThat(customer.lastName()).isEqualTo(LAST_NAME);
        assertThat(customer.dateOfBirth()).isEqualTo(DATE_OF_BIRTH);
        assertThat(customer.phone()).isEqualTo(PHONE);
        assertThat(customer.status())
            .isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should generate customer id")
    void shouldGenerateCustomerId() {

        Customer customer = createCustomer();

        assertThat(customer.id()).isNotNull();
        assertThat(customer.id().value()).isNotNull();
    }

    @Test
    @DisplayName("Should associate user id")
    void shouldAssociateUserId() {

        Customer customer = createCustomer();

        assertThat(customer.userId())
            .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should set active status")
    void shouldSetActiveStatus() {

        Customer customer = createCustomer();

        assertThat(customer.status())
            .isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set created at")
    void shouldSetCreatedAt() {

        Customer customer = createCustomer();

        assertThat(customer.createdAt())
            .isNotNull();
    }

    @Test
    @DisplayName("Should set updated at")
    void shouldSetUpdatedAt() {

        Customer customer = createCustomer();

        assertThat(customer.updatedAt())
            .isNotNull();
    }

    @Test
    @DisplayName("Should restore customer")
    void shouldRestoreCustomer() {

        CustomerId customerId = CustomerId.random();
        Instant createdAt = Instant.now().minusSeconds(100);
        Instant updatedAt = Instant.now();

        Customer customer = Customer.restore(
            customerId,
            USER_ID,
            FIRST_NAME,
            LAST_NAME,
            DATE_OF_BIRTH,
            PHONE,
            CustomerStatus.INACTIVE,
            createdAt,
            updatedAt
        );

        assertThat(customer.id())
            .isEqualTo(customerId);

        assertThat(customer.status())
            .isEqualTo(CustomerStatus.INACTIVE);

        assertThat(customer.createdAt())
            .isEqualTo(createdAt);

        assertThat(customer.updatedAt())
            .isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Should activate customer")
    void shouldActivateCustomer() {

        Customer customer = createCustomer();

        customer.deactivate();
        customer.activate();

        assertThat(customer.status())
            .isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should deactivate customer")
    void shouldDeactivateCustomer() {

        Customer customer = createCustomer();

        customer.deactivate();

        assertThat(customer.status())
            .isEqualTo(CustomerStatus.INACTIVE);
    }

    @Test
    @DisplayName("Should block customer")
    void shouldBlockCustomer() {

        Customer customer = createCustomer();

        customer.block();

        assertThat(customer.status())
            .isEqualTo(CustomerStatus.BLOCKED);
    }

    @Test
    @DisplayName("Should reject null user id")
    void shouldRejectNullUserId() {

        assertThatThrownBy(() ->
            Customer.register(
                null,
                FIRST_NAME,
                LAST_NAME,
                DATE_OF_BIRTH,
                PHONE
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage("UserId cannot be null");
    }

    @Test
    @DisplayName("Should reject blank first name")
    void shouldRejectBlankFirstName() {

        assertThatThrownBy(() ->
            Customer.register(
                USER_ID,
                " ",
                LAST_NAME,
                DATE_OF_BIRTH,
                PHONE
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("First name cannot be blank");
    }

    @Test
    @DisplayName("Should reject blank last name")
    void shouldRejectBlankLastName() {

        assertThatThrownBy(() ->
            Customer.register(
                USER_ID,
                FIRST_NAME,
                " ",
                DATE_OF_BIRTH,
                PHONE
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Last name cannot be blank");
    }

    @Test
    @DisplayName("Should reject null date of birth")
    void shouldRejectNullDateOfBirth() {

        assertThatThrownBy(() ->
            Customer.register(
                USER_ID,
                FIRST_NAME,
                LAST_NAME,
                null,
                PHONE
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Date of birth cannot be null");
    }

    @Test
    @DisplayName("Should reject blank phone")
    void shouldRejectBlankPhone() {

        assertThatThrownBy(() ->
            Customer.register(
                USER_ID,
                FIRST_NAME,
                LAST_NAME,
                DATE_OF_BIRTH,
                " "
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Phone cannot be blank");
    }

    private Customer createCustomer() {

        return Customer.register(
            USER_ID,
            FIRST_NAME,
            LAST_NAME,
            DATE_OF_BIRTH,
            PHONE
        );
    }
}
