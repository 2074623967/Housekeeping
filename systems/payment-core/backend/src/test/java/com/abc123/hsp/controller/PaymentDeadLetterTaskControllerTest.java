package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** MQ 死信补偿任务控制器测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentDeadLetterTaskControllerTest {

    @Mock
    private PaymentDeadLetterTaskService paymentDeadLetterTaskService;

    @Test
    void shouldListDeadLetterTasks() {
        new PaymentDeadLetterTaskController(paymentDeadLetterTaskService)
                .list("PENDING_REVIEW", "clearing-system", "MSG-001", 1, 20);

        verify(paymentDeadLetterTaskService).list(any());
    }

    @Test
    void shouldReplayByTaskNo() {
        PaymentDeadLetterTaskActionRequestDTO request = new PaymentDeadLetterTaskActionRequestDTO();

        new PaymentDeadLetterTaskController(paymentDeadLetterTaskService).replay("DLQ-001", request);

        verify(paymentDeadLetterTaskService).replay("DLQ-001", request);
    }
}
