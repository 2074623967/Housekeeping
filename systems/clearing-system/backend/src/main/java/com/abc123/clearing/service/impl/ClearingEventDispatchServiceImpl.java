package com.abc123.clearing.service.impl;

import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.abc123.clearing.service.ClearingEventDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 清分结果下游派发实现。
 */
@Service
public class ClearingEventDispatchServiceImpl implements ClearingEventDispatchService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final String settlementUrl;
    private final String accountingUrl;
    private final String accountingAccountNo;
    private final RestTemplate restTemplate;
    private final String dispatchMode;
    private final String clearingGeneratedExchange;
    private final String clearingGeneratedRoutingKey;
    private final long publisherConfirmTimeoutMs;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            @Value("${clearing.downstream.settlement.clearing-generated-url:http://127.0.0.1:18130/api/settlements/events/clearing/generated}")
            String settlementUrl,
            @Value("${clearing.downstream.accounting.clearing-generated-url:http://127.0.0.1:18110/api/accounting/events/clearing/generated}")
            String accountingUrl,
            @Value("${clearing.downstream.accounting.clearing-generated-account-no:ACT10002}")
            String accountingAccountNo,
            @Value("${clearing.amqp.dispatch-mode:http}") String dispatchMode,
            @Value("${clearing.amqp.clearing-generated-exchange:clearing.trade}") String clearingGeneratedExchange,
            @Value("${clearing.amqp.clearing-generated-routing-key:clearing.generated.v1}") String clearingGeneratedRoutingKey,
            @Value("${clearing.amqp.publisher-confirm-timeout-ms:5000}") long publisherConfirmTimeoutMs,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this(clearingMemoryStore, settlementUrl, accountingUrl, accountingAccountNo, new RestTemplate(), dispatchMode,
                clearingGeneratedExchange, clearingGeneratedRoutingKey, publisherConfirmTimeoutMs, rabbitTemplate, objectMapper);
    }

    ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            String settlementUrl,
            String accountingUrl,
            String accountingAccountNo,
            RestTemplate restTemplate) {
        this(clearingMemoryStore, settlementUrl, accountingUrl, accountingAccountNo, restTemplate, "http",
                "clearing.trade", "clearing.generated.v1", 5000L, null, null);
    }

    ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            String clearingGeneratedExchange,
            String clearingGeneratedRoutingKey) {
        this(clearingMemoryStore, null, null, "ACT10002", null, "amqp", clearingGeneratedExchange,
                clearingGeneratedRoutingKey, 5000L, rabbitTemplate, objectMapper);
    }

    private ClearingEventDispatchServiceImpl(
            ClearingMemoryStore clearingMemoryStore,
            String settlementUrl,
            String accountingUrl,
            String accountingAccountNo,
            RestTemplate restTemplate,
            String dispatchMode,
            String clearingGeneratedExchange,
            String clearingGeneratedRoutingKey,
            long publisherConfirmTimeoutMs,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.settlementUrl = settlementUrl;
        this.accountingUrl = accountingUrl;
        this.accountingAccountNo = accountingAccountNo;
        this.restTemplate = restTemplate;
        this.dispatchMode = dispatchMode;
        this.clearingGeneratedExchange = clearingGeneratedExchange;
        this.clearingGeneratedRoutingKey = clearingGeneratedRoutingKey;
        this.publisherConfirmTimeoutMs = publisherConfirmTimeoutMs <= 0 ? 5000L : publisherConfirmTimeoutMs;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean publishClearingGenerated(String paymentOrderId) {
        if (!StringUtils.hasText(paymentOrderId)) {
            return false;
        }
        ClearingOrderEntity clearingOrder = clearingMemoryStore.orders().stream()
                .filter(item -> paymentOrderId.equals(item.getPaymentOrderId()))
                .findFirst()
                .orElse(null);
        if (clearingOrder == null) {
            return false;
        }
        com.abc123.clearing.entity.ClearingEventEntity outboxEvent = clearingMemoryStore
                .findClearingGeneratedOutboxEvent(paymentOrderId);
        if (outboxEvent == null) {
            return false;
        }
        List<ShareItemEntity> shares = clearingMemoryStore.sharesByClearingNo(clearingOrder.getClearingNo());
        ShareItemEntity workerShare = findShare(shares, "WORKER").orElse(null);
        BigDecimal workerAmount = workerShare != null ? workerShare.getShareAmount() : clearingOrder.getWorkerAmount();
        try {
            if (usesAmqp()) {
                publishToAmqp(outboxEvent);
                clearingMemoryStore.markOutboxPublishSuccess(outboxEvent.getEventNo());
                return true;
            }
            postForSuccess(settlementUrl, buildSettlementPayload(clearingOrder, workerShare, workerAmount));
            postForSuccess(accountingUrl, buildAccountingPayload(clearingOrder, workerAmount));
            clearingMemoryStore.markOutboxPublishSuccess(outboxEvent.getEventNo());
            return true;
        } catch (RuntimeException exception) {
            clearingMemoryStore.markOutboxPublishFailed(outboxEvent.getEventNo());
            return false;
        }
    }

    private boolean usesAmqp() {
        return "amqp".equalsIgnoreCase(dispatchMode == null ? "" : dispatchMode.trim());
    }

    private void publishToAmqp(com.abc123.clearing.entity.ClearingEventEntity outboxEvent) {
        if (rabbitTemplate == null || objectMapper == null || !StringUtils.hasText(clearingGeneratedExchange)
                || !StringUtils.hasText(clearingGeneratedRoutingKey)) {
            throw new IllegalStateException("AMQP 清分结果事件配置不完整");
        }
        try {
            objectMapper.readTree(outboxEvent.getPayload());
            rabbitTemplate.convertAndSend(clearingGeneratedExchange.trim(), clearingGeneratedRoutingKey.trim(),
                    outboxEvent.getPayload(), buildMessagePostProcessor(outboxEvent));
            rabbitTemplate.waitForConfirmsOrDie(publisherConfirmTimeoutMs);
        } catch (Exception exception) {
            throw new IllegalStateException("清分结果事件投递失败", exception);
        }
    }

    private MessagePostProcessor buildMessagePostProcessor(final com.abc123.clearing.entity.ClearingEventEntity event) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setMessageId(event.getEventNo());
            properties.setCorrelationId(event.getEventNo());
            properties.setHeader("eventType", event.getEventType());
            properties.setHeader("clearingNo", event.getBizNo());
            return message;
        };
    }

    private Optional<ShareItemEntity> findShare(List<ShareItemEntity> shares, String shareType) {
        return shares.stream()
                .filter(item -> shareType.equals(item.getShareType()))
                .findFirst();
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

    private SettlementClearingGeneratedPayload buildSettlementPayload(
            ClearingOrderEntity clearingOrder,
            ShareItemEntity workerShare,
            BigDecimal workerAmount) {
        String targetNo = workerShare == null ? "WRK1001" : workerShare.getShareTargetNo();
        String targetName = workerShare == null ? "待分配服务者" : workerShare.getShareTargetName();
        return new SettlementClearingGeneratedPayload(
                clearingOrder.getClearingNo(),
                clearingOrder.getPaymentOrderId(),
                "WORKER",
                targetNo,
                targetName,
                workerAmount,
                BigDecimal.ZERO,
                workerAmount);
    }

    private AccountingClearingGeneratedPayload buildAccountingPayload(
            ClearingOrderEntity clearingOrder,
            BigDecimal workerAmount) {
        return new AccountingClearingGeneratedPayload(
                accountingAccountNo,
                clearingOrder.getClearingNo(),
                clearingOrder.getPaymentOrderId(),
                workerAmount,
                "清分结果入账至服务者应收");
    }

    /**
     * 结算系统清分结果载荷。
     */
    private static class SettlementClearingGeneratedPayload {

        private final String clearingNo;
        private final String paymentOrderId;
        private final String targetType;
        private final String targetNo;
        private final String targetName;
        private final BigDecimal shouldSettleAmount;
        private final BigDecimal deductAmount;
        private final BigDecimal netSettleAmount;

        SettlementClearingGeneratedPayload(String clearingNo,
                                           String paymentOrderId,
                                           String targetType,
                                           String targetNo,
                                           String targetName,
                                           BigDecimal shouldSettleAmount,
                                           BigDecimal deductAmount,
                                           BigDecimal netSettleAmount) {
            this.clearingNo = clearingNo;
            this.paymentOrderId = paymentOrderId;
            this.targetType = targetType;
            this.targetNo = targetNo;
            this.targetName = targetName;
            this.shouldSettleAmount = shouldSettleAmount;
            this.deductAmount = deductAmount;
            this.netSettleAmount = netSettleAmount;
        }

        public String getClearingNo() {
            return clearingNo;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getTargetType() {
            return targetType;
        }

        public String getTargetNo() {
            return targetNo;
        }

        public String getTargetName() {
            return targetName;
        }

        public BigDecimal getShouldSettleAmount() {
            return shouldSettleAmount;
        }

        public BigDecimal getDeductAmount() {
            return deductAmount;
        }

        public BigDecimal getNetSettleAmount() {
            return netSettleAmount;
        }
    }

    /**
     * 账务系统清分结果载荷。
     */
    private static class AccountingClearingGeneratedPayload {

        private final String accountNo;
        private final String clearingOrderNo;
        private final String bizNo;
        private final BigDecimal amount;
        private final String summary;

        AccountingClearingGeneratedPayload(String accountNo,
                                           String clearingOrderNo,
                                           String bizNo,
                                           BigDecimal amount,
                                           String summary) {
            this.accountNo = accountNo;
            this.clearingOrderNo = clearingOrderNo;
            this.bizNo = bizNo;
            this.amount = amount;
            this.summary = summary;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getClearingOrderNo() {
            return clearingOrderNo;
        }

        public String getBizNo() {
            return bizNo;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getSummary() {
            return summary;
        }
    }
}
