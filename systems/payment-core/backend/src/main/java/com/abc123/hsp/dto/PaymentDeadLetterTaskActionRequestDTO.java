package com.abc123.hsp.dto;

import lombok.Data;

/** MQ 死信补偿任务人工操作请求。 */
@Data
public class PaymentDeadLetterTaskActionRequestDTO {

    private String operator;
    private String resolutionNote;
}
