package com.abc123.walletaccount.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletFlowEntity {

    private Long id;
    private String flowNo;
    private String walletAccountNo;
    private String flowType;
    private String sourceSystem;
    private String sourceBizNo;
    private String idempotencyKey;
    private BigDecimal changeAmount;
    private BigDecimal beforeAvailableBalance;
    private BigDecimal afterAvailableBalance;
    private String operatorName;
    private String operationReason;
    private LocalDateTime createdAt;
}
