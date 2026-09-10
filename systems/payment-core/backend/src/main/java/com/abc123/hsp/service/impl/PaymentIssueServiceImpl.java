package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertAcknowledgeRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.mapper.PaymentIssueMapper;
import com.abc123.hsp.service.PaymentIssueService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付交易异常中心业务实现。
 */
@Service
public class PaymentIssueServiceImpl implements PaymentIssueService {

    private static final int ISSUE_ACTION_BATCH_MAX_SIZE = 100;
    private static final int ISSUE_NO_MAX_LENGTH = 128;
    private static final int ALERT_NO_MAX_LENGTH = 64;
    private static final int ASSIGNEE_MAX_LENGTH = 64;
    private static final int REMARK_MAX_LENGTH = 255;
    private static final int OPERATOR_MAX_LENGTH = 64;

    private final PaymentIssueMapper paymentIssueMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;
    private final int receiptQueryMinAgeSeconds;
    private final int receiptQueryMaxAttempts;
    private final int receiptQueryClaimTimeoutSeconds;

    @Autowired
    public PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper,
                                   PaymentSensitiveDataMasker sensitiveDataMasker,
                                   @Value("${payment.issue-alert.receipt-query.compensation.min-age-seconds:60}")
                                           int receiptQueryMinAgeSeconds,
                                   @Value("${payment.issue-alert.receipt-query.compensation.max-attempts:10}")
                                           int receiptQueryMaxAttempts,
                                   @Value("${payment.issue-alert.receipt-query.compensation.claim-timeout-seconds:30}")
                                           int receiptQueryClaimTimeoutSeconds) {
        this.paymentIssueMapper = paymentIssueMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.receiptQueryMinAgeSeconds = Math.max(receiptQueryMinAgeSeconds, 1);
        this.receiptQueryMaxAttempts = Math.max(receiptQueryMaxAttempts, 1);
        this.receiptQueryClaimTimeoutSeconds = Math.max(receiptQueryClaimTimeoutSeconds, 1);
    }

    PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper) {
        this(paymentIssueMapper, new PaymentSensitiveDataMasker(new ObjectMapper()), 60, 10, 30);
    }

    @Override
    public PageResultDTO<PaymentIssueRowDTO> list(PaymentIssueQueryDTO query) {
        PaymentIssueQueryDTO normalizedQuery = normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(paymentIssueMapper.findAll(normalizedQuery)),
                paymentIssueMapper.count(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize()
        );
    }

    private List<PaymentIssueRowDTO> maskItems(List<PaymentIssueRowDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private PaymentIssueRowDTO maskedCopy(PaymentIssueRowDTO item) {
        if (item == null) {
            return null;
        }
        PaymentIssueRowDTO outwardItem = new PaymentIssueRowDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return outwardItem;
    }

    @Override
    public List<PaymentIssueResponsibilitySummaryDTO> responsibilitySummary(PaymentIssueQueryDTO query) {
        return paymentIssueMapper.responsibilitySummary(normalizeQuery(query));
    }

    @Override
    public PageResultDTO<PaymentIssueAlertLogRowDTO> listAlertLogs(PaymentIssueAlertLogQueryDTO query) {
        PaymentIssueAlertLogQueryDTO normalizedQuery = normalizeAlertLogQuery(query);
        return new PageResultDTO<>(
                maskAlertLogItems(paymentIssueMapper.findAlertLogs(normalizedQuery)),
                paymentIssueMapper.countAlertLogs(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize()
        );
    }

    @Override
    public String exportAlertLogsCsv(PaymentIssueAlertLogQueryDTO query) {
        PaymentIssueAlertLogQueryDTO normalizedQuery = normalizeAlertLogQuery(query);
        List<PaymentIssueAlertLogRowDTO> records = maskAlertLogItems(
                paymentIssueMapper.findAlertLogs(normalizedQuery));
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("告警编号,异常编号,支付单号,异常类型,严重等级,责任组,告警通道,接收人,告警状态,回执状态,供应商编码,供应商名称,供应商端点别名,模板编码,供应商投递状态,供应商投递说明,供应商回执号,回查队列状态,回查次数,最近回查时间,下次自动回查时间,最近回查结果,回执确认人,回执确认时间,触发来源,创建时间,内容快照,回执快照\n");
        for (PaymentIssueAlertLogRowDTO item : records) {
            builder.append(csv(item.getAlertNo())).append(',')
                    .append(csv(item.getIssueNo())).append(',')
                    .append(csv(item.getPaymentOrderId())).append(',')
                    .append(csv(item.getIssueType())).append(',')
                    .append(csv(item.getSeverity())).append(',')
                    .append(csv(item.getResponsibilityGroup())).append(',')
                    .append(csv(item.getAlertChannel())).append(',')
                    .append(csv(item.getReceiver())).append(',')
                    .append(csv(item.getAlertStatus())).append(',')
                    .append(csv(item.getAckStatus())).append(',')
                    .append(csv(item.getProviderCode())).append(',')
                    .append(csv(item.getProviderName())).append(',')
                    .append(csv(item.getEndpointAlias())).append(',')
                    .append(csv(item.getTemplateCode())).append(',')
                    .append(csv(item.getProviderDeliveryStatus())).append(',')
                    .append(csv(item.getProviderDeliveryMessage())).append(',')
                    .append(csv(item.getProviderReceiptNo())).append(',')
                    .append(csv(item.getReceiptQueryState())).append(',')
                    .append(csv(item.getReceiptQueryCount())).append(',')
                    .append(csv(item.getLastReceiptQueryAt())).append(',')
                    .append(csv(item.getNextReceiptQueryAt())).append(',')
                    .append(csv(item.getReceiptQueryResult())).append(',')
                    .append(csv(item.getAckOperator())).append(',')
                    .append(csv(item.getAckAt())).append(',')
                    .append(csv(item.getTriggeredBy())).append(',')
                    .append(csv(item.getCreatedAt())).append(',')
                    .append(csv(item.getAlertContent())).append(',')
                    .append(csv(item.getProviderReceiptSnapshot())).append('\n');
        }
        return builder.toString();
    }

    @Override
    @Transactional
    public PaymentIssueAlertLogRowDTO acknowledgeAlert(String alertNo, PaymentIssueAlertAcknowledgeRequestDTO request) {
        String normalizedAlertNo = normalizeText(alertNo, "告警编号", ALERT_NO_MAX_LENGTH, false);
        String operator = request == null ? null : request.getOperator();
        String normalizedOperator = normalizeOperator(operator, "确认人不能为空");
        PaymentIssueAlertLogRowDTO alertLog = paymentIssueMapper.findAlertLogByAlertNo(normalizedAlertNo);
        if (alertLog == null) {
            throw new IllegalArgumentException("支付异常告警不存在：" + normalizedAlertNo);
        }
        if ("无需回执".equals(alertLog.getAckStatus())) {
            throw new IllegalArgumentException("当前告警无需人工确认：" + normalizedAlertNo);
        }
        if ("已确认".equals(alertLog.getAckStatus())) {
            return maskedAlertLogCopy(alertLog);
        }
        paymentIssueMapper.acknowledgeAlertByAlertNo(normalizedAlertNo, normalizedOperator);
        acknowledgeSourceAlertIfNeeded(alertLog, normalizedOperator);
        return maskedAlertLogCopy(paymentIssueMapper.findAlertLogByAlertNo(normalizedAlertNo));
    }

    @Override
    @Transactional
    public PageResultDTO<PaymentIssueRowDTO> batchAction(PaymentIssueActionRequestDTO request) {
        if (request == null || request.getIssueNos() == null || request.getIssueNos().isEmpty()) {
            throw new IllegalArgumentException("异常编号不能为空");
        }
        if (request.getIssueNos().size() > ISSUE_ACTION_BATCH_MAX_SIZE) {
            throw new IllegalArgumentException("单次最多处理100条异常");
        }
        String actionType = normalizeText(request.getActionType(), "处理动作", 32, false);
        HandlingStatus handlingStatus = resolveHandlingStatus(actionType);
        String assignee = normalizeText(request.getAssignee(), "处理人", ASSIGNEE_MAX_LENGTH, false);
        String operator = normalizeOperator(request.getOperator(), "操作人不能为空");
        String remark = normalizeText(request.getRemark(), "处理备注", REMARK_MAX_LENGTH, true);
        List<String> normalizedIssueNos = request.getIssueNos().stream()
                .map(issueNo -> normalizeText(issueNo, "异常编号", ISSUE_NO_MAX_LENGTH, false))
                .collect(Collectors.toList());
        Set<String> distinctIssueNos = new HashSet<>(normalizedIssueNos);
        if (distinctIssueNos.size() != normalizedIssueNos.size()) {
            throw new IllegalArgumentException("异常编号不能重复");
        }
        for (String normalizedIssueNo : normalizedIssueNos) {
            PaymentIssueRowDTO issue = paymentIssueMapper.findByIssueNo(normalizedIssueNo);
            if (issue == null) {
                throw new IllegalArgumentException("支付交易异常不存在：" + normalizedIssueNo);
            }
            paymentIssueMapper.insertActionLog(
                    "ISSACT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                    normalizedIssueNo,
                    issue.getPaymentOrderId(),
                    issue.getIssueType(),
                    actionType,
                    assignee,
                    handlingStatus.status,
                    handlingStatus.statusType,
                    remark,
                    operator
            );
            if ("已处理".equals(handlingStatus.status)) {
                paymentIssueMapper.acknowledgePendingAlerts(normalizedIssueNo, operator);
            }
        }
        return list(new PaymentIssueQueryDTO());
    }

    private String normalizeText(String text, String fieldName, int maxLength, boolean defaultRemark) {
        if (text != null) {
            for (int index = 0; index < text.length(); index++) {
                if (Character.isISOControl(text.charAt(index))) {
                    throw new IllegalArgumentException(fieldName + "不能包含控制字符");
                }
            }
        }
        if (!StringUtils.hasText(text)) {
            if (defaultRemark) {
                return "批量处理";
            }
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = text.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "字符");
        }
        return normalized;
    }

    private PaymentIssueQueryDTO normalizeQuery(PaymentIssueQueryDTO query) {
        if (query == null) {
            query = new PaymentIssueQueryDTO();
        }
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setIssueType(query.getIssueType() == null ? "全部" : query.getIssueType().trim());
        query.setSeverity(query.getSeverity() == null ? "全部" : query.getSeverity().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setPaymentMethod(query.getPaymentMethod() == null ? "全部" : query.getPaymentMethod().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private PaymentIssueAlertLogQueryDTO normalizeAlertLogQuery(PaymentIssueAlertLogQueryDTO query) {
        if (query == null) {
            query = new PaymentIssueAlertLogQueryDTO();
        }
        query.setAlertNo(query.getAlertNo() == null ? null : query.getAlertNo().trim());
        query.setIssueNo(query.getIssueNo() == null ? null : query.getIssueNo().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setAlertChannel(query.getAlertChannel() == null ? "全部" : query.getAlertChannel().trim());
        query.setAlertStatus(query.getAlertStatus() == null ? "全部" : query.getAlertStatus().trim());
        query.setAckStatus(query.getAckStatus() == null ? "全部" : query.getAckStatus().trim());
        query.setProviderDeliveryStatus(query.getProviderDeliveryStatus() == null ? "全部" : query.getProviderDeliveryStatus().trim());
        query.setReceiptQueryState(normalizeReceiptQueryState(query.getReceiptQueryState()));
        query.setReceiptQueryMinAgeSeconds(receiptQueryMinAgeSeconds);
        query.setReceiptQueryMaxAttempts(receiptQueryMaxAttempts);
        query.setReceiptQueryClaimTimeoutSeconds(receiptQueryClaimTimeoutSeconds);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private String normalizeReceiptQueryState(String receiptQueryState) {
        if (!StringUtils.hasText(receiptQueryState) || "全部".equals(receiptQueryState.trim())) {
            return "全部";
        }
        String normalized = receiptQueryState.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("INITIAL_WAIT", "DUE", "BACKING_OFF", "QUERYING", "EXHAUSTED")
                .contains(normalized)) {
            throw new IllegalArgumentException("不支持的回执回查状态：" + receiptQueryState.trim());
        }
        return normalized;
    }

    private String requireText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private String normalizeOperator(String operator, String requiredMessage) {
        if (operator != null) {
            for (int index = 0; index < operator.length(); index++) {
                if (Character.isISOControl(operator.charAt(index))) {
                    throw new IllegalArgumentException("操作人不能包含控制字符");
                }
            }
        }
        String normalized = requireText(operator, requiredMessage);
        if (normalized.length() > OPERATOR_MAX_LENGTH) {
            throw new IllegalArgumentException("操作人长度不能超过64字符");
        }
        return normalized;
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

    private String csv(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<PaymentIssueAlertLogRowDTO> maskAlertLogItems(List<PaymentIssueAlertLogRowDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedAlertLogCopy).collect(Collectors.toList());
    }

    private PaymentIssueAlertLogRowDTO maskedAlertLogCopy(PaymentIssueAlertLogRowDTO item) {
        if (item == null) {
            return null;
        }
        PaymentIssueAlertLogRowDTO outwardItem = new PaymentIssueAlertLogRowDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setProviderReceiptSnapshot(
                sensitiveDataMasker.maskProviderReceiptSnapshot(item.getProviderReceiptSnapshot()));
        return outwardItem;
    }

    private void acknowledgeSourceAlertIfNeeded(PaymentIssueAlertLogRowDTO alertLog, String operator) {
        if (alertLog == null || !StringUtils.hasText(alertLog.getSourceAlertNo())) {
            return;
        }
        paymentIssueMapper.acknowledgeAlertByAlertNo(alertLog.getSourceAlertNo().trim(), operator);
    }

    private HandlingStatus resolveHandlingStatus(String actionType) {
        if ("分派处理人".equals(actionType)) {
            return new HandlingStatus("已分派", "info");
        }
        if ("标记跟进中".equals(actionType)) {
            return new HandlingStatus("跟进中", "warn");
        }
        if ("标记已处理".equals(actionType)) {
            return new HandlingStatus("已处理", "success");
        }
        if ("补充备注".equals(actionType)) {
            return new HandlingStatus("已备注", "info");
        }
        throw new IllegalArgumentException("不支持的处理动作：" + actionType);
    }

    private static final class HandlingStatus {
        private final String status;
        private final String statusType;

        private HandlingStatus(String status, String statusType) {
            this.status = status;
            this.statusType = statusType;
        }
    }
}
