package com.abc123.accounting.service;

import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;

/**
 * 余额服务。
 */
public interface BalanceService {

    BalanceSnapshotDTO detail(String accountNo);

    BalanceSnapshotDTO credit(BalanceOperationRequestDTO request);

    BalanceSnapshotDTO freeze(BalanceOperationRequestDTO request);

    BalanceSnapshotDTO unfreeze(BalanceOperationRequestDTO request);
}
