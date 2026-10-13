package com.abc123.clearing.service.impl;

import com.abc123.clearing.common.ClearingEventAmqpSignature;
import com.abc123.clearing.common.ClearingEventHttpSignature;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.abc123.clearing.service.ClearingEventDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 清分结果下游派发实现。
 */
@Service
public class ClearingEventDispatchServiceImpl implements ClearingEventDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearingEventDispatchServiceImpl.class);
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

    @Value("${clearing.downstream.reconciliation.fact-url:}")
    private String reconciliationFactUrl;

    @Value("${HSP_CLEARING_GENERATED_HTTP_SECRET:}")
    private String clearingGeneratedHttpSecret;

    @Value("${HSP_CLEARING_GENERATED_AMQP_SECRET:}")
    private String clearingGeneratedAmqpSecret;

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
        this.publisherConfirmTimeoutMs = publisherConfirmTimeoutMs <= 0 ? 5000L
                : Math.min(publisherConfirmTimeoutMs, 60000L);
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
        if ("SUCCESS".equalsIgnoreCase(outboxEvent.getPublishStatus())) {
            return true;
        }
        if (!clearingMemoryStore.tryClaimOutboxPublish(outboxEvent.getEventNo())) {
            return false;
        }
        List<ShareItemEntity> shares = clearingMemoryStore.sharesByClearingNo(clearingOrder.getClearingNo());
        ShareItemEntity workerShare = findShare(shares, "WORKER").orElse(null);
        BigDecimal workerAmount = workerShare != null ? workerShare.getShareAmount() : clearingOrder.getWorkerAmount();
        try {
            if (usesAmqp()) {
                publishToAmqp(outboxEvent);
                postReconciliationFact(clearingOrder);
                clearingMemoryStore.markOutboxPublishSuccess(outboxEvent.getEventNo());
                return true;
            }
            postClearingEvent(settlementUrl, buildSettlementPayload(clearingOrder, workerShare, workerAmount), clearingOrder);
            postClearingEvent(accountingUrl, buildAccountingPayload(clearingOrder, workerAmount), clearingOrder);
            postReconciliationFact(clearingOrder);
            clearingMemoryStore.markOutboxPublishSuccess(outboxEvent.getEventNo());
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("清分出站派发失败 eventNo={}, exceptionType={}",
                    outboxEvent.getEventNo(), exception.getClass().getSimpleName());
            clearingMemoryStore.markOutboxPublishFailed(outboxEvent.getEventNo());
            return false;
        }
    }

    private boolean usesAmqp() {
        return "amqp".equalsIgnoreCase(dispatchMode == null ? "" : dispatchMode.trim());
    }

    private void publishToAmqp(com.abc123.clearing.entity.ClearingEventEntity outboxEvent) {
        if (rabbitTemplate == null || objectMapper == null || !StringUtils.hasText(clearingGeneratedAmqpSecret)
                || !StringUtils.hasText(clearingGeneratedExchange)
                || !StringUtils.hasText(clearingGeneratedRoutingKey)) {
            throw new IllegalStateException("AMQP 清分结果事件配置不完整");
        }
        try {
            objectMapper.readTree(outboxEvent.getPayload());
            CorrelationData correlationData = new CorrelationData();
            rabbitTemplate.convertAndSend(clearingGeneratedExchange.trim(), clearingGeneratedRoutingKey.trim(),
                    outboxEvent.getPayload(), buildMessagePostProcessor(outboxEvent), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(publisherConfirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("清分结果事件发布未确认路由");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("清分结果事件发布确认中断", exception);
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
            properties.setHeader("schemaVersion", "1");
            properties.setHeader("clearingNo", event.getBizNo());
            properties.setHeader(ClearingEventAmqpSignature.HEADER_NAME,
                    ClearingEventAmqpSignature.sign(message.getBody(), clearingGeneratedAmqpSecret));
            return message;
        };
    }

    private Optional<ShareItemEntity> findShare(List<ShareItemEntity> shares, String shareType) {
        return shares.stream()
                .filter(item -> shareType.equals(item.getShareType()))
                .findFirst();
    }

    private Map<?, ?> postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("清分下游地址未配置");
        }
        ResponseEntity<Map> response = restTemplate.postForEntity(url.trim(), payload, Map.class);
        Object code = response == null || response.getBody() == null ? null : response.getBody().get("code");
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || !"0".equals(String.valueOf(code))
                || !(response.getBody().get("data") instanceof Map)) {
            LOGGER.warn("清分下游响应拒绝 target={}, reason=HTTP_CODE_OR_STRUCTURE",
                    url.equals(settlementUrl) ? "SETTLEMENT" : url.equals(accountingUrl) ? "ACCOUNTING" : "RECONCILIATION");
            throw new IllegalStateException("清分下游业务响应失败");
        }
        return (Map<?, ?>) response.getBody().get("data");
    }

    private void postClearingEvent(String url, Object payload, ClearingOrderEntity clearingOrder) {
        Map<?, ?> event = postForSuccess(url, signedClearingPayload(payload));
        if (!"CLEARING_GENERATED".equals(event.get("eventType"))
                || !Objects.equals(clearingOrder.getClearingNo(), event.get("bizNo"))
                || !Objects.equals(clearingOrder.getBusinessChainId(), event.get("businessChainId"))
                || !(event.get("eventNo") instanceof String)
                || !StringUtils.hasText((String) event.get("eventNo"))
                || !"已消费".equals(event.get("eventStatus"))) {
            LOGGER.warn("清分下游事件回执拒绝 clearingNo={}, target={}, reason=IDENTITY",
                    clearingOrder.getClearingNo(), url.equals(settlementUrl) ? "SETTLEMENT" : "ACCOUNTING");
            throw new IllegalStateException("清分下游事件回执归属不匹配");
        }
    }

    private Object signedClearingPayload(Object payload) {
        if (!StringUtils.hasText(clearingGeneratedHttpSecret)) {
            return payload;
        }
        try {
            String body = (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            headers.set(ClearingEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp);
            headers.set(ClearingEventHttpSignature.HEADER_NAME,
                    ClearingEventHttpSignature.sign(body, timestamp, clearingGeneratedHttpSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("清分结果签名序列化失败", exception);
        }
    }

    private void postReconciliationFact(ClearingOrderEntity clearingOrder) {
        if (!StringUtils.hasText(reconciliationFactUrl)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("businessChainId", clearingOrder.getBusinessChainId());
        payload.put("systemCode", "clearing-system");
        payload.put("factType", "CLEARING_GENERATED");
        payload.put("factNo", clearingOrder.getClearingNo());
        payload.put("businessNo", clearingOrder.getPaymentOrderId());
        payload.put("payScene", clearingOrder.getPayScene());
        payload.put("factStatus", "SUCCESS");
        payload.put("amount", clearingOrder.getOrderAmount());
        payload.put("payload", "{\"clearingNo\":\"" + clearingOrder.getClearingNo()
                + "\",\"payScene\":\"" + (clearingOrder.getPayScene() == null ? "" : clearingOrder.getPayScene()) + "\"}");
        Map<?, ?> snapshot = postForSuccess(reconciliationFactUrl, payload);
        if (!Objects.equals(payload.get("businessChainId"), snapshot.get("businessChainId"))
                || !(snapshot.get("facts") instanceof List)) {
            LOGGER.warn("清分对账事实回执拒绝 clearingNo={}, reason=CHAIN_OR_FACTS", clearingOrder.getClearingNo());
            throw new IllegalStateException("清分对账回执业务链不匹配");
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
        LOGGER.warn("清分对账事实回执拒绝 clearingNo={}, reason=FACT_MISSING", clearingOrder.getClearingNo());
        throw new IllegalStateException("清分对账回执缺少本次清分事实");
    }

    private SettlementClearingGeneratedPayload buildSettlementPayload(
            ClearingOrderEntity clearingOrder,
            ShareItemEntity workerShare,
            BigDecimal workerAmount) {
        String targetNo = workerShare == null ? "WRK1001" : workerShare.getShareTargetNo();
        String targetName = workerShare == null ? "待分配服务者" : workerShare.getShareTargetName();
        return new SettlementClearingGeneratedPayload(
                clearingOrder.getBusinessChainId(),
                clearingOrder.getClearingNo(),
                clearingOrder.getPaymentOrderId(),
                clearingOrder.getPayScene(),
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
                clearingOrder.getBusinessChainId(),
                clearingOrder.getPayScene(),
                workerAmount,
                "清分结果入账至服务者应收");
    }

    /**
     * 结算系统清分结果载荷。
     */
    private static class SettlementClearingGeneratedPayload {

        private final String businessChainId;
        private final String clearingNo;
        private final String paymentOrderId;
        private final String payScene;
        private final String targetType;
        private final String targetNo;
        private final String targetName;
        private final BigDecimal shouldSettleAmount;
        private final BigDecimal deductAmount;
        private final BigDecimal netSettleAmount;

        SettlementClearingGeneratedPayload(String businessChainId,
                                           String clearingNo,
                                           String paymentOrderId,
                                           String payScene,
                                           String targetType,
                                           String targetNo,
                                           String targetName,
                                           BigDecimal shouldSettleAmount,
                                           BigDecimal deductAmount,
                                           BigDecimal netSettleAmount) {
            this.businessChainId = businessChainId;
            this.clearingNo = clearingNo;
            this.paymentOrderId = paymentOrderId;
            this.payScene = payScene;
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

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getPayScene() {
            return payScene;
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
        private final String businessChainId;
        private final String payScene;
        private final BigDecimal amount;
        private final String summary;

        AccountingClearingGeneratedPayload(String accountNo,
                                           String clearingOrderNo,
                                           String bizNo,
                                           String businessChainId,
                                           String payScene,
                                           BigDecimal amount,
                                           String summary) {
            this.accountNo = accountNo;
            this.clearingOrderNo = clearingOrderNo;
            this.bizNo = bizNo;
            this.businessChainId = businessChainId;
            this.payScene = payScene;
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

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getPayScene() {
            return payScene;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getSummary() {
            return summary;
        }
    }
}
