package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import org.springframework.amqp.core.Message;

/** MQ 死信补偿任务服务。 */
public interface PaymentDeadLetterTaskService {

    void intake(Message message, String deadLetterRoutingKey);

    PageResultDTO<PaymentDeadLetterTaskEntity> list(PaymentDeadLetterTaskQueryDTO query);

    PaymentDeadLetterTaskEntity markReadyToReplay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);

    PaymentDeadLetterTaskEntity markManuallyResolved(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);

    PaymentDeadLetterTaskEntity replay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);
}
