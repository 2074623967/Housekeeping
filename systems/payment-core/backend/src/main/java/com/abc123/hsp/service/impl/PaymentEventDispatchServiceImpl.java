package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 支付事件下游投递实现。
 */
@Service
public class PaymentEventDispatchServiceImpl implements PaymentEventDispatchService {

    private static final String PAYMENT_SUCCESS_EVENT_TYPE = "PAYMENT_SUCCESS";
    private static final String REFUND_SUBMIT_EVENT_TYPE = "REFUND_SUBMIT";
    private static final int CHANNEL_REFUND_NO_MAX_LENGTH = 128;
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int DOWNSTREAM_URL_MAX_LENGTH = 2048;
    private static final int ACCOUNT_NO_MAX_LENGTH = 64;
    private static final int AMQP_DESTINATION_MAX_LENGTH = 255;
    private static final int DOWNSTREAM_RESPONSE_CODE_MAX_LENGTH = 32;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final long DEFAULT_PUBLISHER_CONFIRM_TIMEOUT_MS = 5000L;
    private static final long MAX_PUBLISHER_CONFIRM_TIMEOUT_MS = 60000L;

    private final PaymentMapper paymentMapper;
    private final PaymentEventMapper paymentEventMapper;
    private final RestTemplate restTemplate;
    private final String clearingUrl;
    private final String accountingUrl;
    private final String accountingPaymentSuccessAccountNo;
    private final int maxRetryCount;
    private final String dispatchMode;
    private final String paymentSuccessExchange;
    private final String paymentSuccessRoutingKey;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final long publisherConfirmTimeoutMs;
    private final RefundMapper refundMapper;
    private final RefundChannelSubmitService refundChannelSubmitService;

