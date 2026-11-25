package com.novabank.payment.application.response;

import java.util.List;

/**
 * One page of a customer's payments, newest first.
 *
 * @param nextCursor where the next (older) page starts; null on the last page
 */
public record PaymentPage(List<PaymentResponse> payments, String nextCursor) {}
