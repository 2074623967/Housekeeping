package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 有效配置快照查询条件。
 */
@Data
public class OpsConfigSnapshotQueryDTO {

    /** 业务线编码。 */
    private String businessCode;
    /** 支付类型编码。 */
    private String payType;
    /** 终端类型。 */
    private String terminalType;
}
