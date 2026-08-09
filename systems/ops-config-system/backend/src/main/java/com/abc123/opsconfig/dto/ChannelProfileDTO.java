package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 渠道档案视图。
 */
@Data
public class ChannelProfileDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 渠道名称。 */
    private String channelName;
    /** 渠道类型。 */
    private String channelType;
    /** 商户号模板。 */
    private String merchantProfile;
    /** 退款时效。 */
    private String refundSla;
    /** 风控标签。 */
    private String riskTag;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
