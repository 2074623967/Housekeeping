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
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付交易异常中心业务实现。
 */
@Service
public class PaymentIssueServiceImpl implements PaymentIssueService {

    private final PaymentIssueMapper paymentIssueMapper;

    public PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper) {
        this.paymentIssueMapper = paymentIssueMapper;
    }

    @Override
    public PageResultDTO<PaymentIssueRowDTO> list(PaymentIssueQueryDTO query) {
        PaymentIssueQueryDTO normalizedQuery = normalizeQuery(query);
        return new PageResultDTO<>(
                paymentIssueMapper.findAll(normalizedQuery),
                paymentIssueMapper.count(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize()
        );
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
