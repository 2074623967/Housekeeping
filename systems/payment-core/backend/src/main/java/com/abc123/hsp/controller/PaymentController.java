package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import com.abc123.hsp.service.PaymentService;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 查询支付单列表，供后台运营页展示。
     */
    @GetMapping
    public ApiResponse<List<PaymentListItemDTO>> list() {
        return ApiResponse.success(paymentService.list());
    }

    /**
     * 查询支付单详情，返回基本信息和全量轨迹日志。
     */
    @GetMapping("/{paymentOrderId}")
    public ApiResponse<PaymentDetailDTO> detail(@PathVariable String paymentOrderId) {
        return ApiResponse.success(paymentService.detail(paymentOrderId));
    }

    /**
     * 基于订单发起支付，生成账单、支付单和预付单。
     */
    @PostMapping("/prepay")
    public ApiResponse<PrepayOrderDTO> prepay(@RequestBody PrepayRequestDTO request) {
        return ApiResponse.success(paymentService.prepay(request));
    }

    /**
     * 获取收银台页面所需的订单摘要、金额和支付方式。
     */
    @GetMapping("/cashier/{prepayOrderNo}")
    public ApiResponse<CashierPageDTO> cashier(@PathVariable String prepayOrderNo) {
        return ApiResponse.success(paymentService.cashier(prepayOrderNo));
    }

    /**
     * 提交支付，记录支付尝试、路由轨迹和事件轨迹。
     */
    @PostMapping("/submit")
    public ApiResponse<PrepayOrderDTO> submit(@RequestBody PaymentSubmitRequestDTO request,
            HttpServletRequest servletRequest) {
        if (!StringUtils.hasText(request.getClientIp())) {
            request.setClientIp(resolveClientIp(servletRequest));
        }
        if (!StringUtils.hasText(request.getTerminal())) {
            request.setTerminal(resolveTerminal(servletRequest));
        }
        return ApiResponse.success(paymentService.submit(request));
    }

    /**
     * 接收渠道回调并收口支付状态。
     */
    @PostMapping("/callback/{channel}")
    public ApiResponse<PaymentDetailDTO> callback(@PathVariable String channel, @RequestBody PaymentCallbackRequestDTO request) {
        return ApiResponse.success(paymentService.callback(channel, request));
    }

    /**
     * 主动查单，当前版本以查询本地支付详情为主。
     */
    @PostMapping("/query")
    public ApiResponse<PaymentDetailDTO> query(@RequestBody PaymentQueryRequestDTO request) {
        return ApiResponse.success(paymentService.query(request));
    }

    /**
     * 关闭支付单，供超时关闭或人工终止使用。
     */
    @PostMapping("/close")
    public ApiResponse<PaymentDetailDTO> close(@RequestBody PaymentCloseRequestDTO request) {
        return ApiResponse.success(paymentService.close(request));
    }

    /**
     * 优先读取代理链路中的真实客户端 IP，避免只记录网关地址。
     */
    private String resolveClientIp(HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return servletRequest.getRemoteAddr();
    }

    /**
     * 终端优先从请求头透传，没有时再基于 User-Agent 做兜底判断。
     */
    private String resolveTerminal(HttpServletRequest servletRequest) {
        String terminal = servletRequest.getHeader("X-Terminal");
        if (StringUtils.hasText(terminal)) {
            return terminal.trim();
        }
        String userAgent = servletRequest.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "UNKNOWN";
        }
        String lowerCaseUserAgent = userAgent.toLowerCase();
        if (lowerCaseUserAgent.contains("iphone")
                || lowerCaseUserAgent.contains("android")
                || lowerCaseUserAgent.contains("mobile")) {
            return "MOBILE_WEB";
        }
        return "PC_WEB";
    }
}
