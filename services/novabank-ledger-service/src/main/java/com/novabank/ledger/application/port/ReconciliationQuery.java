package com.novabank.ledger.application.port;

import java.util.List;

public interface ReconciliationQuery {

  /** Accounts whose cached balance differs from the sum of their entries. */
  List<String> accountsWithBalanceMismatch();

  /** Currencies in which total debits differ from total credits. */
  List<String> unbalancedCurrencies();
}
