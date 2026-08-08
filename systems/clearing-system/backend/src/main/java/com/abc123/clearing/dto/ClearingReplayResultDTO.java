package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分事件重放结果。
 */
@Data
public class ClearingReplayResultDTO {

    /** 本次尝试重放数量。 */
    private Integer attemptedCount;
    /** 当前说明。 */
    private String message;
}
