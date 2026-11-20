package com.novabank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.novabank.account.application.port.funds.BalanceProjectionQuery;
import com.novabank.account.application.port.funds.BalanceProjectionQuery.ProjectedBalance;
import com.novabank.account.application.usecase.CheckProjectionUseCase.LedgerBalance;
import com.novabank.account.application.usecase.CheckProjectionUseCase.Result;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckProjectionServiceTest {

  @Mock private BalanceProjectionQuery projection;

  @Test
  @DisplayName("Only an equal sequence with a different balance is drift; timing gaps are not")
  void classifiesEachAccount() {
    UUID same = UUID.randomUUID();
    UUID scaleOnly = UUID.randomUUID();
    UUID drifted = UUID.randomUUID();
    UUID behind = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    UUID unknown = UUID.randomUUID();
    when(projection.find(any()))
        .thenReturn(
            Map.of(
                same, new ProjectedBalance(new BigDecimal("100.00"), 3),
                scaleOnly, new ProjectedBalance(new BigDecimal("5.0000"), 1),
                drifted, new ProjectedBalance(new BigDecimal("90.00"), 2),
                behind, new ProjectedBalance(new BigDecimal("10.00"), 1),
                newer, new ProjectedBalance(new BigDecimal("30.00"), 4)));

    Result result =
        new CheckProjectionService(projection)
            .check(
                List.of(
                    ledger(same, "100.00", 3),
                    ledger(scaleOnly, "5.00", 1),
                    ledger(drifted, "100.00", 2),
                    ledger(behind, "20.00", 2),
                    ledger(newer, "25.00", 3),
                    ledger(unknown, "1.00", 1)));

    assertThat(result.matched()).isEqualTo(2);
    assertThat(result.drifted()).containsExactly(drifted);
    assertThat(result.lagging()).isOne();
    assertThat(result.ahead()).isOne();
    assertThat(result.unknown()).isOne();
  }

  private static LedgerBalance ledger(UUID account, String balance, long sequence) {
    return new LedgerBalance(account, new BigDecimal(balance), sequence);
  }
}
