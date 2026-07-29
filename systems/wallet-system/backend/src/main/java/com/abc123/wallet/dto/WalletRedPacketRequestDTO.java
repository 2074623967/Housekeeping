package com.abc123.wallet.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钱包红包发放请求。 */
@Data
public class WalletRedPacketRequestDTO {
    /** 出资钱包账户号。 */
    private String accountNo;
    /** 活动名称。 */
    private String campaignName;
    /** 红包总金额。 */
    private BigDecimal totalAmount;
    /** 红包个数。 */
    private Integer packetCount;
    /** 操作人。 */
    private String operatorName;
}
