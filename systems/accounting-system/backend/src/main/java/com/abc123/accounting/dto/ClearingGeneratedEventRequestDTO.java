package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果事件请求。
 */
@Data
public class ClearingGeneratedEventRequestDTO {

    /** 跨系统业务链路 ID；历史事件未传时按清分单号兼容生成。 */
    private String businessChainId;
    private String accountNo;
    private String clearingOrderNo;
    private String bizNo;
    private BigDecimal amount;
    private String summary;
}
