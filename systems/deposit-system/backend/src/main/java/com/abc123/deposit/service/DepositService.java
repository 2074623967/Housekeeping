package com.abc123.deposit.service;

import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.dto.DepositOverviewDTO;
import java.util.List;

/**
 * 保证金业务服务。
 */
public interface DepositService {

    DepositAccountDTO openAccount(DepositAccountCreateRequestDTO request);

    DepositOverviewDTO overview();

    default List<DepositAccountDTO> accounts() {
        return accounts(new DepositAccountQueryDTO());
    }

    List<DepositAccountDTO> accounts(DepositAccountQueryDTO query);

    DepositAccountDetailDTO accountDetail(String accountNo);

    DepositAccountDTO collect(DepositActionRequestDTO request);

    DepositAccountDTO freeze(DepositActionRequestDTO request);

    DepositAccountDTO unfreeze(DepositActionRequestDTO request);

    DepositAccountDTO deduct(DepositActionRequestDTO request);

    DepositAccountDTO refund(DepositActionRequestDTO request);

    DepositAccountDTO offsetDebt(DebtOffsetRequestDTO request);

    default List<DepositFlowDTO> flows(String accountNo) {
        return flows(accountNo, new DepositFlowQueryDTO());
    }

    List<DepositFlowDTO> flows(String accountNo, DepositFlowQueryDTO query);
}
