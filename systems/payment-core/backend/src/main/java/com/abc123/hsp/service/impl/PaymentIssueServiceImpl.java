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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付交易异常中心业务实现。
 */
@Service
public class PaymentIssueServiceImpl implements PaymentIssueService {

    private final PaymentIssueMapper paymentIssueMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper,
                                   PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentIssueMapper = paymentIssueMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper) {
        this(paymentIssueMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
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
                paymentIssueMapper.findAlertLogs(normalizedQuery),
                paymentIssueMapper.countAlertLogs(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize()
        );
    }

    @Override
    public String exportAlertLogsCsv(PaymentIssueAlertLogQueryDTO query) {
        PaymentIssueAlertLogQueryDTO normalizedQuery = normalizeAlertLogQuery(query);
        List<PaymentIssueAlertLogRowDTO> records = paymentIssueMapper.findAlertLogs(normalizedQuery);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("告警编号,异常编号,支付单号,异常类型,严重等级,责任组,告警通道,接收人,告警状态,回执状态,供应商编码,供应商名称,供应商端点别名,模板编码,供应商投递状态,供应商投递说明,供应商回执号,回执确认人,回执确认时间,触发来源,创建时间,内容快照,回执快照\n");
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
        String normalizedAlertNo = requireText(alertNo, "告警编号不能为空");
        PaymentIssueAlertLogRowDTO alertLog = paymentIssueMapper.findAlertLogByAlertNo(normalizedAlertNo);
        if (alertLog == null) {
            throw new IllegalArgumentException("支付异常告警不存在：" + normalizedAlertNo);
        }
        if ("无需回执".equals(alertLog.getAckStatus())) {
            throw new IllegalArgumentException("当前告警无需人工确认：" + normalizedAlertNo);
        }
        if ("已确认".equals(alertLog.getAckStatus())) {
            return alertLog;
        }
        String operator = request == null ? null : request.getOperator();
        String normalizedOperator = requireText(operator, "确认人不能为空");
        paymentIssueMapper.acknowledgeAlertByAlertNo(normalizedAlertNo, normalizedOperator);
        acknowledgeSourceAlertIfNeeded(alertLog, normalizedOperator);
        return paymentIssueMapper.findAlertLogByAlertNo(normalizedAlertNo);
    }

    @Override
    @Transactional
    public PageResultDTO<PaymentIssueRowDTO> batchAction(PaymentIssueActionRequestDTO request) {
        if (request == null || request.getIssueNos() == null || request.getIssueNos().isEmpty()) {
            throw new IllegalArgumentException("异常编号不能为空");
        }
        String actionType = requireText(request.getActionType(), "处理动作不能为空");
        String assignee = requireText(request.getAssignee(), "处理人不能为空");
        String operator = StringUtils.hasText(request.getOperator()) ? request.getOperator().trim() : assignee;
        String remark = StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "批量处理";
        HandlingStatus handlingStatus = resolveHandlingStatus(actionType);
        for (String issueNo : request.getIssueNos()) {
            String normalizedIssueNo = requireText(issueNo, "异常编号不能为空");
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
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private String requireText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
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
