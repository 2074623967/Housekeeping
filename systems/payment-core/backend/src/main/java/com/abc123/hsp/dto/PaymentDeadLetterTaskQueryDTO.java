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

    public int getPageNo() {
        return PageQueryBounds.normalizePageNo(pageNo);
    }

    public int getOffset() {
        return PageQueryBounds.offset(pageNo, pageSize);
    }

    public int getLimit() {
        return PageQueryBounds.normalizePageSize(pageSize);
    }

    public String getMessageIdLikePattern() {
        return LikePattern.escape(messageId);
    }
}
