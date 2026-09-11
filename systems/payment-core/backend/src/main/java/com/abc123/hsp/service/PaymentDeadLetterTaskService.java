package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import org.springframework.amqp.core.Message;

/** MQ 死信补偿任务服务。 */
public interface PaymentDeadLetterTaskService {

    /** 明确不可映射到任何受控重放目标的永久路由错误。 */
    final class UnsupportedDeadLetterRouteException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public UnsupportedDeadLetterRouteException() {
            super("不支持的死信路由");
        }
    }

    /** 无法安全持久化或重放的永久消息元数据错误。 */
    final class InvalidDeadLetterMessageException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public InvalidDeadLetterMessageException() {
            super("死信消息元数据非法");
        }
    }

    void intake(Message message, String deadLetterRoutingKey);

    PageResultDTO<PaymentDeadLetterTaskEntity> list(PaymentDeadLetterTaskQueryDTO query);

    PaymentDeadLetterTaskEntity markReadyToReplay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);

    PaymentDeadLetterTaskEntity markManuallyResolved(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);

    PaymentDeadLetterTaskEntity replay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request);
}
