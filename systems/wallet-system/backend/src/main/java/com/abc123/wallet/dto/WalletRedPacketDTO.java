package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包红包批次。 */
@Data
public class WalletRedPacketDTO {
    /** 红包批次号。 */
    private String redPacketNo;
    /** 出资钱包账户号。 */
    private String accountNo;
    /** 活动名称。 */
    private String campaignName;
    /** 红包总金额。 */
    private String totalAmount;
    /** 红包个数。 */
    private Integer packetCount;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
