package com.novabank.customer.application.usecase;

import com.novabank.customer.application.command.RegisterCustomerCommand;
import com.novabank.customer.application.response.RegisterCustomerResponse;

public interface RegisterCustomerUseCase {

  RegisterCustomerResponse register(RegisterCustomerCommand command);
}
