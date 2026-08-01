package com.abc123.hsp.dto;

import lombok.Data;

/** MQ 死信补偿任务查询条件。 */
@Data
public class PaymentDeadLetterTaskQueryDTO {

    private String taskStatus;
    private String targetSystem;
    private String messageId;
    private int pageNo;
    private int pageSize;

    public int getOffset() {
        return (Math.max(pageNo, 1) - 1) * Math.max(pageSize, 1);
    }

    public int getLimit() {
        return Math.max(pageSize, 1);
    }
}
