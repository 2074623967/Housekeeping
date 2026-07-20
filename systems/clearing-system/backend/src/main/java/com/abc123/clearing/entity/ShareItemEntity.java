package com.abc123.clearing.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 分账明细实体。
 */
@Data
public class ShareItemEntity {

    private String shareItemNo;
    private String clearingNo;
    private String shareType;
    private String shareTargetNo;
    private String shareTargetName;
    private BigDecimal shareAmount;
    private String shareStatus;
    private String createdAt;
}
