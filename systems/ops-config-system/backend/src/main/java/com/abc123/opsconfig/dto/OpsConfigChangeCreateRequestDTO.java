package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 创建运营配置变更请求。
 */
@Data
public class OpsConfigChangeCreateRequestDTO {

    /** 配置类型，例如 ROUTING_RULE。 */
    private String configType;
    /** 配置编码。 */
    private String configCode;
    /** 目标启停状态。 */
    private Boolean enabled;
    /** 变更原因。 */
    private String reason;
    /** 灰度范围，例如 ALL、APP、HOME_CLEAN。 */
    private String grayScope;
    /** 灰度开始时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String grayStartAt;
    /** 灰度结束时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String grayEndAt;
    /** 申请人。 */
    private String applicant;
}
