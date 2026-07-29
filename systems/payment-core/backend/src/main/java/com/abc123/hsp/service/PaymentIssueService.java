package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertAcknowledgeRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import java.util.List;

/**
 * 支付交易异常中心 Service。
 */
public interface PaymentIssueService {

    /**
     * 查询支付交易异常列表。
     */
    PageResultDTO<PaymentIssueRowDTO> list(PaymentIssueQueryDTO query);

    /**
     * 查询当前筛选条件下的责任组全量统计。
     */
    List<PaymentIssueResponsibilitySummaryDTO> responsibilitySummary(PaymentIssueQueryDTO query);

    /**
     * 查询支付异常告警通知明细。
     */
    PageResultDTO<PaymentIssueAlertLogRowDTO> listAlertLogs(PaymentIssueAlertLogQueryDTO query);

    /**
     * 批量记录支付交易异常处理动作。
     */
    PageResultDTO<PaymentIssueRowDTO> batchAction(PaymentIssueActionRequestDTO request);

    /**
     * 人工确认支付异常告警回执。
     */
    PaymentIssueAlertLogRowDTO acknowledgeAlert(String alertNo, PaymentIssueAlertAcknowledgeRequestDTO request);
}
