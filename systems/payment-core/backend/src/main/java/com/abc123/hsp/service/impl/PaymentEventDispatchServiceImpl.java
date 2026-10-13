package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.common.PaymentEventAmqpSignature;
import com.abc123.hsp.common.PaymentEventHttpSignature;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentEventDispatchServiceImpl.class);
    private static final String PAYMENT_SUCCESS_EVENT_TYPE = "PAYMENT_SUCCESS";
    private static final String PAYMENT_SUCCESS_EVENT_SCHEMA_VERSION = "1";
    private static final String REFUND_SUBMIT_EVENT_TYPE = "REFUND_SUBMIT";
    private static final String REFUND_SUBMIT_DOWNSTREAM_SYSTEM = "gateway-access";
    private static final String REFUND_SUBMIT_EVENT_TOPIC = "payment.refund.submitted.v1";
    private static final String HTTP_STAGE_PENDING = "PENDING";
    private static final String HTTP_STAGE_CLEARING_ACCEPTED = "CLEARING_ACCEPTED";
    private static final String HTTP_STAGE_COMPLETED = "COMPLETED";
    private static final int CHANNEL_REFUND_NO_MAX_LENGTH = 128;
    private static final int CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int CHANNEL_TRANSACTION_NO_MAX_LENGTH = 128;
    private static final int EVENT_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int CUSTOMER_NAME_MAX_LENGTH = 128;
    private static final int WORKER_NAME_MAX_LENGTH = 128;
    private static final int REFUND_ORDER_ID_MAX_LENGTH = 64;
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int DOWNSTREAM_URL_MAX_LENGTH = 2048;
    private static final int ACCOUNT_NO_MAX_LENGTH = 64;
    private static final int AMQP_DESTINATION_MAX_LENGTH = 255;
    private static final int DOWNSTREAM_RESPONSE_CODE_MAX_LENGTH = 32;
    private static final int MONEY_MAX_SCALE = 2;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final long DEFAULT_PUBLISHER_CONFIRM_TIMEOUT_MS = 5000L;
    private static final long MAX_PUBLISHER_CONFIRM_TIMEOUT_MS = 60000L;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PAYMENT_FACT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Value("${payment.downstream.reconciliation.fact-url:}")
    private String reconciliationFactUrl;

    @Value("${payment.downstream.refund.projection-url:}")
    private String refundProjectionUrl;

    @Value("${HSP_PAYMENT_REFUND_PROJECTION_HTTP_SECRET:}")
    private String refundProjectionSecret;

    @Value("${HSP_PAYMENT_SUCCESS_HTTP_SECRET:}")
    private String paymentSuccessHttpSecret;

    @Value("${HSP_PAYMENT_RECONCILIATION_FACT_SECRET:}")
    private String reconciliationFactSecret;

    @Value("${HSP_PAYMENT_SUCCESS_AMQP_SECRET:}")
    private String paymentSuccessAmqpSecret;

    @Autowired
    public PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            @Value("${payment.downstream.clearing.payment-success-url:http://localhost:18120/api/clearing/events/payments/success}")
            String clearingUrl,
            @Value("${payment.downstream.accounting.payment-success-url:http://localhost:18110/api/accounting/events/payments/success}")
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
        dispatchPaymentSuccess(
                normalizeRequiredIdentifier(eventNo, "事件号", EVENT_NO_MAX_LENGTH),
                normalizeRequiredIdentifier(paymentOrderId, "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishRefundSubmit(String eventNo, String refundAttemptNo, String operator) {
        return dispatchRefundSubmit(
                normalizeRequiredIdentifier(eventNo, "事件号", EVENT_NO_MAX_LENGTH),
                normalizeRequiredIdentifier(refundAttemptNo, "退款尝试号", REFUND_ORDER_ID_MAX_LENGTH),
                normalizeOperator(operator));
    }

    @Override
    @Transactional
    public boolean republish(String eventNo, String operator) {
        String normalizedEventNo = normalizeRequiredIdentifier(eventNo, "事件号", EVENT_NO_MAX_LENGTH);
        String normalizedOperator = normalizeOperator(operator);
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNoForUpdate(normalizedEventNo);
        if (event == null) {
            return false;
        }
        if (REFUND_SUBMIT_EVENT_TYPE.equals(event.getEventType())) {
            if (!"UNKNOWN".equals(event.getPublishStatus())
                    && !isRepublishableStatus(event.getPublishStatus())) {
                return false;
            }
            return dispatchRefundSubmit(normalizedEventNo, event.getBizNo(), normalizedOperator);
        }
        if (!PAYMENT_SUCCESS_EVENT_TYPE.equals(event.getEventType())
                || !isRepublishableStatus(event.getPublishStatus())) {
            return false;
        }
        return dispatchPaymentSuccess(normalizedEventNo, event.getPaymentOrderId());
    }

    private boolean isRepublishableStatus(String publishStatus) {
        return "PENDING".equals(publishStatus)
                || "FAILED".equals(publishStatus)
                || "DEAD_LETTER".equals(publishStatus);
    }

    private boolean dispatchRefundSubmit(String eventNo, String refundAttemptNo, String operator) {
        if (refundMapper == null || refundChannelSubmitService == null) {
            return false;
        }
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNoForUpdate(eventNo);
        if (event == null
                || !REFUND_SUBMIT_EVENT_TYPE.equals(event.getEventType())
                || !Objects.equals(event.getBizNo(), refundAttemptNo)
                || !REFUND_SUBMIT_DOWNSTREAM_SYSTEM.equals(event.getDownstreamSystem())
                || !REFUND_SUBMIT_EVENT_TOPIC.equals(event.getEventTopic())) {
            return false;
        }
        if ("SUCCESS".equals(event.getPublishStatus())) {
            return refundMapper.isMatchingSuccessfulRefundSubmitEvent(eventNo, refundAttemptNo);
        }
        boolean resultUnknown = "UNKNOWN".equals(event.getPublishStatus());
        if (!resultUnknown && !isRepublishableStatus(event.getPublishStatus())) {
            return false;
        }
        RefundChannelSubmitRequestDTO request =
                refundMapper.findChannelSubmitRequestByAttemptNo(refundAttemptNo);
        if (request == null) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        String refundOrderId = request.getRefundOrderId();
        if (!"PROCESSING".equals(request.getRefundStatus())) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        try {
            validateRefundSubmitRequest(event, request);
        } catch (RuntimeException exception) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            if (request.getChannelRefundNo() != null
                    || (!"PENDING".equals(request.getAttemptStatus())
                    && !"FAILED".equals(request.getAttemptStatus()))) {
                markFailedOrDeadLetter(eventNo);
                return false;
            }
            recordRefundSubmitFailure(refundOrderId, refundAttemptNo, operator,
                        "退款渠道下发快照校验失败，修复数据后再重试");
            if (refundMapper.markRefundAttemptFailed(refundAttemptNo) != 1) {
                throw new IllegalStateException("退款尝试失败状态更新未实际生效");
            }
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        if (request.getChannelRefundNo() != null) {
            try {
                validateAcceptedRefundTrace(request);
            } catch (RuntimeException exception) {
                if (resultUnknown) {
                    throw refundSubmitReviewRequired();
                }
                markFailedOrDeadLetter(eventNo);
                return false;
            }
            return paymentEventMapper.markPublishSuccess(eventNo) == 1;
        }
        if (request.getSubmittedChannelCode() != null
                || request.getOrderChannelRefundNo() != null
                || Boolean.TRUE.equals(request.getChannelSubmitRecorded())) {
            if (resultUnknown) {
                throw refundSubmitReviewRequired();
            }
            markFailedOrDeadLetter(eventNo);
            return false;
        }
        if (resultUnknown) {
            throw refundSubmitReviewRequired();
        }
        RefundChannelSubmitResultDTO result;
        String channelRefundNo;
        try {
            result = refundChannelSubmitService.submit(request);
            validateRefundSubmitResult(result);
            channelRefundNo = normalizeChannelRefundNo(result.getChannelRefundNo());
        } catch (RefundChannelSubmitException exception) {
            if (!exception.isResultUnknown()) {
                recordRefundSubmitFailure(refundOrderId, refundAttemptNo, operator,
                        "退款提交在调用渠道前被配置或治理门禁拒绝，等待修复后重试");
                if (refundMapper.markRefundAttemptFailed(refundAttemptNo) != 1) {
                    throw new IllegalStateException("退款尝试失败状态更新未实际生效");
                }
                markFailedOrDeadLetter(eventNo);
                return false;
            }
            markRefundSubmitUnknown(
                    eventNo, refundOrderId, refundAttemptNo, request.getChannelCode(), operator);
            return false;
        } catch (RuntimeException exception) {
            markRefundSubmitUnknown(
                    eventNo, refundOrderId, refundAttemptNo, request.getChannelCode(), operator);
            return false;
        }
        if (refundMapper.bindRefundAttemptAccepted(refundAttemptNo, channelRefundNo) != 1
                || refundMapper.bindRefundChannelAccepted(
                refundOrderId, refundAttemptNo, request.getChannelCode(), channelRefundNo) != 1) {
            throw new IllegalStateException("退款渠道受理事实绑定失败");
        }
        insertRefundOperationLog(RefundServiceImpl.buildOperationLog(
                refundOrderId,
                refundAttemptNo,
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
    }

    private void validateRefundSubmitResult(RefundChannelSubmitResultDTO result) {
        if (result == null
                || !StringUtils.hasText(result.getChannelRefundNo())
                || !"PROCESSING".equalsIgnoreCase(result.getStatus())
                || !"warn".equalsIgnoreCase(result.getStatusType())) {
            throw new IllegalArgumentException("退款渠道提交结果不是可确认的处理中受理事实");
        }
    }

    private BusinessException refundSubmitReviewRequired() {
        return new BusinessException(
                ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED,
                "refund submit result is unknown; verify the provider result before any retry");
    }

    private void recordRefundSubmitFailure(String refundOrderId,
                                           String refundAttemptNo,
                                           String operator,
                                           String remark) {
        insertRefundOperationLog(RefundServiceImpl.buildOperationLog(
                refundOrderId,
                refundAttemptNo,
                "CHANNEL_SUBMIT_FAILED",
                "退款渠道下发失败",
                "PROCESSING",
                "PROCESSING",
                operator,
                remark));
    }

    private void markRefundSubmitUnknown(String eventNo,
                                         String refundOrderId,
                                         String refundAttemptNo,
                                         String channelCode,
                                         String operator) {
        if (refundMapper.markRefundAttemptUnknown(refundAttemptNo) != 1) {
            throw new IllegalStateException("退款尝试未知状态更新未实际生效");
        }
        if (refundMapper.recordUnknownRefundSubmitChannel(
                eventNo, refundOrderId, refundAttemptNo, channelCode) != 1) {
            throw new IllegalStateException("退款未知渠道事实写入未实际生效");
        }
        insertRefundOperationLog(RefundServiceImpl.buildOperationLog(
                refundOrderId,
                refundAttemptNo,
                "CHANNEL_SUBMIT_UNKNOWN",
                "退款渠道下发结果未知",
                "PROCESSING",
                "PROCESSING",
                operator,
                "退款渠道可能已经受理，已停止直接重试；须按退款单号向渠道查单或人工核查"));
        if (paymentEventMapper.markPublishUnknown(eventNo) != 1) {
            throw new IllegalStateException("退款渠道事件未知状态更新失败");
        }
    }

    private void insertRefundOperationLog(RefundOperationLogEntity operationLog) {
        if (refundMapper.insertOperationLog(operationLog) != 1) {
            throw new IllegalStateException("退款渠道操作日志新增未实际生效");
        }
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

    private String normalizeRequiredIdentifier(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "字符");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "字符");
        }
        return normalized;
    }

    private void validateRefundSubmitRequest(PaymentEventListItemDTO event,
                                             RefundChannelSubmitRequestDTO request) {
        boolean acceptedTraceExists = StringUtils.hasText(request.getChannelRefundNo());
        boolean attemptStatusMatchesTrace = acceptedTraceExists
                ? "ACCEPTED".equals(request.getAttemptStatus())
                : "PENDING".equals(request.getAttemptStatus())
                || "FAILED".equals(request.getAttemptStatus());
        if (!Objects.equals(event.getBizNo(), request.getRefundAttemptNo())
                || !Objects.equals(event.getPaymentOrderId(), request.getPaymentOrderId())
                || request.getRefundAmount() == null
                || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(request.getChannelCode())
                || !request.getChannelCode().equals(request.getChannelCode().trim())
                || !attemptStatusMatchesTrace) {
            throw new IllegalArgumentException("退款渠道下发快照不完整或不一致");
        }
    }

    private void validateAcceptedRefundTrace(RefundChannelSubmitRequestDTO request) {
        String channelRefundNo = normalizeChannelRefundNo(request.getChannelRefundNo());
        if (!channelRefundNo.equals(request.getChannelRefundNo())
                || !Objects.equals(request.getChannelCode(), request.getSubmittedChannelCode())
                || !Objects.equals(request.getChannelRefundNo(), request.getOrderChannelRefundNo())
                || !Boolean.TRUE.equals(request.getChannelSubmitRecorded())) {
            throw new IllegalArgumentException("退款渠道受理事实不一致");
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
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNoForUpdate(eventNo);
        if (event == null
                || !PAYMENT_SUCCESS_EVENT_TYPE.equals(event.getEventType())
                || !Objects.equals(event.getPaymentOrderId(), paymentOrderId)) {
            return false;
        }
        if ("SUCCESS".equals(event.getPublishStatus())) {
            return true;
        }
        if (!isRepublishableStatus(event.getPublishStatus())) {
            return false;
        }
        try {
            validateDispatchMode();
            boolean amqp = usesAmqp();
            String httpStage = amqp ? null : requirePaymentSuccessHttpStage(event);
            if (!HTTP_STAGE_COMPLETED.equals(httpStage)) {
                if (StringUtils.hasText(refundProjectionUrl) && !StringUtils.hasText(refundProjectionSecret)) {
                    throw new IllegalStateException("退款中心支付成功投影签名密钥未配置");
                }
                PaymentDetailDTO detail = paymentMapper.findDetail(paymentOrderId);
                if (detail == null) {
                    throw new IllegalArgumentException("支付成功事件对应支付详情不存在");
                }
                validatePaymentSuccessDetail(paymentOrderId, event.getBizNo(), detail);
                BigDecimal amount = parsePaymentSuccessAmount(detail.getAmount());
                String orderNo = detail.getOrderNo().trim();
                String customerName = detail.getCustomerName().trim();
                String workerName = normalizeWorkerName(paymentMapper.findWorkerNameByOrderNo(orderNo));
                if (amqp) {
                    publishToAmqp(eventNo, detail.getPaymentOrderId(), orderNo, customerName,
                            workerName, detail.getPayScene(), amount);
                    postRefundProjection(detail, orderNo, customerName, amount);
                    postReconciliationFact(buildReconciliationFactPayload(
                            detail.getPaymentOrderId(), orderNo, detail.getPayScene(), amount));
                } else {
                    validateHttpDispatchConfiguration();
                    dispatchPaymentSuccessViaHttp(
                            event, httpStage, detail, orderNo, customerName, workerName, amount);
                }
            }
            if (paymentEventMapper.markPublishSuccess(eventNo) != 1) {
                throw new IllegalStateException("支付成功事件成功状态更新失败");
            }
            return true;
        } catch (RuntimeException exception) {
            String stage = event.getPaymentSuccessHttpStage();
            if (!HTTP_STAGE_PENDING.equals(stage) && !HTTP_STAGE_CLEARING_ACCEPTED.equals(stage)
                    && !HTTP_STAGE_COMPLETED.equals(stage)) {
                stage = "INVALID";
            }
            LOGGER.warn("支付成功事件派发失败 eventNo={}, stage={}, exceptionType={}",
                    eventNo, stage, exception.getClass().getSimpleName());
            markFailedOrDeadLetter(eventNo);
            return false;
        }
    }

    private void dispatchPaymentSuccessViaHttp(PaymentEventListItemDTO event,
                                               String stage,
                                               PaymentDetailDTO detail,
                                               String orderNo,
                                               String customerName,
                                               String workerName,
                                               BigDecimal amount) {
        if (HTTP_STAGE_PENDING.equals(stage)) {
            postPaymentEventForSuccess(clearingUrl, buildClearingPayload(
                    event.getEventNo(), detail.getPaymentOrderId(), orderNo,
                    customerName, workerName, detail.getPayScene(), amount), detail.getPaymentOrderId());
            if (paymentEventMapper.markPaymentSuccessClearingAccepted(event.getEventNo()) != 1) {
                throw new IllegalStateException("支付成功事件清分受理阶段更新失败");
            }
        } else if (!HTTP_STAGE_CLEARING_ACCEPTED.equals(stage)) {
            throw new IllegalArgumentException("支付成功事件 HTTP 投递阶段非法");
        }
        postPaymentEventForSuccess(accountingUrl, buildAccountingPayload(
                event.getEventNo(), detail.getPaymentOrderId(), orderNo, customerName,
                detail.getPayScene(), amount), detail.getPaymentOrderId());
        postRefundProjection(detail, orderNo, customerName, amount);
        postReconciliationFact(buildReconciliationFactPayload(
                detail.getPaymentOrderId(), orderNo, detail.getPayScene(), amount));
        if (paymentEventMapper.markPaymentSuccessHttpCompleted(event.getEventNo()) != 1) {
            throw new IllegalStateException("支付成功事件 HTTP 完成阶段更新失败");
        }
    }

    private String requirePaymentSuccessHttpStage(PaymentEventListItemDTO event) {
        String stage = StringUtils.hasText(event.getPaymentSuccessHttpStage())
                ? event.getPaymentSuccessHttpStage().trim()
                : "";
        if (!HTTP_STAGE_PENDING.equals(stage)
                && !HTTP_STAGE_CLEARING_ACCEPTED.equals(stage)
                && !HTTP_STAGE_COMPLETED.equals(stage)) {
            throw new IllegalArgumentException("支付成功事件 HTTP 投递阶段非法");
        }
        return stage;
    }

    private void validatePaymentSuccessDetail(String paymentOrderId,
                                              String eventBizNo,
                                              PaymentDetailDTO detail) {
        if (!Objects.equals(paymentOrderId, detail.getPaymentOrderId())
                || !isSamePaymentOrderNo(eventBizNo, detail.getOrderNo())
                || !"SUCCESS".equals(detail.getStatus())
                || !"success".equalsIgnoreCase(detail.getStatusType())
                || !Boolean.TRUE.equals(detail.getPrepayAmountBindingValid())
                || !("STANDARD_ORDER".equals(detail.getSourceKind())
                        || "VIRTUAL".equals(detail.getSourceKind()))
                || !isValidConfiguredText(detail.getOrderNo(), ORDER_NO_MAX_LENGTH)
                || !isValidConfiguredText(detail.getCustomerName(), CUSTOMER_NAME_MAX_LENGTH)
                || !isValidConfiguredText(detail.getChannel(), CHANNEL_CODE_MAX_LENGTH)
                || !isValidConfiguredText(
                        detail.getChannelTransactionNo(), CHANNEL_TRANSACTION_NO_MAX_LENGTH)
                || !"成功".equals(detail.getLatestAttemptStatus())
                || !"success".equalsIgnoreCase(detail.getLatestAttemptStatusType())
                || !StringUtils.hasText(detail.getLatestAttemptCompletedAt())
                || !StringUtils.hasText(detail.getSuccessAt())
                || !StringUtils.hasText(detail.getCreatedAt())
                || !Boolean.TRUE.equals(detail.getPaymentFactTimelineValid())
                || !isPaymentFactTimelineValid(
                        detail.getCreatedAt(), detail.getLatestAttemptCompletedAt(), detail.getSuccessAt())) {
            throw new IllegalArgumentException("支付成功事件与支付事实不一致");
        }
        BigDecimal orderAmount = paymentMapper.findOrderAmount(detail.getOrderNo());
        BigDecimal paidAmount = paymentMapper.findPaidAmount(detail.getOrderNo());
        if ("STANDARD_ORDER".equals(detail.getSourceKind())
                ? orderAmount == null || paidAmount == null
                : orderAmount != null || paidAmount != null) {
            throw new IllegalArgumentException("支付成功事件对应订单来源已变化");
        }
    }

    private boolean isSamePaymentOrderNo(String eventBizNo, String detailOrderNo) {
        return StringUtils.hasText(eventBizNo)
                && StringUtils.hasText(detailOrderNo)
                && eventBizNo.equals(detailOrderNo)
                && eventBizNo.equals(eventBizNo.trim());
    }

    private boolean isPaymentFactTimelineValid(String createdAt, String attemptCompletedAt, String successAt) {
        try {
            LocalDateTime created = LocalDateTime.parse(createdAt.trim(), PAYMENT_FACT_TIME_FORMATTER);
            LocalDateTime completedAt = LocalDateTime.parse(
                    attemptCompletedAt.trim(), PAYMENT_FACT_TIME_FORMATTER);
            LocalDateTime succeededAt = LocalDateTime.parse(
                    successAt.trim(), PAYMENT_FACT_TIME_FORMATTER);
            return !created.isAfter(completedAt) && !completedAt.isAfter(succeededAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private String normalizeWorkerName(String workerName) {
        if (!StringUtils.hasText(workerName)) {
            return "待分配服务者";
        }
        if (!isValidConfiguredText(workerName, WORKER_NAME_MAX_LENGTH)) {
            throw new IllegalArgumentException("支付成功事件服务者名称非法");
        }
        return workerName.trim();
    }

    private BigDecimal parsePaymentSuccessAmount(String amountText) {
        if (!StringUtils.hasText(amountText)) {
            throw new IllegalArgumentException("支付成功事件金额不能为空");
        }
        BigDecimal amount = new BigDecimal(amountText
                .replace("¥", "")
                .replace(",", "")
                .trim());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付成功事件金额必须大于 0");
        }
        if (amount.scale() > MONEY_MAX_SCALE
                || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new IllegalArgumentException("支付成功事件金额最多支持 16 位整数和 2 位小数");
        }
        return amount;
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

    private void publishToAmqp(String eventNo,
                               String paymentOrderId,
                               String orderNo,
                               String customerName,
                               String workerName,
                               String payScene,
                               BigDecimal amount) {
        if (rabbitTemplate == null || objectMapper == null
                || !StringUtils.hasText(paymentSuccessAmqpSecret)
                || !isValidConfiguredText(paymentSuccessExchange, AMQP_DESTINATION_MAX_LENGTH)
                || !isValidConfiguredText(paymentSuccessRoutingKey, AMQP_DESTINATION_MAX_LENGTH)
                || !isValidConfiguredText(accountingPaymentSuccessAccountNo, ACCOUNT_NO_MAX_LENGTH)) {
            throw new IllegalStateException("AMQP 支付成功事件配置不完整");
        }
        try {
            final String payload = objectMapper.writeValueAsString(
                    buildAmqpPayload(eventNo, paymentOrderId, orderNo, customerName, workerName,
                            payScene, amount));
            CorrelationData correlationData = new CorrelationData();
            rabbitTemplate.convertAndSend(paymentSuccessExchange.trim(), paymentSuccessRoutingKey.trim(),
                    payload, buildMessagePostProcessor(eventNo, paymentOrderId), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(publisherConfirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("AMQP 支付成功事件未被目标队列确认");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付成功事件序列化失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AMQP 支付成功事件确认中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("AMQP 支付成功事件确认失败", exception);
        }
    }

    private Map<String, Object> buildAmqpPayload(String eventNo,
                                                 String paymentOrderId,
                                                 String orderNo,
                                                 String customerName,
                                                 String workerName,
                                                 String payScene,
                                                 BigDecimal amount) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("eventNo", eventNo);
        payload.put("eventType", PAYMENT_SUCCESS_EVENT_TYPE);
        payload.put("schemaVersion", PAYMENT_SUCCESS_EVENT_SCHEMA_VERSION);
        payload.put("businessChainId", businessChainId(paymentOrderId));
        payload.put("accountNo", accountingPaymentSuccessAccountNo);
        payload.put("paymentOrderId", paymentOrderId);
        payload.put("orderNo", orderNo);
        payload.put("batchDate", currentBusinessDate());
        payload.put("customerName", customerName);
        payload.put("merchantName", "家政平台");
        payload.put("workerName", workerName);
        payload.put("payScene", payScene);
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
            properties.setHeader("schemaVersion", PAYMENT_SUCCESS_EVENT_SCHEMA_VERSION);
            properties.setHeader("paymentOrderId", paymentOrderId);
            properties.setHeader(PaymentEventAmqpSignature.HEADER_NAME,
                    PaymentEventAmqpSignature.sign(message.getBody(), paymentSuccessAmqpSecret));
            return message;
        };
    }

    /**
     * 超过最大重试次数后直接进入死信，避免任务中心无限重试同一支付事件。
     */
    private void markFailedOrDeadLetter(String eventNo) {
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNo(eventNo);
        if (event != null && "DEAD_LETTER".equals(event.getPublishStatus())) {
            if (paymentEventMapper.markPublishDeadLetter(eventNo) != 1) {
                throw new IllegalStateException("支付事件死信状态更新失败");
            }
            return;
        }
        int currentRetryCount = event == null || event.getRetryCount() == null ? 0 : event.getRetryCount();
        if (currentRetryCount + 1 >= maxRetryCount) {
            if (paymentEventMapper.markPublishDeadLetter(eventNo) != 1) {
                throw new IllegalStateException("支付事件死信状态更新失败");
            }
            return;
        }
        if (paymentEventMapper.markPublishFailed(eventNo) != 1) {
            throw new IllegalStateException("支付事件失败状态更新失败");
        }
    }

    private void validateHttpDispatchConfiguration() {
        if (restTemplate == null
                || !StringUtils.hasText(paymentSuccessHttpSecret)
                || !isValidConfiguredText(clearingUrl, DOWNSTREAM_URL_MAX_LENGTH)
                || !isValidConfiguredText(accountingUrl, DOWNSTREAM_URL_MAX_LENGTH)
                || !isValidConfiguredText(accountingPaymentSuccessAccountNo, ACCOUNT_NO_MAX_LENGTH)) {
            throw new IllegalStateException("HTTP 支付成功事件配置不完整");
        }
    }

    private boolean isValidConfiguredText(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() > maxLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private void postPaymentEventForSuccess(String url, Object payload, String paymentOrderId) {
        HttpEntity<String> request;
        try {
            String body = (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            headers.set(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp);
            headers.set(PaymentEventHttpSignature.HEADER_NAME,
                    PaymentEventHttpSignature.sign(body, timestamp, paymentSuccessHttpSecret));
            request = new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付成功事件签名序列化失败", exception);
        }
        Object data = postForSuccess(url, request).getData();
        if (!(data instanceof Map)) {
            LOGGER.warn("支付成功事件回执拒绝 paymentOrderId={}, target={}, reason=STRUCTURE",
                    paymentOrderId, url.equals(clearingUrl) ? "CLEARING" : "ACCOUNTING");
            throw new IllegalStateException("下游支付事件回执缺少归属事实");
        }
        Map<?, ?> event = (Map<?, ?>) data;
        if (!PAYMENT_SUCCESS_EVENT_TYPE.equals(event.get("eventType"))
                || !paymentOrderId.equals(event.get("bizNo"))
                || !businessChainId(paymentOrderId).equals(event.get("businessChainId"))
                || !(event.get("eventNo") instanceof String)
                || !StringUtils.hasText((String) event.get("eventNo"))
                || !"已消费".equals(event.get("eventStatus"))) {
            LOGGER.warn("支付成功事件回执拒绝 paymentOrderId={}, target={}, reason=IDENTITY",
                    paymentOrderId, url.equals(clearingUrl) ? "CLEARING" : "ACCOUNTING");
            throw new IllegalStateException("下游支付事件回执与原支付单不匹配");
        }
    }

    private DownstreamApiResponse postForSuccess(String url, Object payload) {
        ResponseEntity<DownstreamApiResponse> response = restTemplate.postForEntity(
                url.trim(), payload, DownstreamApiResponse.class);
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null || response.getBody().getData() == null
                || !isSuccessfulResponseCode(response.getBody().getCode())) {
            LOGGER.warn("支付成功事件下游响应拒绝 target={}, reason=HTTP_CODE_OR_DATA",
                    url.equals(clearingUrl) ? "CLEARING" : url.equals(accountingUrl) ? "ACCOUNTING" : "RECONCILIATION");
            throw new IllegalStateException("下游系统未明确返回业务成功");
        }
        return response.getBody();
    }

    private void postRefundProjection(PaymentDetailDTO detail, String orderNo,
                                      String customerName, BigDecimal amount) {
        if (!StringUtils.hasText(refundProjectionUrl)) {
            return;
        }
        if (!StringUtils.hasText(refundProjectionSecret)) {
            throw new IllegalStateException("退款中心支付成功投影签名密钥未配置");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentOrderId", detail.getPaymentOrderId());
        payload.put("orderNo", orderNo);
        payload.put("customerName", customerName);
        payload.put("paidAmount", amount);
        payload.put("channelCode", detail.getChannel());
        payload.put("payScene", detail.getPayScene());
        try {
            String body = (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(payload);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            headers.set("X-HSP-Payment-Projection-Timestamp", timestamp);
            headers.set("X-HSP-Payment-Projection-Signature",
                    PaymentEventHttpSignature.sign(body, timestamp, refundProjectionSecret));
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    refundProjectionUrl.trim(), new HttpEntity<>(body, headers), Map.class);
            if (response == null || !response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null
                    || !"0".equals(String.valueOf(response.getBody().get("code")))) {
                throw new IllegalStateException("退款中心支付成功投影未明确确认");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付成功投影序列化失败", exception);
        }
    }

    private HttpEntity<String> signedReconciliationFact(Map<String, Object> payload) {
        if (!StringUtils.hasText(reconciliationFactSecret)) {
            throw new IllegalStateException("支付对账事实签名密钥未配置");
        }
        try {
            String body = (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(payload);
            String source = "payment-core";
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            headers.set("X-HSP-Reconciliation-Fact-Source", source);
            headers.set("X-HSP-Reconciliation-Fact-Timestamp", timestamp);
            headers.set("X-HSP-Reconciliation-Fact-Signature",
                    PaymentEventHttpSignature.sign(source + "\n" + body, timestamp, reconciliationFactSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付对账事实序列化失败", exception);
        }
    }

    private void postReconciliationFact(Map<String, Object> payload) {
        if (!StringUtils.hasText(reconciliationFactUrl)) {
            return;
        }
        Object data = postForSuccess(reconciliationFactUrl, signedReconciliationFact(payload)).getData();
        if (!(data instanceof Map)) {
            LOGGER.warn("支付对账事实回执拒绝 paymentOrderId={}, reason=STRUCTURE", payload.get("factNo"));
            throw new IllegalStateException("对账事实回执缺少业务链快照");
        }
        Map<?, ?> snapshot = (Map<?, ?>) data;
        if (!Objects.equals(payload.get("businessChainId"), snapshot.get("businessChainId"))
                || !(snapshot.get("facts") instanceof List)) {
            LOGGER.warn("支付对账事实回执拒绝 paymentOrderId={}, reason=CHAIN_OR_FACTS", payload.get("factNo"));
            throw new IllegalStateException("对账事实回执与原业务链不匹配");
        }
        for (Object item : (List<?>) snapshot.get("facts")) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> fact = (Map<?, ?>) item;
            if (Objects.equals(payload.get("businessChainId"), fact.get("businessChainId"))
                    && Objects.equals(payload.get("systemCode"), fact.get("systemCode"))
                    && Objects.equals(payload.get("factType"), fact.get("factType"))
                    && Objects.equals(payload.get("factNo"), fact.get("factNo"))
                    && Objects.equals(payload.get("businessNo"), fact.get("businessNo"))
                    && Objects.equals(payload.get("payScene"), fact.get("payScene"))
                    && Objects.equals(payload.get("factStatus"), fact.get("factStatus"))
                    && fact.get("amount") instanceof Number
                    && new BigDecimal(fact.get("amount").toString())
                            .compareTo((BigDecimal) payload.get("amount")) == 0) {
                return;
            }
        }
        LOGGER.warn("支付对账事实回执拒绝 paymentOrderId={}, reason=FACT_MISSING", payload.get("factNo"));
        throw new IllegalStateException("对账事实回执缺少本次支付成功事实");
    }

    private Map<String, Object> buildReconciliationFactPayload(String paymentOrderId,
                                                               String orderNo,
                                                               String payScene,
                                                               BigDecimal amount) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("businessChainId", businessChainId(paymentOrderId));
        payload.put("systemCode", "payment-core");
        payload.put("factType", PAYMENT_SUCCESS_EVENT_TYPE);
        payload.put("factNo", paymentOrderId);
        payload.put("businessNo", orderNo);
        payload.put("payScene", payScene);
        payload.put("factStatus", "SUCCESS");
        payload.put("amount", amount);
        payload.put("payload", "{\"paymentOrderId\":\"" + paymentOrderId
                + "\",\"payScene\":\"" + (payScene == null ? "" : payScene) + "\"}");
        return payload;
    }

    private boolean isSuccessfulResponseCode(String code) {
        if (!isValidConfiguredText(code, DOWNSTREAM_RESPONSE_CODE_MAX_LENGTH)) {
            return false;
        }
        return "0".equals(code.trim());
    }

    private PaymentSuccessClearingPayload buildClearingPayload(String eventNo,
                                                               String paymentOrderId,
                                                               String orderNo,
                                                               String customerName,
                                                               String workerName,
                                                               String payScene,
                                                               BigDecimal amount) {
        return new PaymentSuccessClearingPayload(
                eventNo,
                PAYMENT_SUCCESS_EVENT_TYPE,
                PAYMENT_SUCCESS_EVENT_SCHEMA_VERSION,
                businessChainId(paymentOrderId),
                paymentOrderId,
                orderNo,
                currentBusinessDate(),
                customerName,
                "家政平台",
                workerName,
                payScene,
                amount);
    }

    private String currentBusinessDate() {
        return LocalDate.now(BUSINESS_ZONE_ID).toString();
    }

    private PaymentSuccessAccountingPayload buildAccountingPayload(String eventNo,
                                                                   String paymentOrderId,
                                                                   String orderNo,
                                                                   String customerName,
                                                                   String payScene,
                                                                   BigDecimal amount) {
        return new PaymentSuccessAccountingPayload(
                eventNo,
                PAYMENT_SUCCESS_EVENT_TYPE,
                PAYMENT_SUCCESS_EVENT_SCHEMA_VERSION,
                businessChainId(paymentOrderId),
                accountingPaymentSuccessAccountNo,
                paymentOrderId,
                orderNo,
                customerName,
                payScene,
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
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }

    /**
     * 清分事件载荷。
     */
    private static class PaymentSuccessClearingPayload {

        private final String eventNo;
        private final String eventType;
        private final String schemaVersion;
        private final String businessChainId;
        private final String paymentOrderId;
        private final String orderNo;
        private final String batchDate;
        private final String customerName;
        private final String merchantName;
        private final String workerName;
        private final String payScene;
        private final BigDecimal amount;

        PaymentSuccessClearingPayload(String eventNo,
                                      String eventType,
                                      String schemaVersion,
                                      String businessChainId,
                                      String paymentOrderId,
                                      String orderNo,
                                      String batchDate,
                                      String customerName,
                                      String merchantName,
                                      String workerName,
                                      String payScene,
                                      BigDecimal amount) {
            this.eventNo = eventNo;
            this.eventType = eventType;
            this.schemaVersion = schemaVersion;
            this.businessChainId = businessChainId;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.batchDate = batchDate;
            this.customerName = customerName;
            this.merchantName = merchantName;
            this.workerName = workerName;
            this.payScene = payScene;
            this.amount = amount;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getEventNo() {
            return eventNo;
        }

        public String getEventType() {
            return eventType;
        }

        public String getSchemaVersion() {
            return schemaVersion;
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

        public String getPayScene() {
            return payScene;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 账务事件载荷。
     */
    private static class PaymentSuccessAccountingPayload {

        private final String eventNo;
        private final String eventType;
        private final String schemaVersion;
        private final String businessChainId;
        private final String accountNo;
        private final String paymentOrderId;
        private final String orderNo;
        private final String customerName;
        private final String payScene;
        private final BigDecimal amount;

        PaymentSuccessAccountingPayload(String eventNo,
                                        String eventType,
                                        String schemaVersion,
                                        String businessChainId,
                                        String accountNo,
                                        String paymentOrderId,
                                        String orderNo,
                                        String customerName,
                                        String payScene,
                                        BigDecimal amount) {
            this.eventNo = eventNo;
            this.eventType = eventType;
            this.schemaVersion = schemaVersion;
            this.businessChainId = businessChainId;
            this.accountNo = accountNo;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.customerName = customerName;
            this.payScene = payScene;
            this.amount = amount;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getEventNo() {
            return eventNo;
        }

        public String getEventType() {
            return eventType;
        }

        public String getSchemaVersion() {
            return schemaVersion;
        }

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getPayScene() {
            return payScene;
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
