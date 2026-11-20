package com.novabank.account.application.service;

import com.novabank.account.application.port.funds.BalanceProjectionQuery;
import com.novabank.account.application.port.funds.BalanceProjectionQuery.ProjectedBalance;
import com.novabank.account.application.usecase.CheckProjectionUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The projection applies ledger entries in sequence order and stores the balance the ledger
 * reported with each one, so at equal sequence numbers the balances must be equal. Any other
 * difference is just timing: the projection or the snapshot is a few entries behind the other.
 */
@Service
@RequiredArgsConstructor
public class CheckProjectionService implements CheckProjectionUseCase {

  private final BalanceProjectionQuery projection;

  @Override
  @Transactional(readOnly = true)
  public Result check(List<LedgerBalance> snapshot) {
    Map<UUID, ProjectedBalance> projected =
        projection.find(snapshot.stream().map(LedgerBalance::accountId).toList());
    int matched = 0;
    int lagging = 0;
    int ahead = 0;
    int unknown = 0;
    List<UUID> drifted = new ArrayList<>();
    for (LedgerBalance ledger : snapshot) {
      ProjectedBalance mine = projected.get(ledger.accountId());
      if (mine == null) {
        unknown++;
      } else if (mine.sequence() < ledger.sequence()) {
        lagging++;
      } else if (mine.sequence() > ledger.sequence()) {
        ahead++;
      } else if (mine.balance().compareTo(ledger.balance()) == 0) {
        matched++;
      } else {
        drifted.add(ledger.accountId());
      }
    }
    return new Result(matched, drifted, lagging, ahead, unknown);
  }
}
