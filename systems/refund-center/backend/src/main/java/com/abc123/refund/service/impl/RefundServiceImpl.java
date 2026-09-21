package com.abc123.refund.service.impl;

import com.abc123.refund.common.BusinessException;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    private final RefundDao refundDao;
    private final String operator;
    private final String outboxDispatchMode;
    private final String accountingRefundSuccessUrl;
    private final String accountingRefundSuccessAccountNo;
    private final String reconciliationFactUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public RefundServiceImpl(RefundDao refundDao,
                             @Value("${refund-center.operator:refund-center-admin}") String operator,
                             @Value("${refund-center.outbox-dispatch-mode:HTTP}") String outboxDispatchMode,
                             @Value("${refund-center.downstream.accounting.refund-success-url:}")
                             String accountingRefundSuccessUrl,
                             @Value("${refund-center.downstream.accounting.refund-success-account-no:ACT10003}")
                             String accountingRefundSuccessAccountNo,
                             @Value("${refund-center.downstream.reconciliation.fact-url:}") String reconciliationFactUrl) {
        this(refundDao, operator, outboxDispatchMode, accountingRefundSuccessUrl,
                accountingRefundSuccessAccountNo, reconciliationFactUrl, new RestTemplate());
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
        this.refundDao = refundDao;
        this.operator = operator;
        this.outboxDispatchMode = outboxDispatchMode;
        this.accountingRefundSuccessUrl = accountingRefundSuccessUrl;
        this.accountingRefundSuccessAccountNo = accountingRefundSuccessAccountNo;
        this.reconciliationFactUrl = reconciliationFactUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        query = normalize(query);
        return new PageResultDTO<>(
                refundDao.findList(query),
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
        detail.setOrderNo(item.getOrderNo());
        detail.setCustomerName(item.getCustomerName());
        detail.setPaidAmount(item.getPaidAmount());
        detail.setRefundAmount(item.getRefundAmount());
        detail.setRefundMethod(item.getRefundMethod());
        detail.setRefundReason(item.getRefundReason());
        detail.setStatus(item.getStatus());
        detail.setChannelRefundId(item.getChannelRefundId());
        detail.setAppliedAt(item.getAppliedAt());
        detail.setSuccessAt(item.getSuccessAt());
        detail.setOperationLogs(safeList(refundDao.findLogs(id)));
        return detail;
    }

    @Override
    public byte[] exportDetailSnapshot(String refundOrderId) {
        RefundDetailDTO detail = detail(refundOrderId);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("退款单号,原支付单号,业务订单号,客户名称,原支付金额,退款金额,退款方式,退款原因,状态,渠道退款号,申请时间,成功时间\n");
        builder.append(csv(detail.getRefundOrderId())).append(',')
                .append(csv(detail.getPaymentOrderId())).append(',')
                .append(csv(detail.getOrderNo())).append(',')
                .append(csv(detail.getCustomerName())).append(',')
                .append(csv(decimal(detail.getPaidAmount()))).append(',')
                .append(csv(decimal(detail.getRefundAmount()))).append(',')
                .append(csv(detail.getRefundMethod())).append(',')
                .append(csv(detail.getRefundReason())).append(',')
                .append(csv(detail.getStatus())).append(',')
                .append(csv(detail.getChannelRefundId())).append(',')
                .append(csv(time(detail.getAppliedAt()))).append(',')
                .append(csv(time(detail.getSuccessAt()))).append('\n');
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
                refundDao.findOutboxList(query),
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
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }
        PaymentSuccessProjectionEntity source = refundDao.findPaymentSource(paymentOrderId);
        if (source == null) {
            throw new BusinessException("未找到支付成功事实，禁止发起退款");
        }
        BigDecimal refunded = refundDao.sumActiveRefundAmount(paymentOrderId);
        if (refunded == null) {
            refunded = BigDecimal.ZERO;
        }
        if (refunded.add(request.getRefundAmount()).compareTo(source.getPaidAmount()) > 0) {
            throw new BusinessException("累计退款金额不能超过原支付金额");
        }
        RefundOrderEntity entity = new RefundOrderEntity();
        entity.setRefundOrderId("REF" + LocalDateTime.now().format(ID_FORMATTER));
        entity.setPaymentOrderId(paymentOrderId);
        entity.setOrderNo(source.getOrderNo());
        entity.setCustomerName(source.getCustomerName());
        entity.setPaidAmount(source.getPaidAmount());
        entity.setRefundAmount(request.getRefundAmount());
        entity.setRefundMethod(StringUtils.hasText(request.getRefundMethod()) ? request.getRefundMethod().trim() : "ORIGINAL");
        entity.setRefundReason(StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason().trim() : "客户申请退款");
        entity.setStatus(REVIEWING);
        entity.setIdempotencyKey(StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim() : UUID.randomUUID().toString());
        RefundListItemDTO existing = refundDao.findByIdempotencyKey(entity.getIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        if (refundDao.insertRefund(entity) != 1) {
            throw new BusinessException("退款申请重复或写入失败");
        }
        refundDao.insertLog(entity.getRefundOrderId(), "APPLY", "发起退款申请",
                "INIT", REVIEWING, operator, entity.getRefundReason());
        return refundDao.findByRefundOrderId(entity.getRefundOrderId());
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
        String target = "SUCCESS".equalsIgnoreCase(request.getResult()) ? SUCCESS : FAIL;
        int affected = refundDao.updateCallback(id, target, trim(request.getChannelRefundId()),
                trim(request.getFailureCode()));
        if (affected == 0) {
            RefundListItemDTO existing = refundDao.findByRefundOrderId(id);
            if (existing == null) {
                throw new BusinessException("退款单不存在");
            }
            if (SUCCESS.equals(target) && SUCCESS.equals(existing.getStatus())) {
                return existing;
            }
            if (FAIL.equals(target) && FAIL.equals(existing.getStatus())) {
                return existing;
            }
            throw new BusinessException("退款单不在可回调状态，或回调已处理");
        }
        refundDao.insertLog(id, "CALLBACK_" + target, "渠道退款回调",
                PROCESSING, target, "channel-callback",
                StringUtils.hasText(request.getRawMessage()) ? request.getRawMessage().trim() : target);
        if (SUCCESS.equals(target)) {
            RefundListItemDTO refund = refundDao.findByRefundOrderId(id);
            refundDao.insertSuccessOutbox(id, refund.getPaymentOrderId(), refund.getRefundAmount());
            return refund;
        }
        return refundDao.findByRefundOrderId(id);
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
        String businessChainId = "CHAIN-" + refund.getPaymentOrderId();
        postForSuccess(accountingRefundSuccessUrl, new AccountingRefundSuccessPayload(
                businessChainId,
                accountingRefundSuccessAccountNo,
                refund.getRefundOrderId(),
                refund.getPaymentOrderId(),
                refund.getRefundAmount(),
                "退款成功后冲回原支付入账"));
        postForSuccess(reconciliationFactUrl, new ReconciliationFactPayload(
                businessChainId,
                "refund-center",
                "REFUND_SUCCESS",
                event.getEventId(),
                refund.getRefundOrderId(),
                SUCCESS,
                refund.getRefundAmount(),
                "退款成功事实，原支付单号=" + refund.getPaymentOrderId()));
    }

    private void postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("退款成功下游地址未配置");
        }
        ResponseEntity<Map> response = restTemplate.postForEntity(url.trim(), payload, Map.class);
        Object responseCode = response.getBody() == null ? null : response.getBody().get("code");
        if (!response.getStatusCode().is2xxSuccessful() || !"0".equals(String.valueOf(responseCode))) {
            throw new IllegalStateException("退款成功下游响应失败");
        }
    }

    @Override
    @Transactional
    public void projectPaymentSuccess(PaymentSuccessProjectionDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || request.getPaidAmount() == null
                || request.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("支付成功事实缺少必要字段");
        }
        PaymentSuccessProjectionEntity entity = new PaymentSuccessProjectionEntity();
        entity.setPaymentOrderId(request.getPaymentOrderId().trim());
        entity.setOrderNo(trim(request.getOrderNo()));
        entity.setCustomerName(trim(request.getCustomerName()));
        entity.setPaidAmount(request.getPaidAmount());
        entity.setChannelCode(trim(request.getChannelCode()));
        entity.setPaidAt(LocalDateTime.now());
        refundDao.insertPaymentSource(entity);
    }

    private RefundListItemDTO transition(RefundActionRequestDTO request, String from, String to,
                                         String actionCode, String actionName) {
        if (request == null) {
            throw new BusinessException("退款操作不能为空");
        }
        String id = required(request.getRefundOrderId(), "退款单号不能为空");
        int affected = refundDao.updateStatus(id, from, to, null);
        if (affected == 0) {
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
        RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
        query.setEventId(eventId);
        query.setPageNo(1);
        query.setPageSize(1);
        return safeList(refundDao.findOutboxList(query)).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("退款成功事件不存在"));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
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

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
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
        private final BigDecimal amount;
        private final String summary;

        AccountingRefundSuccessPayload(String businessChainId, String accountNo, String refundOrderId,
                                       String paymentOrderId, BigDecimal amount, String summary) {
            this.businessChainId = businessChainId;
            this.accountNo = accountNo;
            this.refundOrderId = refundOrderId;
            this.paymentOrderId = paymentOrderId;
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
        private final String payload;

        ReconciliationFactPayload(String businessChainId, String systemCode, String factType, String factNo,
                                  String businessNo, String factStatus, BigDecimal amount, String payload) {
            this.businessChainId = businessChainId;
            this.systemCode = systemCode;
            this.factType = factType;
            this.factNo = factNo;
            this.businessNo = businessNo;
            this.factStatus = factStatus;
            this.amount = amount;
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

        public String getPayload() {
            return payload;
        }
    }
}
