package com.abc123.refund.service.impl;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.common.RefundEventHttpSignature;
import com.abc123.refund.dao.RefundDao;
import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOrderEntity;
import com.abc123.refund.entity.RefundOutboxEventEntity;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.service.RefundService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 退款中心业务编排实现。
 *
 * <p>所有状态更新均使用期望状态条件，避免重复点击或重复回调造成状态回退。</p>
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final String REVIEWING = "REVIEWING";
    private static final String APPROVED = "APPROVED";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAIL = "FAIL";
    private static final String OUTBOX_SENT = "SENT";
    private static final String OUTBOX_DISPATCH_FAIL = "FAIL";
    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final RefundDao refundDao;
    private final String operator;
    private final String outboxDispatchMode;
    private final String accountingRefundSuccessUrl;
    private final String accountingRefundSuccessAccountNo;
    private final String reconciliationFactUrl;
    private final RestTemplate restTemplate;
    private final String refundSuccessHttpSecret;

    @Value("${HSP_REFUND_RECONCILIATION_FACT_SECRET:}")
    private String reconciliationFactSecret;

    @Autowired
    public RefundServiceImpl(RefundDao refundDao,
                             @Value("${refund-center.operator:refund-center-admin}") String operator,
                             @Value("${refund-center.outbox-dispatch-mode:HTTP}") String outboxDispatchMode,
                             @Value("${refund-center.downstream.accounting.refund-success-url:}")
                             String accountingRefundSuccessUrl,
                             @Value("${refund-center.downstream.accounting.refund-success-account-no:ACT10003}")
                             String accountingRefundSuccessAccountNo,
                             @Value("${refund-center.downstream.reconciliation.fact-url:}") String reconciliationFactUrl,
                             @Value("${HSP_REFUND_SUCCESS_HTTP_SECRET:}") String refundSuccessHttpSecret) {
        this(refundDao, operator, outboxDispatchMode, accountingRefundSuccessUrl,
                accountingRefundSuccessAccountNo, reconciliationFactUrl, new RestTemplate(), refundSuccessHttpSecret);
    }

    RefundServiceImpl(RefundDao refundDao, String operator) {
        this(refundDao, operator, "LOCAL", null, "ACT10003", null, new RestTemplate());
    }

    RefundServiceImpl(RefundDao refundDao,
                      String operator,
                      String outboxDispatchMode,
                      String accountingRefundSuccessUrl,
                      String accountingRefundSuccessAccountNo,
                      String reconciliationFactUrl,
                      RestTemplate restTemplate) {
        this(refundDao, operator, outboxDispatchMode, accountingRefundSuccessUrl,
                accountingRefundSuccessAccountNo, reconciliationFactUrl, restTemplate, "");
    }

    RefundServiceImpl(RefundDao refundDao,
                      String operator,
                      String outboxDispatchMode,
                      String accountingRefundSuccessUrl,
                      String accountingRefundSuccessAccountNo,
                      String reconciliationFactUrl,
                      RestTemplate restTemplate,
                      String refundSuccessHttpSecret) {
        this.refundDao = refundDao;
        this.operator = operator;
        this.outboxDispatchMode = outboxDispatchMode;
        this.accountingRefundSuccessUrl = accountingRefundSuccessUrl;
        this.accountingRefundSuccessAccountNo = accountingRefundSuccessAccountNo;
        this.reconciliationFactUrl = reconciliationFactUrl;
        this.restTemplate = restTemplate;
        this.refundSuccessHttpSecret = refundSuccessHttpSecret;
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        query = normalize(query);
        return new PageResultDTO<>(
                safeList(refundDao.findList(query)),
                refundDao.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public RefundDetailDTO detail(String refundOrderId) {
        String id = required(refundOrderId, "退款单号不能为空");
        RefundListItemDTO item = refundDao.findByRefundOrderId(id);
        if (item == null) {
            throw new BusinessException("退款单不存在");
        }
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId(item.getRefundOrderId());
        detail.setPaymentOrderId(item.getPaymentOrderId());
        detail.setPayScene(item.getPayScene());
        detail.setOrderNo(item.getOrderNo());
        detail.setCustomerName(item.getCustomerName());
        detail.setPaidAmount(item.getPaidAmount());
        detail.setRefundAmount(item.getRefundAmount());
        detail.setRefundMethod(item.getRefundMethod());
        detail.setRefundReason(item.getRefundReason());
        detail.setStatus(item.getStatus());
        detail.setChannelRefundId(item.getChannelRefundId());
        detail.setChannelCode(item.getChannelCode());
        detail.setFailureCode(item.getFailureCode());
        detail.setChannelSubmittedAt(item.getChannelSubmittedAt());
        detail.setAppliedAt(item.getAppliedAt());
        detail.setSuccessAt(item.getSuccessAt());
        detail.setTerminalAt(item.getTerminalAt());
        detail.setOperationLogs(safeList(refundDao.findLogs(id)));
        return detail;
    }

    @Override
    public byte[] exportDetailSnapshot(String refundOrderId) {
        RefundDetailDTO detail = detail(refundOrderId);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("退款单号,原支付单号,业务订单号,客户名称,原支付金额,退款金额,退款方式,退款原因,状态,状态样式,渠道退款号,失败码,申请时间,成功时间,终态时间\n");
        builder.append(csv(detail.getRefundOrderId())).append(',')
                .append(csv(detail.getPaymentOrderId())).append(',')
                .append(csv(detail.getOrderNo())).append(',')
                .append(csv(detail.getCustomerName())).append(',')
                .append(csv(decimal(detail.getPaidAmount()))).append(',')
                .append(csv(decimal(detail.getRefundAmount()))).append(',')
                .append(csv(detail.getRefundMethod())).append(',')
                .append(csv(detail.getRefundReason())).append(',')
                .append(csv(detail.getStatus())).append(',')
                .append(csv(detail.getStatusType())).append(',')
                .append(csv(detail.getChannelRefundId())).append(',')
                .append(csv(detail.getFailureCode())).append(',')
                .append(csv(time(detail.getAppliedAt()))).append(',')
                .append(csv(time(detail.getSuccessAt()))).append(',')
                .append(csv(time(detail.getTerminalAt()))).append('\n');
        builder.append('\n');
        builder.append("操作日志\n");
        builder.append("日志号,动作编码,动作名称,原状态,新状态,操作人,备注,操作时间\n");
        for (int i = 0; i < detail.getOperationLogs().size(); i++) {
            RefundOperationLogDTO log = detail.getOperationLogs().get(i);
            builder.append(csv(log.getLogNo())).append(',')
                    .append(csv(log.getActionCode())).append(',')
                    .append(csv(log.getActionName())).append(',')
                    .append(csv(log.getFromStatus())).append(',')
                    .append(csv(log.getToStatus())).append(',')
                    .append(csv(log.getOperatorName())).append(',')
                    .append(csv(log.getOperationRemark())).append(',')
                    .append(csv(time(log.getOperatedAt()))).append('\n');
        }
        builder.append('\n');
        builder.append("退款成功事件出站\n");
        builder.append("事件编号,事件类型,聚合编号,出站状态,重试次数,最近错误信息,最近派发时间,创建时间,更新时间\n");
        RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
        query.setAggregateId(detail.getRefundOrderId());
        query.setPageNo(1);
        query.setPageSize(10);
        PageResultDTO<RefundOutboxItemDTO> outboxPage = listOutbox(query);
        for (int i = 0; i < outboxPage.getItems().size(); i++) {
            RefundOutboxItemDTO item = outboxPage.getItems().get(i);
            builder.append(csv(item.getEventId())).append(',')
                    .append(csv(item.getEventType())).append(',')
                    .append(csv(item.getAggregateId())).append(',')
                    .append(csv(item.getStatus())).append(',')
                    .append(csv(item.getRetryCount() == null ? null : String.valueOf(item.getRetryCount()))).append(',')
                    .append(csv(item.getLastErrorMessage())).append(',')
                    .append(csv(time(item.getLastRelayAt()))).append(',')
                    .append(csv(time(item.getCreatedAt()))).append(',')
                    .append(csv(time(item.getUpdatedAt()))).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public RefundOverviewDTO overview() {
        return refundDao.overview();
    }

    @Override
    public PageResultDTO<RefundOutboxItemDTO> listOutbox(RefundOutboxQueryDTO query) {
        query = normalize(query);
        return new PageResultDTO<>(
                safeList(refundDao.findOutboxList(query)),
                refundDao.countOutbox(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    @Transactional
    public RefundListItemDTO apply(RefundApplyRequestDTO request) {
        if (request == null) {
            throw new BusinessException("退款申请不能为空");
        }
        String paymentOrderId = required(request.getPaymentOrderId(), "原支付单号不能为空");
        validateMoneyAmount(request.getRefundAmount(), "退款金额必须大于0且最多保留2位小数");
        String refundMethod = StringUtils.hasText(request.getRefundMethod())
                ? request.getRefundMethod().trim() : "ORIGINAL";
        String refundReason = StringUtils.hasText(request.getRefundReason())
                ? request.getRefundReason().trim() : "客户申请退款";
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        PaymentSuccessProjectionEntity source = refundDao.findPaymentSourceForUpdate(paymentOrderId);
        if (source == null) {
            throw new BusinessException("未找到支付成功事实，禁止发起退款");
        }
        RefundListItemDTO existing = refundDao.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            assertSameRefundRequest(existing, paymentOrderId, request.getRefundAmount(),
                    refundMethod, refundReason);
            return existing;
        }
        BigDecimal refunded = refundDao.sumActiveRefundAmount(paymentOrderId);
        if (refunded == null) {
            refunded = BigDecimal.ZERO;
        }
        if (refunded.add(request.getRefundAmount()).compareTo(source.getPaidAmount()) > 0) {
            throw new BusinessException("累计退款金额不能超过原支付金额");
        }
        RefundOrderEntity entity = new RefundOrderEntity();
        entity.setRefundOrderId(newRefundOrderId(LocalDateTime.now()));
        entity.setPaymentOrderId(paymentOrderId);
        entity.setChannelCode(source.getChannelCode());
        entity.setOrderNo(source.getOrderNo());
        entity.setCustomerName(source.getCustomerName());
        entity.setPaidAmount(source.getPaidAmount());
        entity.setRefundAmount(request.getRefundAmount());
        entity.setRefundMethod(refundMethod);
        entity.setRefundReason(refundReason);
        entity.setStatus(REVIEWING);
        entity.setIdempotencyKey(idempotencyKey);
        if (refundDao.insertRefund(entity) != 1) {
            existing = refundDao.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                assertSameRefundRequest(existing, paymentOrderId, request.getRefundAmount(),
                        refundMethod, refundReason);
                return existing;
            }
            throw new BusinessException("退款申请重复或写入失败");
        }
        refundDao.insertLog(entity.getRefundOrderId(), "APPLY", "发起退款申请",
                "INIT", REVIEWING, operator, entity.getRefundReason());
        return refundDao.findByRefundOrderId(entity.getRefundOrderId());
    }

    static String newRefundOrderId(LocalDateTime timestamp) {
        return "REF" + timestamp.format(ID_FORMATTER) + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null) {
            return UUID.randomUUID().toString();
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new BusinessException("退款幂等键包含控制字符");
            }
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized) || normalized.length() > 128) {
            throw new BusinessException("退款幂等键不能为空且不能超过128字符");
        }
        return normalized;
    }

    private void assertSameRefundRequest(RefundListItemDTO existing, String paymentOrderId,
                                         BigDecimal refundAmount, String refundMethod,
                                         String refundReason) {
        if (!paymentOrderId.equals(trim(existing.getPaymentOrderId()))
                || existing.getRefundAmount() == null
                || existing.getRefundAmount().compareTo(refundAmount) != 0
                || !refundMethod.equals(trim(existing.getRefundMethod()))
                || !refundReason.equals(trim(existing.getRefundReason()))) {
            throw new BusinessException("退款幂等键与已存在退款申请不一致");
        }
    }

    @Override
    @Transactional
    public RefundListItemDTO approve(RefundActionRequestDTO request) {
        return transition(request, REVIEWING, APPROVED, "APPROVE", "审核通过");
    }

    @Override
    @Transactional
    public RefundListItemDTO submit(RefundActionRequestDTO request) {
        return transition(request, APPROVED, PROCESSING, "SUBMIT", "提交退款渠道");
    }

    @Override
    @Transactional
    public RefundListItemDTO callback(RefundCallbackRequestDTO request) {
        if (request == null) {
            throw new BusinessException("退款回调不能为空");
        }
        String id = required(request.getRefundOrderId(), "退款单号不能为空");
        String result = trim(request.getResult());
        if (!SUCCESS.equalsIgnoreCase(result) && !FAIL.equalsIgnoreCase(result)) {
            throw new BusinessException("退款回调结果必须为 SUCCESS 或 FAIL");
        }
        String target = SUCCESS.equalsIgnoreCase(result) ? SUCCESS : FAIL;
        String channelRefundId = normalizeCallbackIdentifier(request.getChannelRefundId(), "渠道退款流水号", 128);
        String failureCode = normalizeCallbackIdentifier(request.getFailureCode(), "渠道失败码", 64);
        String rawMessage = request.getRawMessage();
        if (rawMessage != null && rawMessage.length() > 500) {
            throw new BusinessException("渠道回执摘要不能超过500字符");
        }
        if (SUCCESS.equals(target) && !StringUtils.hasText(channelRefundId)) {
            throw new BusinessException("退款成功回调缺少渠道退款流水号");
        }
        int affected;
        try {
            affected = refundDao.updateCallback(id, target, channelRefundId, failureCode);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("渠道退款流水归属冲突，待人工核查");
        }
        if (affected == 0) {
            RefundListItemDTO existing = refundDao.findByRefundOrderId(id);
            if (existing == null) {
                throw new BusinessException("退款单不存在");
            }
            if ("PROCESSING".equals(existing.getStatus())
                    && channelRefundId != null && !StringUtils.hasText(existing.getChannelCode())) {
                throw new BusinessException("退款单缺少原支付渠道事实，待人工核查");
            }
            if (SUCCESS.equals(target) && SUCCESS.equals(existing.getStatus())) {
                assertSameSuccessfulCallback(existing, channelRefundId);
                return existing;
            }
            if (FAIL.equals(target) && FAIL.equals(existing.getStatus())) {
                assertSameFailedCallback(existing, channelRefundId, failureCode);
                return existing;
            }
            throw new BusinessException("退款单不在可回调状态，或回调已处理");
        }
        refundDao.insertLog(id, "CALLBACK_" + target, "渠道退款回调",
                PROCESSING, target, "channel-callback",
                StringUtils.hasText(rawMessage) ? rawMessage.trim() : target);
        if (SUCCESS.equals(target)) {
            RefundListItemDTO refund = refundDao.findByRefundOrderId(id);
            if (refundDao.insertSuccessOutbox(id, refund.getPaymentOrderId(), refund.getPayScene(),
                    refund.getRefundAmount()) != 1) {
                throw new IllegalStateException("refund success outbox was not created atomically");
            }
            return refund;
        }
        return refundDao.findByRefundOrderId(id);
    }

    private void assertSameSuccessfulCallback(RefundListItemDTO existing, String channelRefundId) {
        if (!Objects.equals(trim(existing.getChannelRefundId()), channelRefundId)) {
            throw new BusinessException("退款成功回调与已存在渠道退款事实不一致");
        }
    }

    private void assertSameFailedCallback(RefundListItemDTO existing, String channelRefundId, String failureCode) {
        if (!Objects.equals(trim(existing.getChannelRefundId()), channelRefundId)
                || !Objects.equals(trim(existing.getFailureCode()), failureCode)) {
            throw new BusinessException("退款失败回调与已存在失败事实不一致");
        }
    }

    @Override
    @Transactional
    public RefundListItemDTO retry(RefundActionRequestDTO request) {
        return transition(request, FAIL, PROCESSING, "RETRY", "失败退款重新提交");
    }

    @Override
    @Transactional
    public RefundOutboxItemDTO dispatchOutbox(String eventId, RefundOutboxDispatchRequestDTO request) {
        String id = required(eventId, "事件编号不能为空");
        RefundOutboxEventEntity existing = refundDao.findOutboxByEventId(id);
        if (existing == null) {
            throw new BusinessException("退款成功事件不存在");
        }
        if (OUTBOX_SENT.equals(existing.getStatus())) {
            return loadSingleOutbox(id);
        }
        if (refundDao.markOutboxDispatching(id) != 1) {
            throw new BusinessException("退款成功事件正在派发，请稍后重试");
        }
        String simulateResult = request == null ? null : trim(request.getSimulateResult());
        String remark = request == null ? null : trim(request.getRemark());
        if (OUTBOX_DISPATCH_FAIL.equalsIgnoreCase(simulateResult)) {
            String errorMessage = StringUtils.hasText(remark) ? remark : "本地派发模拟失败";
            if (refundDao.markOutboxFailed(id, errorMessage) != 1) {
                throw new BusinessException("退款成功事件派发失败状态回写异常");
            }
            return loadSingleOutbox(id);
        }
        try {
            if (!"LOCAL".equalsIgnoreCase(outboxDispatchMode == null ? "" : outboxDispatchMode.trim())) {
                dispatchRefundSuccess(existing);
            }
            if (refundDao.markOutboxSent(id) != 1) {
                throw new BusinessException("退款成功事件派发状态回写异常");
            }
        } catch (RuntimeException exception) {
            String errorMessage = "下游派发失败:" + exception.getClass().getSimpleName();
            if (refundDao.markOutboxFailed(id, errorMessage) != 1) {
                throw new BusinessException("退款成功事件派发失败状态回写异常");
            }
        }
        return loadSingleOutbox(id);
    }

    private void dispatchRefundSuccess(RefundOutboxEventEntity event) {
        RefundListItemDTO refund = refundDao.findByRefundOrderId(event.getAggregateId());
        if (refund == null || !SUCCESS.equals(refund.getStatus())) {
            throw new BusinessException("退款成功事实不存在或状态异常");
        }
        String businessChainId = refund.getBusinessChainId();
        AccountingRefundSuccessPayload accountingPayload = new AccountingRefundSuccessPayload(
                businessChainId,
                accountingRefundSuccessAccountNo,
                refund.getRefundOrderId(),
                refund.getPaymentOrderId(),
                refund.getPayScene(),
                refund.getRefundAmount(),
                "退款成功后冲回原支付入账");
        Map<?, ?> accountingReceipt = postForSuccess(accountingRefundSuccessUrl,
                signedAccountingPayload(accountingPayload));
        if (!Objects.equals(businessChainId, accountingReceipt.get("businessChainId"))
                || !"REFUND_SUCCESS".equals(accountingReceipt.get("eventType"))
                || !Objects.equals(refund.getRefundOrderId(), accountingReceipt.get("bizNo"))
                || !"已消费".equals(accountingReceipt.get("eventStatus"))) {
            throw new IllegalStateException("退款成功账务回执与退款单不匹配");
        }
        ReconciliationFactPayload reconciliationPayload = new ReconciliationFactPayload(
                businessChainId,
                "refund-center",
                "REFUND_SUCCESS",
                event.getEventId(),
                refund.getRefundOrderId(),
                SUCCESS,
                refund.getRefundAmount(),
                refund.getPayScene(),
                "退款成功事实，原支付单号=" + trim(refund.getPaymentOrderId())
                        + "，支付场景=" + trim(refund.getPayScene()));
        Map<?, ?> reconciliationReceipt = postForSuccess(reconciliationFactUrl,
                signedReconciliationFact(reconciliationPayload));
        if (!Objects.equals(businessChainId, reconciliationReceipt.get("businessChainId"))
                || !(reconciliationReceipt.get("facts") instanceof List)) {
            throw new IllegalStateException("退款成功对账回执缺少业务链事实");
        }
        for (Object item : (List<?>) reconciliationReceipt.get("facts")) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> fact = (Map<?, ?>) item;
            if (Objects.equals(businessChainId, fact.get("businessChainId"))
                    && "refund-center".equals(fact.get("systemCode"))
                    && "REFUND_SUCCESS".equals(fact.get("factType"))
                    && Objects.equals(event.getEventId(), fact.get("factNo"))
                    && Objects.equals(refund.getRefundOrderId(), fact.get("businessNo"))
                    && Objects.equals(refund.getPayScene(), fact.get("payScene"))
                    && SUCCESS.equals(fact.get("factStatus"))
                    && fact.get("amount") instanceof Number
                    && new BigDecimal(fact.get("amount").toString())
                            .compareTo(refund.getRefundAmount()) == 0) {
                return;
            }
        }
        throw new IllegalStateException("退款成功对账回执缺少本次事实");
    }

    private HttpEntity<String> signedReconciliationFact(Object payload) {
        if (!StringUtils.hasText(reconciliationFactSecret)) {
            throw new IllegalStateException("退款对账事实签名密钥未配置");
        }
        try {
            String body = JSON_MAPPER.writeValueAsString(payload);
            String source = "refund-center";
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
            headers.set("X-HSP-Reconciliation-Fact-Source", source);
            headers.set("X-HSP-Reconciliation-Fact-Timestamp", timestamp);
            headers.set("X-HSP-Reconciliation-Fact-Signature",
                    RefundEventHttpSignature.sign(source + "\n" + body, timestamp, reconciliationFactSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("退款对账事实序列化失败", exception);
        }
    }

    private HttpEntity<String> signedAccountingPayload(Object payload) {
        if (!StringUtils.hasText(refundSuccessHttpSecret)) {
            throw new IllegalStateException("HTTP 退款成功事件签名密钥未配置");
        }
        try {
            String body = JSON_MAPPER.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            headers.set(RefundEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp);
            headers.set(RefundEventHttpSignature.HEADER_NAME,
                    RefundEventHttpSignature.sign(body, timestamp, refundSuccessHttpSecret));
            return new HttpEntity<>(body, headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("退款成功事件签名序列化失败", exception);
        }
    }

    private Map<?, ?> postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("退款成功下游地址未配置");
        }
        ResponseEntity<Map> response = restTemplate.postForEntity(url.trim(), payload, Map.class);
        if (response == null || response.getStatusCode() != HttpStatus.OK
                || response.getBody() == null || !"0".equals(response.getBody().get("code"))
                || !(response.getBody().get("data") instanceof Map)) {
            throw new IllegalStateException("退款成功下游响应失败");
        }
        return (Map<?, ?>) response.getBody().get("data");
    }

    @Override
    @Transactional
    public void projectPaymentSuccess(PaymentSuccessProjectionDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())) {
            throw new BusinessException("支付成功事实缺少必要字段");
        }
        validateMoneyAmount(request.getPaidAmount(), "支付成功事实金额必须大于0且最多保留2位小数");
        PaymentSuccessProjectionEntity entity = new PaymentSuccessProjectionEntity();
        entity.setPaymentOrderId(request.getPaymentOrderId().trim());
        entity.setOrderNo(required(request.getOrderNo(), "业务订单号不能为空"));
        entity.setCustomerName(trim(request.getCustomerName()));
        entity.setPaidAmount(request.getPaidAmount());
        entity.setChannelCode(trim(request.getChannelCode()));
        entity.setPayScene(trim(request.getPayScene()));
        entity.setPaidAt(LocalDateTime.now());
        PaymentSuccessProjectionEntity existing = refundDao.findPaymentSource(entity.getPaymentOrderId());
        if (existing != null) {
            assertSamePaymentSource(entity, existing);
            return;
        }
        int affected = refundDao.insertPaymentSource(entity);
        if (affected == 1) {
            return;
        }
        existing = refundDao.findPaymentSource(entity.getPaymentOrderId());
        if (existing == null) {
            throw new BusinessException("支付成功事实写入失败");
        }
        assertSamePaymentSource(entity, existing);
    }

    private void assertSamePaymentSource(
            PaymentSuccessProjectionEntity expected,
            PaymentSuccessProjectionEntity actual) {
        if (!Objects.equals(expected.getOrderNo(), actual.getOrderNo())
                || !Objects.equals(expected.getCustomerName(), actual.getCustomerName())
                || expected.getPaidAmount().compareTo(actual.getPaidAmount()) != 0
                || !Objects.equals(expected.getChannelCode(), actual.getChannelCode())
                || (expected.getPayScene() != null
                    && !Objects.equals(expected.getPayScene(), actual.getPayScene()))) {
            throw new BusinessException("支付成功事实与已存在投影不一致");
        }
    }

    private void validateMoneyAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new BusinessException(message);
        }
    }

    private RefundListItemDTO transition(RefundActionRequestDTO request, String from, String to,
                                         String actionCode, String actionName) {
        if (request == null) {
            throw new BusinessException("退款操作不能为空");
        }
        String id = required(request.getRefundOrderId(), "退款单号不能为空");
        int affected = refundDao.updateStatus(id, from, to, null);
        if (affected == 0) {
            if (PROCESSING.equals(to)) {
                RefundListItemDTO existing = refundDao.findByRefundOrderId(id);
                if (existing != null && from.equals(existing.getStatus())) {
                    if (!StringUtils.hasText(existing.getChannelCode())) {
                        throw new BusinessException("退款单缺少原支付渠道事实，待人工核查");
                    }
                    if (existing.getChannelRefundId() != null) {
                        throw new BusinessException(FAIL.equals(from)
                                ? "失败退款已有渠道流水，须人工核查后再处理"
                                : "退款单已有渠道流水，须人工核查后再处理");
                    }
                }
            }
            throw new BusinessException("退款单状态不允许执行当前操作");
        }
        refundDao.insertLog(id, actionCode, actionName, from, to, operator,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : actionName);
        return refundDao.findByRefundOrderId(id);
    }

    private RefundQueryDTO normalize(RefundQueryDTO query) {
        if (query == null) {
            query = new RefundQueryDTO();
        }
        query.setRefundOrderId(trim(query.getRefundOrderId()));
        query.setPaymentOrderId(trim(query.getPaymentOrderId()));
        query.setStatus(StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : null);
        query.setRefundMethod(StringUtils.hasText(query.getRefundMethod()) ? query.getRefundMethod().trim() : null);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private RefundOutboxQueryDTO normalize(RefundOutboxQueryDTO query) {
        if (query == null) {
            query = new RefundOutboxQueryDTO();
        }
        query.setEventId(trim(query.getEventId()));
        query.setAggregateId(trim(query.getAggregateId()));
        query.setStatus(StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : null);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private RefundOutboxItemDTO loadSingleOutbox(String eventId) {
        RefundOutboxEventEntity event = refundDao.findOutboxByEventId(eventId);
        if (event == null) {
            throw new BusinessException("退款成功事件不存在");
        }
        RefundOutboxItemDTO item = new RefundOutboxItemDTO();
        BeanUtils.copyProperties(event, item);
        return item;
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(value -> value != null).collect(java.util.stream.Collectors.toList());
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCallbackIdentifier(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new BusinessException(fieldName + "包含控制字符");
            }
        }
        if (value.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "字符");
        }
        return trim(value);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        char firstCharacter = value.isEmpty() ? '\0' : value.charAt(0);
        boolean formulaPrefix = firstCharacter == '=' || firstCharacter == '+' || firstCharacter == '-'
                || firstCharacter == '@' || firstCharacter == '\t' || firstCharacter == '\r'
                || firstCharacter == '\n' || firstCharacter == '\uFF1D' || firstCharacter == '\uFF0B'
                || firstCharacter == '\uFF0D' || firstCharacter == '\uFF20';
        String escaped = (formulaPrefix ? "'" + value : value).replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static class AccountingRefundSuccessPayload {

        private final String businessChainId;
        private final String accountNo;
        private final String refundOrderId;
        private final String paymentOrderId;
        private final String payScene;
        private final BigDecimal amount;
        private final String summary;

        AccountingRefundSuccessPayload(String businessChainId, String accountNo, String refundOrderId,
                                       String paymentOrderId, String payScene, BigDecimal amount, String summary) {
            this.businessChainId = businessChainId;
            this.accountNo = accountNo;
            this.refundOrderId = refundOrderId;
            this.paymentOrderId = paymentOrderId;
            this.payScene = payScene;
            this.amount = amount;
            this.summary = summary;
        }

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getRefundOrderId() {
            return refundOrderId;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
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

    private static class ReconciliationFactPayload {

        private final String businessChainId;
        private final String systemCode;
        private final String factType;
        private final String factNo;
        private final String businessNo;
        private final String factStatus;
        private final BigDecimal amount;
        private final String payScene;
        private final String payload;

        ReconciliationFactPayload(String businessChainId, String systemCode, String factType, String factNo,
                                  String businessNo, String factStatus, BigDecimal amount, String payScene, String payload) {
            this.businessChainId = businessChainId;
            this.systemCode = systemCode;
            this.factType = factType;
            this.factNo = factNo;
            this.businessNo = businessNo;
            this.factStatus = factStatus;
            this.amount = amount;
            this.payScene = payScene;
            this.payload = payload;
        }

        public String getBusinessChainId() {
            return businessChainId;
        }

        public String getSystemCode() {
            return systemCode;
        }

        public String getFactType() {
            return factType;
        }

        public String getFactNo() {
            return factNo;
        }

        public String getBusinessNo() {
            return businessNo;
        }

        public String getFactStatus() {
            return factStatus;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getPayScene() {
            return payScene;
        }

        public String getPayload() {
            return payload;
        }
    }
}
