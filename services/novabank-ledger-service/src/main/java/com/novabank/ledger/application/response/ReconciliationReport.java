package com.novabank.ledger.application.response;

import java.util.List;

/**
 * @param balanced {@code true} if both lists are empty
 * @param accountsWithBalanceMismatch cached balance differs from the sum of entries
 * @param unbalancedCurrencies total debits differ from total credits
 */
public record ReconciliationReport(
    boolean balanced,
    List<String> accountsWithBalanceMismatch,
    List<String> unbalancedCurrencies) {}
