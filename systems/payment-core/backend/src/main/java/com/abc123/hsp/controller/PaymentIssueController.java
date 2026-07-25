package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.service.PaymentIssueService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付交易异常中心控制器。
 */
@RestController
@RequestMapping("/api/payment-issues")
public class PaymentIssueController {

    private final PaymentIssueService paymentIssueService;

    public PaymentIssueController(PaymentIssueService paymentIssueService) {
        this.paymentIssueService = paymentIssueService;
    }

    /**
     * 查询支付交易异常列表，供运营、研发和测试统一排障使用。
     */
    @GetMapping
    public ApiResponse<PageResultDTO<PaymentIssueRowDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String issueType,
            @RequestParam(defaultValue = "全部") String severity,
            @RequestParam(required = false) String channelCode,
            @RequestParam(defaultValue = "全部") String paymentMethod,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setIssueType(issueType);
        query.setSeverity(severity);
        query.setChannelCode(channelCode);
        query.setPaymentMethod(paymentMethod);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentIssueService.list(query));
    }

    /**
     * 查询当前筛选条件下的责任组全量统计，供运营判断异常处理归口。
     */
    @GetMapping("/responsibility-summary")
    public ApiResponse<List<PaymentIssueResponsibilitySummaryDTO>> responsibilitySummary(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String issueType,
            @RequestParam(defaultValue = "全部") String severity,
            @RequestParam(required = false) String channelCode,
            @RequestParam(defaultValue = "全部") String paymentMethod) {
        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setIssueType(issueType);
        query.setSeverity(severity);
        query.setChannelCode(channelCode);
        query.setPaymentMethod(paymentMethod);
        return ApiResponse.success(paymentIssueService.responsibilitySummary(query));
    }

    /**
     * 查询支付异常告警通知明细，供运营、测试和研发联查供应商投递与回执状态。
     */
    @GetMapping("/alerts")
    public ApiResponse<PageResultDTO<PaymentIssueAlertLogRowDTO>> listAlertLogs(
            @RequestParam(required = false) String alertNo,
            @RequestParam(required = false) String issueNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String alertChannel,
            @RequestParam(defaultValue = "全部") String alertStatus,
            @RequestParam(defaultValue = "全部") String ackStatus,
            @RequestParam(defaultValue = "全部") String providerDeliveryStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
        query.setAlertNo(alertNo);
        query.setIssueNo(issueNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setAlertChannel(alertChannel);
        query.setAlertStatus(alertStatus);
        query.setAckStatus(ackStatus);
        query.setProviderDeliveryStatus(providerDeliveryStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentIssueService.listAlertLogs(query));
    }

    /**
     * 批量分派、跟进或备注支付交易异常。
     */
    @PostMapping("/actions")
    public ApiResponse<PageResultDTO<PaymentIssueRowDTO>> batchAction(@RequestBody PaymentIssueActionRequestDTO request) {
        return ApiResponse.success(paymentIssueService.batchAction(request));
    }
}
