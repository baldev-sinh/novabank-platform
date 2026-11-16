package com.novabank.customer.application.usecase;

import com.novabank.customer.application.response.CustomerDetailsResponse;
import java.util.List;

public interface ListPendingKycUseCase {

  /** The compliance review queue: customers waiting for KYC, oldest first, at most 50. */
  List<CustomerDetailsResponse> listPending();
}
