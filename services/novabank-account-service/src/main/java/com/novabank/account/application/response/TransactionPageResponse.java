package com.novabank.account.application.response;

import java.util.List;

/**
 * @param nextBefore pass as {@code before} to get the next (older) page; null on the last page
 */
public record TransactionPageResponse(List<TransactionResponse> transactions, Long nextBefore) {}
