package com.abc123.settlement.service;

import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreateSettlementOrderRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.dto.SettlementOrderDetailDTO;

/**
 * 结算单服务。
 */
public interface SettlementOrderService {

    PageResultDTO<SettlementOrderDTO> list(String batchNo, String targetType, String settlementStatus, int pageNo, int pageSize);

    SettlementOrderDTO create(CreateSettlementOrderRequestDTO request);

    SettlementOrderDTO detail(String settlementNo);

    SettlementOrderDTO audit(String settlementNo, AuditSettlementRequestDTO request);

    SettlementOrderDTO reject(String settlementNo, AuditSettlementRequestDTO request);

    SettlementOrderDetailDTO fullDetail(String settlementNo);
}
