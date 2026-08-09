package com.abc123.accounting.service;

import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.dto.PageResultDTO;

/**
 * 流水服务。
 */
public interface LedgerService {

    PageResultDTO<LedgerItemDTO> list(String accountNo, String bizNo, String bizType, int pageNo, int pageSize);

    /**
     * 导出当前筛选条件下的账户流水快照。
     */
    byte[] exportSnapshot(String accountNo, String bizNo, String bizType);
}