    @Autowired
    public PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            @Value("${payment.downstream.clearing.payment-success-url:http://127.0.0.1:18120/api/clearing/events/payments/success}")
            String clearingUrl,
            @Value("${payment.downstream.accounting.payment-success-url:http://127.0.0.1:18110/api/accounting/events/payments/success}")
            String accountingUrl,
            @Value("${payment.downstream.accounting.payment-success-account-no:ACT10003}")
            String accountingPaymentSuccessAccountNo,
            @Value("${payment.event-dispatch.mode:http}") String dispatchMode,
            @Value("${payment.event-dispatch.http-timeout-ms:3000}") int httpTimeoutMs,
            @Value("${payment.amqp.payment-success-exchange:payment.trade}") String paymentSuccessExchange,
            @Value("${payment.amqp.payment-success-routing-key:payment.success.v1}") String paymentSuccessRoutingKey,
            @Value("${payment.amqp.publisher-confirm-timeout-ms:5000}") long publisherConfirmTimeoutMs,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            RefundMapper refundMapper,
            RefundChannelSubmitService refundChannelSubmitService) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                DEFAULT_MAX_RETRY_COUNT,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(httpTimeoutMs),
                dispatchMode,
                paymentSuccessExchange,
                paymentSuccessRoutingKey,
                publisherConfirmTimeoutMs,
                rabbitTemplate,
                objectMapper,
                refundMapper,
                refundChannelSubmitService);
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String clearingUrl,
            String accountingUrl,
            String accountingPaymentSuccessAccountNo,
            RestTemplate restTemplate) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                DEFAULT_MAX_RETRY_COUNT,
                restTemplate,
                "http",
                "payment.trade",
                "payment.success.v1",
                5000L,
                null,
                null,
                null,
                null);
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String clearingUrl,
            String accountingUrl,
            String accountingPaymentSuccessAccountNo,
            RestTemplate restTemplate,
            String dispatchMode,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                DEFAULT_MAX_RETRY_COUNT,
                restTemplate,
                dispatchMode,
                "payment.trade",
                "payment.success.v1",
                DEFAULT_PUBLISHER_CONFIRM_TIMEOUT_MS,
                rabbitTemplate,
                objectMapper,
                null,
                null);
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String clearingUrl,
            String accountingUrl,
            String accountingPaymentSuccessAccountNo,
            int maxRetryCount,
            RestTemplate restTemplate) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                maxRetryCount,
                restTemplate,
                "http",
                "payment.trade",
                "payment.success.v1",
                5000L,
                null,
                null,
                null,
                null);
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String accountingPaymentSuccessAccountNo,
            int maxRetryCount,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            String paymentSuccessExchange,
            String paymentSuccessRoutingKey) {
        this(paymentMapper,
                paymentEventMapper,
                null,
                null,
                accountingPaymentSuccessAccountNo,
                maxRetryCount,
                null,
                "amqp",
                paymentSuccessExchange,
                paymentSuccessRoutingKey,
                DEFAULT_PUBLISHER_CONFIRM_TIMEOUT_MS,
                rabbitTemplate,
                objectMapper,
                null,
                null);
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String accountingPaymentSuccessAccountNo,
            int maxRetryCount,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            String paymentSuccessExchange,
            String paymentSuccessRoutingKey,
            long publisherConfirmTimeoutMs) {
        this(paymentMapper,
                paymentEventMapper,
                null,
                null,
                accountingPaymentSuccessAccountNo,
                maxRetryCount,
                null,
                "amqp",
                paymentSuccessExchange,
                paymentSuccessRoutingKey,
                publisherConfirmTimeoutMs,
                rabbitTemplate,
                objectMapper,
                null,
                null);
    }

    PaymentEventDispatchServiceImpl(PaymentEventMapper paymentEventMapper,
                                    RefundMapper refundMapper,
                                    RefundChannelSubmitService refundChannelSubmitService,
                                    int maxRetryCount) {
        this(null,
                paymentEventMapper,
                null,
                null,
                null,
                maxRetryCount,
                null,
                "http",
                null,
                null,
                5000L,
                null,
                null,
                refundMapper,
                refundChannelSubmitService);
    }

    private PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String clearingUrl,
            String accountingUrl,
            String accountingPaymentSuccessAccountNo,
            int maxRetryCount,
            RestTemplate restTemplate,
            String dispatchMode,
            String paymentSuccessExchange,
            String paymentSuccessRoutingKey,
            long publisherConfirmTimeoutMs,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            RefundMapper refundMapper,
            RefundChannelSubmitService refundChannelSubmitService) {
        this.paymentMapper = paymentMapper;
        this.paymentEventMapper = paymentEventMapper;
        this.clearingUrl = clearingUrl;
        this.accountingUrl = accountingUrl;
        this.accountingPaymentSuccessAccountNo = accountingPaymentSuccessAccountNo;
        this.maxRetryCount = maxRetryCount <= 0 ? DEFAULT_MAX_RETRY_COUNT : maxRetryCount;
        this.restTemplate = restTemplate;
        this.dispatchMode = dispatchMode;
        this.paymentSuccessExchange = paymentSuccessExchange;
        this.paymentSuccessRoutingKey = paymentSuccessRoutingKey;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.publisherConfirmTimeoutMs = normalizePublisherConfirmTimeoutMs(publisherConfirmTimeoutMs);
        this.refundMapper = refundMapper;
        this.refundChannelSubmitService = refundChannelSubmitService;
    }

    private long normalizePublisherConfirmTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0) {
            return DEFAULT_PUBLISHER_CONFIRM_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_PUBLISHER_CONFIRM_TIMEOUT_MS);
    }

    @Override
    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
        dispatchPaymentSuccess(eventNo, paymentOrderId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishRefundSubmit(String eventNo, String refundOrderId, String operator) {
        return dispatchRefundSubmit(eventNo, refundOrderId, normalizeOperator(operator));
    }

    @Override
    @Transactional
    public boolean republish(String eventNo, String operator) {
        String normalizedOperator = normalizeOperator(operator);
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNoForUpdate(eventNo);
        if (event == null) {
            return false;
        }
        if (REFUND_SUBMIT_EVENT_TYPE.equals(event.getEventType())) {
            return dispatchRefundSubmit(eventNo, event.getBizNo(), normalizedOperator);
        }
        if (!PAYMENT_SUCCESS_EVENT_TYPE.equals(event.getEventType())) {
            return false;
        }
        return dispatchPaymentSuccess(eventNo, event.getPaymentOrderId());
    }

    private boolean dispatchRefundSubmit(String eventNo, String refundOrderId, String operator) {
        if (refundMapper == null || refundChannelSubmitService == null) {
            return false;
        }
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNoForUpdate(eventNo);
        if (event == null
                || !REFUND_SUBMIT_EVENT_TYPE.equals(event.getEventType())
                || !Objects.equals(event.getBizNo(), refundOrderId)) {
            return false;
        }
        if ("SUCCESS".equals(event.getPublishStatus())) {
            return true;
        }
        boolean resultUnknown = "UNKNOWN".equals(event.getPublishStatus());
        RefundChannelSubmitRequestDTO request =
                refundMapper.findChannelSubmitRequestByRefundOrderId(refundOrderId);
        if (request == null) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        if (!"PROCESSING".equals(request.getRefundStatus())) {
            return paymentEventMapper.markPublishSuccess(eventNo) == 1;
        }
        try {
            validateRefundSubmitRequest(event, request);
        } catch (RuntimeException exception) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            recordRefundSubmitFailure(refundOrderId, operator,
                    "退款渠道下发快照校验失败，修复数据后再重试");
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        if (StringUtils.hasText(request.getChannelRefundNo())) {
            try {
                validateAcceptedRefundTrace(request);
                return paymentEventMapper.markPublishSuccess(eventNo) == 1;
            } catch (RuntimeException exception) {
                if (resultUnknown) {
                    throw refundSubmitReviewRequired();
                }
                markRefundSubmitUnknown(eventNo, refundOrderId, operator);
                return false;
            }
        }
        if (resultUnknown) {
            throw refundSubmitReviewRequired();
        }
        try {
            RefundChannelSubmitResultDTO result = refundChannelSubmitService.submit(request);
            if (result == null || !StringUtils.hasText(result.getChannelRefundNo())) {
                throw new IllegalStateException("退款渠道未返回受理单号");
            }
            String channelRefundNo = normalizeChannelRefundNo(result.getChannelRefundNo());
            if (refundMapper.bindRefundChannelAccepted(
                    refundOrderId, request.getChannelCode().trim(), channelRefundNo) != 1) {
                throw new IllegalStateException("退款渠道受理事实绑定失败");
            }
            refundMapper.insertOperationLog(RefundServiceImpl.buildOperationLog(
                    refundOrderId,
                    "CHANNEL_SUBMIT",
                    "提交退款渠道",
                    "PROCESSING",
                    "PROCESSING",
                    operator,
                    refundSubmitRemark(request, result)));
            if (paymentEventMapper.markPublishSuccess(eventNo) != 1) {
                throw new IllegalStateException("退款渠道事件成功状态更新失败");
            }
            return true;
        } catch (RefundChannelSubmitException exception) {
            if (!exception.isResultUnknown()) {
                recordRefundSubmitFailure(refundOrderId, operator,
                        "退款提交在调用渠道前被配置或治理门禁拒绝，等待修复后重试");
                markFailedOrDeadLetter(eventNo);
                return false;
            }
            markRefundSubmitUnknown(eventNo, refundOrderId, operator);
            return false;
        } catch (RuntimeException exception) {
            markRefundSubmitUnknown(eventNo, refundOrderId, operator);
            return false;
        }
    }

    private BusinessException refundSubmitReviewRequired() {
        return new BusinessException(
                ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED,
                "refund submit result is unknown; verify the provider result before any retry");
    }

    private void recordRefundSubmitFailure(String refundOrderId, String operator, String remark) {
        refundMapper.insertOperationLog(RefundServiceImpl.buildOperationLog(
                refundOrderId,
                "CHANNEL_SUBMIT_FAILED",
                "退款渠道下发失败",
                "PROCESSING",
                "PROCESSING",
                operator,
                remark));
    }

    private void markRefundSubmitUnknown(String eventNo, String refundOrderId, String operator) {
        refundMapper.insertOperationLog(RefundServiceImpl.buildOperationLog(
                refundOrderId,
                "CHANNEL_SUBMIT_UNKNOWN",
                "退款渠道下发结果未知",
                "PROCESSING",
                "PROCESSING",
                operator,
                "退款渠道可能已经受理，已停止直接重试；须按退款单号向渠道查单或人工核查"));
        paymentEventMapper.markPublishUnknown(eventNo);
    }

    private String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("事件派发操作人不能为空");
        }
        for (int index = 0; index < operator.length(); index++) {
            if (Character.isISOControl(operator.charAt(index))) {
                throw new IllegalArgumentException("事件派发操作人不能包含控制字符");
            }
        }
        String normalized = operator.trim();
        if (normalized.length() > OPERATOR_MAX_LENGTH) {
            throw new IllegalArgumentException("事件派发操作人不能超过64个字符");
        }
        return normalized;
    }

    private void validateRefundSubmitRequest(PaymentEventListItemDTO event,
                                             RefundChannelSubmitRequestDTO request) {
        if (!Objects.equals(event.getBizNo(), request.getRefundOrderId())
                || !Objects.equals(event.getPaymentOrderId(), request.getPaymentOrderId())
                || request.getRefundAmount() == null
                || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(request.getChannelCode())) {
            throw new IllegalArgumentException("退款渠道下发快照不完整或不一致");
        }
    }

    private void validateAcceptedRefundTrace(RefundChannelSubmitRequestDTO request) {
        normalizeChannelRefundNo(request.getChannelRefundNo());
        if (!StringUtils.hasText(request.getSubmittedChannelCode())
                || !request.getChannelCode().trim().equalsIgnoreCase(
                        request.getSubmittedChannelCode().trim())) {
            throw new IllegalArgumentException("退款渠道受理事实与原支付渠道不一致");
        }
    }

    private String normalizeChannelRefundNo(String channelRefundNo) {
        if (!StringUtils.hasText(channelRefundNo)) {
            throw new IllegalArgumentException("渠道退款号不能为空");
        }
        for (int index = 0; index < channelRefundNo.length(); index++) {
            if (Character.isISOControl(channelRefundNo.charAt(index))) {
                throw new IllegalArgumentException("渠道退款号不能包含控制字符");
            }
        }
        String normalized = channelRefundNo.trim();
        if (normalized.length() > CHANNEL_REFUND_NO_MAX_LENGTH) {
            throw new IllegalArgumentException("渠道退款号长度不能超过128字符");
        }
        return normalized;
    }

    private String refundSubmitRemark(RefundChannelSubmitRequestDTO request,
                                      RefundChannelSubmitResultDTO result) {
        String remark = "退款渠道已受理 channel="
                + safeText(request.getChannelCode())
                + ", channelRefundNo="
                + safeText(result.getChannelRefundNo());
        return remark.length() <= 255 ? remark : remark.substring(0, 255);
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private boolean dispatchPaymentSuccess(String eventNo, String paymentOrderId) {
        PaymentDetailDTO detail = paymentMapper.findDetail(paymentOrderId);
        if (detail == null) {
            paymentEventMapper.markPublishFailed(eventNo);
            return false;
        }
        String workerName = paymentMapper.findWorkerNameByOrderNo(detail.getOrderNo());
        BigDecimal amount = parseAmount(detail.getAmount());
        try {
            validateDispatchMode();
            if (usesAmqp()) {
                publishToAmqp(eventNo, detail, workerName, amount);
                paymentEventMapper.markPublishSuccess(eventNo);
                return true;
            }
            validateHttpDispatchConfiguration();
            postForSuccess(clearingUrl, buildClearingPayload(detail, workerName, amount));
            postForSuccess(accountingUrl, buildAccountingPayload(detail, amount));
            paymentEventMapper.markPublishSuccess(eventNo);
            return true;
        } catch (RuntimeException exception) {
            markFailedOrDeadLetter(eventNo);
            return false;
        }
    }

    private void validateDispatchMode() {
        String normalizedMode = StringUtils.hasText(dispatchMode) ? dispatchMode.trim() : "";
        if (!"http".equalsIgnoreCase(normalizedMode) && !"amqp".equalsIgnoreCase(normalizedMode)) {
            throw new IllegalStateException("支付成功事件派发模式配置非法");
        }
    }

    private boolean usesAmqp() {
        return "amqp".equalsIgnoreCase(dispatchMode == null ? "" : dispatchMode.trim());
    }

    private void publishToAmqp(String eventNo, PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        if (rabbitTemplate == null || objectMapper == null
                || !isValidConfiguredText(paymentSuccessExchange, AMQP_DESTINATION_MAX_LENGTH)
                || !isValidConfiguredText(paymentSuccessRoutingKey, AMQP_DESTINATION_MAX_LENGTH)
                || !isValidConfiguredText(accountingPaymentSuccessAccountNo, ACCOUNT_NO_MAX_LENGTH)) {
            throw new IllegalStateException("AMQP 支付成功事件配置不完整");
        }
        try {
            final String payload = objectMapper.writeValueAsString(buildAmqpPayload(detail, workerName, amount));
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(
                        paymentSuccessExchange.trim(),
                        paymentSuccessRoutingKey.trim(),
                        payload,
                        buildMessagePostProcessor(eventNo, detail.getPaymentOrderId()));
                operations.waitForConfirmsOrDie(publisherConfirmTimeoutMs);
                return null;
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付成功事件序列化失败", exception);
        }
    }

    private Map<String, Object> buildAmqpPayload(PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("businessChainId", businessChainId(detail.getPaymentOrderId()));
        payload.put("accountNo", accountingPaymentSuccessAccountNo);
        payload.put("paymentOrderId", detail.getPaymentOrderId());
        payload.put("orderNo", detail.getOrderNo());
        payload.put("batchDate", LocalDate.now().toString());
        payload.put("customerName", detail.getCustomerName());
        payload.put("merchantName", "家政平台");
        payload.put("workerName", StringUtils.hasText(workerName) ? workerName : "待分配服务者");
        payload.put("amount", amount);
        return payload;
    }

    private MessagePostProcessor buildMessagePostProcessor(final String eventNo, final String paymentOrderId) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setMessageId(eventNo);
            properties.setCorrelationId(eventNo);
            properties.setHeader("eventType", PAYMENT_SUCCESS_EVENT_TYPE);
            properties.setHeader("paymentOrderId", paymentOrderId);
            return message;
        };
    }

    /**
     * 超过最大重试次数后直接进入死信，避免任务中心无限重试同一支付事件。
     */
    private void markFailedOrDeadLetter(String eventNo) {
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNo(eventNo);
        int currentRetryCount = event == null || event.getRetryCount() == null ? 0 : event.getRetryCount();
        if (currentRetryCount + 1 >= maxRetryCount) {
            paymentEventMapper.markPublishDeadLetter(eventNo);
            return;
        }
        paymentEventMapper.markPublishFailed(eventNo);
    }

    private void validateHttpDispatchConfiguration() {
        if (restTemplate == null
                || !isValidConfiguredText(clearingUrl, DOWNSTREAM_URL_MAX_LENGTH)
                || !isValidConfiguredText(accountingUrl, DOWNSTREAM_URL_MAX_LENGTH)
                || !isValidConfiguredText(accountingPaymentSuccessAccountNo, ACCOUNT_NO_MAX_LENGTH)) {
            throw new IllegalStateException("HTTP 支付成功事件配置不完整");
        }
    }

    private boolean isValidConfiguredText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private void postForSuccess(String url, Object payload) {
        ResponseEntity<DownstreamApiResponse> response = restTemplate.postForEntity(
                url.trim(), payload, DownstreamApiResponse.class);
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null || response.getBody().getData() == null
                || !isSuccessfulResponseCode(response.getBody().getCode())) {
            throw new IllegalStateException("下游系统未明确返回业务成功");
        }
    }

    private boolean isSuccessfulResponseCode(String code) {
        if (!isValidConfiguredText(code, DOWNSTREAM_RESPONSE_CODE_MAX_LENGTH)) {
            return false;
        }
        return "0".equals(code.trim());
    }

    private PaymentSuccessClearingPayload buildClearingPayload(PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        return new PaymentSuccessClearingPayload(
                businessChainId(detail.getPaymentOrderId()),
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                LocalDate.now().toString(),
                detail.getCustomerName(),
                "家政平台",
                StringUtils.hasText(workerName) ? workerName : "待分配服务者",
                amount);
    }

    private PaymentSuccessAccountingPayload buildAccountingPayload(PaymentDetailDTO detail, BigDecimal amount) {
        return new PaymentSuccessAccountingPayload(
                businessChainId(detail.getPaymentOrderId()),
                accountingPaymentSuccessAccountNo,
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                detail.getCustomerName(),
                amount);
    }

    private BigDecimal parseAmount(String amountText) {
        String normalized = amountText == null ? "0" : amountText.replace("¥", "").replace(",", "").trim();
        return new BigDecimal(normalized);
    }

    /**
     * 对齐清分与账务系统统一响应结构，仅用于确认业务接收结果。
     */
    public static class DownstreamApiResponse {

        private String code;
        private String message;
        private Object data;
        private String requestId;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    /**
     * 一期以支付单号作为稳定根键，后续可替换为订单域下发的全局链路号。
     */
    private String businessChainId(String paymentOrderId) {
        return "CHAIN-" + paymentOrderId;
    }

    /**
     * 清分事件载荷。
     */
    private static class PaymentSuccessClearingPayload {

        private final String businessChainId;
        private final String paymentOrderId;
        private final String orderNo;
        private final String batchDate;
        private final String customerName;
        private final String merchantName;
        private final String workerName;
        private final BigDecimal amount;

        PaymentSuccessClearingPayload(String businessChainId,
                                      String paymentOrderId,
                                      String orderNo,
                                      String batchDate,
                                      String customerName,
                                      String merchantName,
                                      String workerName,
                                      BigDecimal amount) {
            this.businessChainId = businessChainId;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.batchDate = batchDate;
            this.customerName = customerName;
            this.merchantName = merchantName;
            this.workerName = workerName;
            this.amount = amount;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getBatchDate() {
            return batchDate;
        }

        public String getCustomerName() {
            return customerName;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public String getWorkerName() {
            return workerName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 账务事件载荷。
     */
    private static class PaymentSuccessAccountingPayload {

        private final String businessChainId;
        private final String accountNo;
        private final String paymentOrderId;
        private final String orderNo;
        private final String customerName;
        private final BigDecimal amount;

        PaymentSuccessAccountingPayload(String businessChainId,
                                        String accountNo,
                                        String paymentOrderId,
                                        String orderNo,
                                        String customerName,
                                        BigDecimal amount) {
            this.businessChainId = businessChainId;
            this.accountNo = accountNo;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.customerName = customerName;
            this.amount = amount;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getCustomerName() {
            return customerName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
