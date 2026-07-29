package com.abc123.settlement.service;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;

/**
 * 结算事件服务。
 */
public interface SettlementEventService {

    PageResultDTO<SettlementEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize);

    SettlementEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request);
}
