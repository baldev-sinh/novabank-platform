package com.novabank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.novabank.customer.application.command.RegisterCustomerCommand;
import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.application.response.RegisterCustomerResponse;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.model.CustomerStatus;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterCustomerServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private static final String FIRST_NAME = "Baldev";

  private static final String LAST_NAME = "Parmar";

  private static final LocalDate DATE_OF_BIRTH = LocalDate.of(2001, 1, 15);

  private static final String PHONE = "+919876543210";

  @Mock private CustomerRepository repository;

  private RegisterCustomerService service;

  @BeforeEach
  void setUp() {
    service = new RegisterCustomerService(repository);
  }

  @Test
  @DisplayName("Should register customer successfully")
  void shouldRegisterCustomerSuccessfully() {

    RegisterCustomerCommand command = createCommand();

    when(repository.existsByUserId(any(UserId.class))).thenReturn(false);

    Customer customer =
        Customer.register(new UserId(USER_ID), FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, PHONE);

    when(repository.save(any(Customer.class))).thenReturn(customer);

    RegisterCustomerResponse response = service.register(command);

    assertThat(response).isNotNull();
    assertThat(response.customerId()).isEqualTo(customer.id().value());
    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.firstName()).isEqualTo(FIRST_NAME);
    assertThat(response.lastName()).isEqualTo(LAST_NAME);
    assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);

    verify(repository).existsByUserId(new UserId(USER_ID));

    verify(repository).save(any(Customer.class));

    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("Should reject null command")
  void shouldRejectNullCommand() {

    assertThatThrownBy(() -> service.register(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("command cannot be null");

    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("Should reject duplicate customer")
  void shouldRejectDuplicateCustomer() {

    RegisterCustomerCommand command = createCommand();

    when(repository.existsByUserId(any(UserId.class))).thenReturn(true);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(DuplicateCustomerException.class)
        .hasMessage("Customer already exists for user: " + USER_ID);

    verify(repository).existsByUserId(new UserId(USER_ID));

    verify(repository, never()).save(any(Customer.class));

    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("Should propagate repository failure during existence check")
  void shouldPropagateRepositoryFailureDuringExistenceCheck() {

    RegisterCustomerCommand command = createCommand();

    when(repository.existsByUserId(any(UserId.class)))
        .thenThrow(new RuntimeException("Database unavailable"));

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Database unavailable");

    verify(repository).existsByUserId(any(UserId.class));

    verify(repository, never()).save(any(Customer.class));

    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("Should propagate repository failure during save")
  void shouldPropagateRepositoryFailureDuringSave() {

    RegisterCustomerCommand command = createCommand();

    when(repository.existsByUserId(any(UserId.class))).thenReturn(false);

    when(repository.save(any(Customer.class)))
        .thenThrow(new RuntimeException("Database unavailable"));

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Database unavailable");

    verify(repository).existsByUserId(any(UserId.class));

    verify(repository).save(any(Customer.class));

    verifyNoMoreInteractions(repository);
  }

  private RegisterCustomerCommand createCommand() {

    return new RegisterCustomerCommand(USER_ID, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, PHONE);
  }
}
