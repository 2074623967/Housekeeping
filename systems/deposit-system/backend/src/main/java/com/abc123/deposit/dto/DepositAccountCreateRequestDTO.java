package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 开立保证金账户请求。
 */
@Data
public class DepositAccountCreateRequestDTO {

    /** 资金主体编号。 */
    private String ownerId;
    /** 资金主体类型。 */
    private String ownerType;
    /** 初始应缴金额。 */
    private BigDecimal requiredAmount;
}
