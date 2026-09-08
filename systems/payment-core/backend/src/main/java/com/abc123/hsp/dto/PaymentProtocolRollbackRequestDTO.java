package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议版本回滚请求。
 */
@Data
public class PaymentProtocolRollbackRequestDTO {

    /** 目标历史版本号。 */
    private Integer versionNo;
    /** 操作人看到的当前版本号，用于阻止陈旧页面覆盖新版本。 */
    private Integer baseVersionNo;
    /** 回滚原因。 */
    private String changeReason;
}
