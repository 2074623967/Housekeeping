package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 协议模板视图。
 */
@Data
public class AgreementTemplateDTO {

    /** 模板编码。 */
    private String templateCode;
    /** 模板名称。 */
    private String templateName;
    /** 适用主体。 */
    private String subjectType;
    /** 签约要素。 */
    private String signFields;
    /** 电子签章服务商。 */
    private String esignProvider;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
