package com.novabank.customer.application.service;

import com.novabank.customer.application.response.CustomerDetailsResponse;
import com.novabank.customer.application.usecase.ListPendingKycUseCase;
import com.novabank.customer.domain.repository.CustomerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPendingKycService implements ListPendingKycUseCase {

  static final int MAX_RESULTS = 50;

  private final CustomerRepository customers;

  @Override
  @Transactional(readOnly = true)
  public List<CustomerDetailsResponse> listPending() {
    return customers.findPendingKyc(MAX_RESULTS).stream()
        .map(CustomerDetailsResponse::from)
        .toList();
  }
}
