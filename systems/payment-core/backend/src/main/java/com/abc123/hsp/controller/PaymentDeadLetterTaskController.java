package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MQ 死信补偿任务控制器。 */
@RestController
@RequestMapping("/api/payment-dead-letter-tasks")
public class PaymentDeadLetterTaskController {

    private final PaymentDeadLetterTaskService paymentDeadLetterTaskService;

    public PaymentDeadLetterTaskController(PaymentDeadLetterTaskService paymentDeadLetterTaskService) {
        this.paymentDeadLetterTaskService = paymentDeadLetterTaskService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<PaymentDeadLetterTaskEntity>> list(
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String targetSystem,
            @RequestParam(required = false) String messageId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentDeadLetterTaskQueryDTO query = new PaymentDeadLetterTaskQueryDTO();
        query.setTaskStatus(taskStatus);
        query.setTargetSystem(targetSystem);
        query.setMessageId(messageId);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentDeadLetterTaskService.list(query));
    }

    @PostMapping("/{taskNo}/ready-to-replay")
    public ApiResponse<PaymentDeadLetterTaskEntity> markReadyToReplay(
            @PathVariable String taskNo,
            @RequestBody PaymentDeadLetterTaskActionRequestDTO request) {
        return ApiResponse.success(paymentDeadLetterTaskService.markReadyToReplay(taskNo, request));
    }

    @PostMapping("/{taskNo}/resolve-manually")
    public ApiResponse<PaymentDeadLetterTaskEntity> markManuallyResolved(
            @PathVariable String taskNo,
            @RequestBody PaymentDeadLetterTaskActionRequestDTO request) {
        return ApiResponse.success(paymentDeadLetterTaskService.markManuallyResolved(taskNo, request));
    }

    @PostMapping("/{taskNo}/replay")
    public ApiResponse<PaymentDeadLetterTaskEntity> replay(
            @PathVariable String taskNo,
            @RequestBody PaymentDeadLetterTaskActionRequestDTO request) {
        return ApiResponse.success(paymentDeadLetterTaskService.replay(taskNo, request));
    }
}
