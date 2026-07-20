package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 分账明细展示对象。
 */
@Data
public class ShareItemDTO {

    private String shareItemNo;
    private String clearingNo;
    private String shareType;
    private String shareTargetNo;
    private String shareTargetName;
    private String shareAmount;
    private String shareStatus;
    private String statusType;
    private String createdAt;
}
