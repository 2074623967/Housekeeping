package com.abc123.walletaccount.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletAccountEntity {

    private Long id;
    private String walletAccountNo;
    private String walletOwnerId;
    private String ownerType;
    private String ownerName;
    private String accountType;
    private String accountScene;
    private String currencyCode;
    private String accountStatus;
    private Boolean allowCredit;
    private String riskLevel;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal pendingInBalance;
    private BigDecimal pendingOutBalance;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
