package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议版本回滚请求。
 */
@Data
public class PaymentProtocolRollbackRequestDTO {

    /** 目标历史版本号。 */
    private Integer versionNo;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    /** 回滚原因。 */
    private String changeReason;
}
