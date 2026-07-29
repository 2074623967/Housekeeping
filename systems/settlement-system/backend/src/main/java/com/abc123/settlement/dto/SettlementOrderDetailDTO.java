package com.abc123.settlement.dto;

import java.util.List;
import lombok.Data;

/**
 * 结算单详情对象。
 */
@Data
public class SettlementOrderDetailDTO {

    private SettlementOrderDTO order;
    private List<SettlementItemDTO> items;
    private List<SettlementAuditLogDTO> auditLogs;
}
