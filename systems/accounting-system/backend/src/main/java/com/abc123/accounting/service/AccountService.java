package com.abc123.accounting.service;

import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.dto.AccountListItemDTO;
import com.abc123.accounting.dto.OpenAccountRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;

/**
 * 账户服务。
 */
public interface AccountService {

    PageResultDTO<AccountListItemDTO> list(String subjectId, String accountType, String status, int pageNo, int pageSize);

    AccountListItemDTO open(OpenAccountRequestDTO request);

    AccountDetailDTO detail(String accountNo);

    /**
     * 导出账户详情快照，供运营、财务和测试做留档与问题复盘。
     *
     * @param accountNo 账户号
     * @return CSV 二进制内容
     */
    byte[] exportDetailSnapshot(String accountNo);
}
