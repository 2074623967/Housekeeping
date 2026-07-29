package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付交易异常中心 Mapper。
 */
public interface PaymentIssueMapper {

    /**
     * 查询支付交易异常列表。
     */
    List<PaymentIssueRowDTO> findAll(@Param("query") PaymentIssueQueryDTO query);

    /**
     * 统计支付交易异常数量。
     */
    long count(@Param("query") PaymentIssueQueryDTO query);

    /**
     * 按责任组统计当前筛选条件下的异常总量和超时量。
     */
    List<PaymentIssueResponsibilitySummaryDTO> responsibilitySummary(@Param("query") PaymentIssueQueryDTO query);

    /**
     * 查询支付异常告警明细。
     */
    List<PaymentIssueAlertLogRowDTO> findAlertLogs(@Param("query") PaymentIssueAlertLogQueryDTO query);

    /**
     * 统计支付异常告警明细数量。
     */
    long countAlertLogs(@Param("query") PaymentIssueAlertLogQueryDTO query);

    /**
     * 按告警编号查询支付异常告警明细。
     */
    PaymentIssueAlertLogRowDTO findAlertLogByAlertNo(@Param("alertNo") String alertNo);

    /**
     * 按异常编号查询当前聚合异常。
     */
    PaymentIssueRowDTO findByIssueNo(@Param("issueNo") String issueNo);

    /**
     * 写入支付交易异常处理动作。
     */
    void insertActionLog(@Param("actionNo") String actionNo,
                         @Param("issueNo") String issueNo,
                         @Param("paymentOrderId") String paymentOrderId,
                         @Param("issueType") String issueType,
                         @Param("actionType") String actionType,
                         @Param("assignee") String assignee,
                         @Param("handlingStatus") String handlingStatus,
                         @Param("handlingStatusType") String handlingStatusType,
                         @Param("actionRemark") String actionRemark,
                         @Param("operator") String operator);

    /**
     * 确认支付交易异常未回执告警。
     */
    int acknowledgePendingAlerts(@Param("issueNo") String issueNo,
                                 @Param("operator") String operator);

    /**
     * 按告警编号确认支付异常告警回执。
     */
    int acknowledgeAlertByAlertNo(@Param("alertNo") String alertNo,
                                  @Param("operator") String operator);
}
