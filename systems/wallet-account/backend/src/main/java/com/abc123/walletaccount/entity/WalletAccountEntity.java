package com.abc123.walletaccount.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletAccountEntity {

    /** 数据库主键。 */
    private Long id;
    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 钱包主体编号。 */
    private String walletOwnerId;
    /** 主体类型。 */
    private String ownerType;
    /** 主体名称。 */
    private String ownerName;
    /** 账户类型。 */
    private String accountType;
    /** 账户场景。 */
    private String accountScene;
    /** 币种编码。 */
    private String currencyCode;
    /** 账户状态。 */
    private String accountStatus;
    /** 是否允许透支。 */
    private Boolean allowCredit;
    /** 风险等级。 */
    private String riskLevel;
    /** 总余额。 */
    private BigDecimal totalBalance;
    /** 可用余额。 */
    private BigDecimal availableBalance;
    /** 冻结余额。 */
    private BigDecimal frozenBalance;
    /** 在途入账金额。 */
    private BigDecimal pendingInBalance;
    /** 在途出账金额。 */
    private BigDecimal pendingOutBalance;
    /** 开户时间。 */
    private LocalDateTime openedAt;
    /** 销户时间。 */
    private LocalDateTime closedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
