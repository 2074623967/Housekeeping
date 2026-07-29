package com.abc123.accounting.service;

import com.abc123.accounting.dto.CreateFreezeRequestDTO;
import com.abc123.accounting.dto.FreezeItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.UnfreezeRequestDTO;

/**
 * 冻结服务。
 */
public interface FreezeService {

    PageResultDTO<FreezeItemDTO> list(String accountNo, String freezeStatus, int pageNo, int pageSize);

    FreezeItemDTO create(CreateFreezeRequestDTO request);

    FreezeItemDTO unfreeze(String freezeNo, UnfreezeRequestDTO request);
}
