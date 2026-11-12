package com.novabank.customer.application.usecase;

import com.novabank.customer.application.response.CustomerDetailsResponse;
import java.util.UUID;

public interface GetCustomerByUserIdUseCase {

  /**
   * @throws com.novabank.customer.application.exception.CustomerNotFoundException if the user has
   *     no customer profile
   */
  CustomerDetailsResponse getByUserId(UUID userId);
}
