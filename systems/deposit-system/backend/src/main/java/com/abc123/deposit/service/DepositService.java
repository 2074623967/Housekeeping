package com.abc123.deposit.service;

import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import java.util.List;

/**
 * 保证金业务服务。
 */
public interface DepositService {

    DepositAccountDTO openAccount(DepositAccountCreateRequestDTO request);

    List<DepositAccountDTO> accounts();

    DepositAccountDTO collect(DepositActionRequestDTO request);

    DepositAccountDTO freeze(DepositActionRequestDTO request);

    DepositAccountDTO unfreeze(DepositActionRequestDTO request);

    DepositAccountDTO deduct(DepositActionRequestDTO request);

    DepositAccountDTO refund(DepositActionRequestDTO request);

    DepositAccountDTO offsetDebt(DebtOffsetRequestDTO request);

    List<DepositFlowDTO> flows(String accountNo);
}
