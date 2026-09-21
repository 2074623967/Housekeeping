package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskOperatorRequestDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付任务中心控制器。
 */
@RestController
@RequestMapping("/api/payment-task-center")
public class PaymentTaskCenterController {

    private final PaymentTaskCenterService paymentTaskCenterService;

    public PaymentTaskCenterController(PaymentTaskCenterService paymentTaskCenterService) {
        this.paymentTaskCenterService = paymentTaskCenterService;
    }

    /**
     * 查询支付任务中心总览。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentTaskCenterOverviewDTO> overview() {
        return ApiResponse.success(paymentTaskCenterService.overview());
    }

    /**
     * 查询支付任务执行日志列表。
     */
    @GetMapping("/task-runs")
    public ApiResponse<PageResultDTO<PaymentTaskRunLogItemDTO>> listTaskRuns(
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String bizDate,
            @RequestParam(defaultValue = "全部") String runMode,
            @RequestParam(defaultValue = "全部") String taskStatus,
            @RequestParam(defaultValue = "全部") String severityLevel,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        PaymentTaskRunLogQueryDTO query = new PaymentTaskRunLogQueryDTO();
        query.setTaskCode(taskCode);
        query.setBizDate(bizDate);
        query.setRunMode(runMode);
        query.setTaskStatus(taskStatus);
        query.setSeverityLevel(severityLevel);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentTaskCenterService.listTaskRuns(query));
    }

    @GetMapping("/task-runs/{taskLogNo}")
    public ApiResponse<PaymentTaskRunLogItemDTO> taskRunDetail(@PathVariable String taskLogNo) {
        return ApiResponse.success(paymentTaskCenterService.taskRunDetail(taskLogNo));
    }

    /**
     * 导出支付任务执行日志。
     */
    @GetMapping(value = "/task-runs/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportTaskRuns(
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String bizDate,
            @RequestParam(defaultValue = "全部") String runMode,
            @RequestParam(defaultValue = "全部") String taskStatus,
            @RequestParam(defaultValue = "全部") String severityLevel) {
        PaymentTaskRunLogQueryDTO query = new PaymentTaskRunLogQueryDTO();
        query.setTaskCode(taskCode);
        query.setBizDate(bizDate);
        query.setRunMode(runMode);
        query.setTaskStatus(taskStatus);
        query.setSeverityLevel(severityLevel);
        byte[] csvBytes = paymentTaskCenterService.exportTaskRunsCsv(query).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-task-runs.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }

    /**
     * 手动执行超时关单任务。
     */
    @PostMapping("/close-expired-payments")
    public ApiResponse<PaymentTaskActionResultDTO> runCloseExpiredPayments(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runCloseExpiredPayments(operatorOf(request)));
    }

    /**
     * 手动执行待回调支付单主动查单补偿。
     */
    @PostMapping("/query-pending-payments")
    public ApiResponse<PaymentTaskActionResultDTO> runPendingPaymentQueries(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runPendingPaymentQueries(operatorOf(request)));
    }

    /**
     * 手动执行处理中退款主动查单补偿。
     */
    @PostMapping("/query-pending-refunds")
    public ApiResponse<PaymentTaskActionResultDTO> runPendingRefundQueries(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runPendingRefundQueries(operatorOf(request)));
    }

    /**
     * 手动生成主动查单监控告警。
     */
    @PostMapping("/generate-query-monitor-alerts")
    public ApiResponse<PaymentTaskActionResultDTO> runGenerateQueryMonitorAlerts(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runGenerateQueryMonitorAlerts(operatorOf(request)));
    }

    /**
     * 手动执行失败事件重发。
     */
    @PostMapping("/republish-failed-events")
    public ApiResponse<PaymentTaskActionResultDTO> runRepublishFailedEvents(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runRepublishFailedEvents(operatorOf(request)));
    }

    /**
     * 手动执行失败退款重试。
     */
    @PostMapping("/retry-failed-refunds")
    public ApiResponse<PaymentTaskActionResultDTO> runRetryFailedRefunds(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runRetryFailedRefunds(operatorOf(request)));
    }

    /**
     * 手动执行异常 SLA 升级巡检。
     */
    @PostMapping("/escalate-overdue-issues")
    public ApiResponse<PaymentTaskActionResultDTO> runEscalateOverdueIssues(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runEscalateOverdueIssues(operatorOf(request)));
    }

    /**
     * 手动执行异常告警派发。
     */
    @PostMapping("/dispatch-issue-alerts")
    public ApiResponse<PaymentTaskActionResultDTO> runDispatchIssueAlerts(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runDispatchIssueAlerts(operatorOf(request)));
    }

    /**
     * 手动执行异常告警供应商回执回查。
     */
    @PostMapping("/reconcile-issue-alert-receipts")
    public ApiResponse<PaymentTaskActionResultDTO> runReconcileIssueAlertReceipts(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runReconcileIssueAlertReceipts(operatorOf(request)));
    }

    /**
     * 手动执行指定异常告警的供应商回执回查。
     */
    @PostMapping("/reconcile-issue-alert-receipts/{alertNo}")
    public ApiResponse<PaymentTaskActionResultDTO> runReconcileIssueAlertReceipt(
            @PathVariable String alertNo,
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runReconcileIssueAlertReceipt(
                alertNo, operatorOf(request)));
    }

    /**
     * 手动执行支付控制策略自动巡检。
     */
    @PostMapping("/control-policy-self-checks")
    public ApiResponse<PaymentTaskActionResultDTO> runControlPolicySelfChecks(
            @RequestBody(required = false) PaymentTaskOperatorRequestDTO request) {
        return ApiResponse.success(paymentTaskCenterService.runControlPolicySelfChecks(operatorOf(request)));
    }

    private String operatorOf(PaymentTaskOperatorRequestDTO request) {
        return request == null ? null : request.getOperator();
    }
}
