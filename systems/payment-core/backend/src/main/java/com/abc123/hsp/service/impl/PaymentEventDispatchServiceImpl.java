package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 支付事件下游投递实现。
 */
@Service
public class PaymentEventDispatchServiceImpl implements PaymentEventDispatchService {

    private static final String PAYMENT_SUCCESS_EVENT_TYPE = "PAYMENT_SUCCESS";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

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
            @Value("${payment.amqp.payment-success-exchange:payment.trade}") String paymentSuccessExchange,
            @Value("${payment.amqp.payment-success-routing-key:payment.success.v1}") String paymentSuccessRoutingKey,
            @Value("${payment.amqp.publisher-confirm-timeout-ms:5000}") long publisherConfirmTimeoutMs,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                DEFAULT_MAX_RETRY_COUNT,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(3000),
                dispatchMode,
                paymentSuccessExchange,
                paymentSuccessRoutingKey,
                publisherConfirmTimeoutMs,
                rabbitTemplate,
                objectMapper);
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
                5000L,
                rabbitTemplate,
                objectMapper);
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
            ObjectMapper objectMapper) {
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
        this.publisherConfirmTimeoutMs = publisherConfirmTimeoutMs <= 0 ? 5000L : publisherConfirmTimeoutMs;
    }

    @Override
    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
        dispatchPaymentSuccess(eventNo, paymentOrderId);
    }

    @Override
    public boolean republish(String eventNo) {
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNo(eventNo);
        if (event == null || !PAYMENT_SUCCESS_EVENT_TYPE.equals(event.getEventType())) {
            return false;
        }
        return dispatchPaymentSuccess(eventNo, event.getPaymentOrderId());
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
            if (usesAmqp()) {
                publishToAmqp(eventNo, detail, workerName, amount);
                paymentEventMapper.markPublishSuccess(eventNo);
                return true;
            }
            postForSuccess(clearingUrl, buildClearingPayload(detail, workerName, amount));
            postForSuccess(accountingUrl, buildAccountingPayload(detail, amount));
            paymentEventMapper.markPublishSuccess(eventNo);
            return true;
        } catch (RuntimeException exception) {
            markFailedOrDeadLetter(eventNo);
            return false;
        }
    }

    private boolean usesAmqp() {
        return "amqp".equalsIgnoreCase(dispatchMode == null ? "" : dispatchMode.trim());
    }

    private void publishToAmqp(String eventNo, PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        if (rabbitTemplate == null || objectMapper == null || !StringUtils.hasText(paymentSuccessExchange)
                || !StringUtils.hasText(paymentSuccessRoutingKey)) {
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

    private void postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        ResponseEntity<String> response = restTemplate.postForEntity(url.trim(), payload, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("下游系统响应非 2xx");
        }
    }

    private PaymentSuccessClearingPayload buildClearingPayload(PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        return new PaymentSuccessClearingPayload(
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
     * 清分事件载荷。
     */
    private static class PaymentSuccessClearingPayload {

        private final String paymentOrderId;
        private final String orderNo;
        private final String batchDate;
        private final String customerName;
        private final String merchantName;
        private final String workerName;
        private final BigDecimal amount;

        PaymentSuccessClearingPayload(String paymentOrderId,
                                      String orderNo,
                                      String batchDate,
                                      String customerName,
                                      String merchantName,
                                      String workerName,
                                      BigDecimal amount) {
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

        private final String accountNo;
        private final String paymentOrderId;
        private final String orderNo;
        private final String customerName;
        private final BigDecimal amount;

        PaymentSuccessAccountingPayload(String accountNo,
                                        String paymentOrderId,
                                        String orderNo,
                                        String customerName,
                                        BigDecimal amount) {
            this.accountNo = accountNo;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.customerName = customerName;
            this.amount = amount;
        }

        public String getAccountNo() {
            return accountNo;
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
