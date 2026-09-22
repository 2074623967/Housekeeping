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
import com.abc123.deposit.dto.DepositTotalAccountDTO;
import com.abc123.deposit.dto.DepositTotalAccountDetailDTO;
import com.abc123.deposit.dto.DepositV2OverviewDTO;
import com.abc123.deposit.dto.DepositMigrationPreviewDTO;
import com.abc123.deposit.dto.PageResultDTO;
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

    PageResultDTO<DepositAccountDTO> accountsPage(DepositAccountQueryDTO query);

    byte[] exportAccounts(DepositAccountQueryDTO query);

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

    PageResultDTO<DepositFlowDTO> flowsPage(String accountNo, DepositFlowQueryDTO query);

    byte[] exportFlows(String accountNo, DepositFlowQueryDTO query);

    /**
     * 查询 V2 演进概览。
     */
    DepositV2OverviewDTO v2Overview();

    /**
     * 查询总保证金账户预览列表。
     */
    List<DepositTotalAccountDTO> totalAccounts();

    /**
     * 查询总保证金账户预览详情。
     */
    DepositTotalAccountDetailDTO totalAccountDetail(String totalAccountNo);

    /**
     * 查询旧账户结转预览列表。
     */
    List<DepositMigrationPreviewDTO> migrationPreview();
}
