package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 结算补偿事件请求。
 */
@Data
public class SettlementCompensationEventRequestDTO {

    /** 账户号。 */
    private String accountNo;
    /** 结算补偿单号。 */
    private String settlementOrderNo;
    /** 原始业务单号。 */
    private String bizNo;
    /** 补偿金额。 */
    private BigDecimal amount;
    /** 补偿方向，支持 贷方 / 借方。 */
    private String compensationDirection;
    /** 补偿摘要。 */
    private String summary;
    /** 操作人。 */
    private String operatorName;
}
