package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.dto.PaymentDayEndAlertItemDTO;
import com.abc123.hsp.entity.PaymentDayEndBatchEntity;
import com.abc123.hsp.mapper.PaymentDayEndMapper;
import com.abc123.hsp.service.PaymentDayEndService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付日终处理服务默认实现。
 */
@Service
public class PaymentDayEndServiceImpl implements PaymentDayEndService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PaymentDayEndMapper paymentDayEndMapper;

    public PaymentDayEndServiceImpl(PaymentDayEndMapper paymentDayEndMapper) {
        this.paymentDayEndMapper = paymentDayEndMapper;
    }

    @Override
    public PaymentDayEndOverviewDTO overview() {
        PaymentDayEndOverviewDTO overview = paymentDayEndMapper.findOverviewSummary();
        if (overview == null) {
            overview = new PaymentDayEndOverviewDTO();
            overview.setTotalBatchCount(0);
            overview.setCompletedBatchCount(0);
            overview.setAbnormalBatchCount(0);
            overview.setOpenChannelAbnormalCount(0);
            overview.setOpenInternalAbnormalCount(0);
            overview.setOpenPendingRefundCount(0);
            overview.setLatestBatchStatus("未跑批");
        }
        overview.setAlerts(buildAlerts(overview));
        overview.setRecentBatches(paymentDayEndMapper.findRecentBatches());
        return overview;
    }

    @Override
    @Transactional
    public PaymentDayEndOverviewDTO run(PaymentDayEndRunRequestDTO request) {
        String bizDate = resolveBizDate(request);
        PaymentDayEndBatchEntity entity = new PaymentDayEndBatchEntity();
        entity.setBatchNo(buildBatchNo());
        entity.setBizDate(bizDate);
        entity.setRunMode(resolveRunMode(request));
        entity.setPaymentTotalCount(defaultZero(paymentDayEndMapper.countPaymentsByDate(bizDate)));
        entity.setPaymentSuccessCount(defaultZero(paymentDayEndMapper.countSuccessPaymentsByDate(bizDate)));
        entity.setPaymentSuccessAmount(defaultAmount(paymentDayEndMapper.sumSuccessPaymentAmountByDate(bizDate)));
        entity.setRefundSuccessCount(defaultZero(paymentDayEndMapper.countSuccessRefundsByDate(bizDate)));
        entity.setRefundSuccessAmount(defaultAmount(paymentDayEndMapper.sumSuccessRefundAmountByDate(bizDate)));
        entity.setChannelAbnormalCount(defaultZero(paymentDayEndMapper.countChannelAbnormalByDate(bizDate)));
        entity.setInternalAbnormalCount(defaultZero(paymentDayEndMapper.countInternalAbnormalByDate(bizDate)));
        entity.setPendingRefundCount(defaultZero(paymentDayEndMapper.countPendingRefundByDate(bizDate)));
        entity.setTriggeredBy(resolveTriggeredBy(request));
        entity.setSummaryComment(buildSummaryComment(entity));
        entity.setBatchStatus(resolveBatchStatus(entity));
        entity.setBatchStatusType(resolveBatchStatusType(entity.getBatchStatus()));
        paymentDayEndMapper.insertBatch(entity);
        return overview();
    }

    private String resolveBizDate(PaymentDayEndRunRequestDTO request) {
        String rawBizDate = request == null ? null : request.getBizDate();
        if (!StringUtils.hasText(rawBizDate)) {
            return LocalDate.now().minusDays(1).format(DATE_FORMATTER);
        }
        try {
            return LocalDate.parse(rawBizDate.trim(), DATE_FORMATTER).format(DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("业务日期格式必须为 yyyy-MM-dd");
        }
    }

    private String resolveTriggeredBy(PaymentDayEndRunRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getTriggeredBy())) {
            return "system-auto";
        }
        return request.getTriggeredBy().trim();
    }

    private String resolveRunMode(PaymentDayEndRunRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRunMode())) {
            return "MANUAL";
        }
        return request.getRunMode().trim();
    }

    private String buildSummaryComment(PaymentDayEndBatchEntity entity) {
        if (entity.getChannelAbnormalCount() == 0
                && entity.getInternalAbnormalCount() == 0
                && entity.getPendingRefundCount() == 0) {
            return "支付、渠道回调、内部事件和退款状态均已完成日终收口。";
        }
        return String.format(
                "渠道异常%d笔，内部事件异常%d笔，待收口退款%d笔，需在对账与差错环节继续跟进。",
                entity.getChannelAbnormalCount(),
                entity.getInternalAbnormalCount(),
                entity.getPendingRefundCount()
        );
    }

    private String resolveBatchStatus(PaymentDayEndBatchEntity entity) {
        if (entity.getChannelAbnormalCount() > 0
                || entity.getInternalAbnormalCount() > 0
                || entity.getPendingRefundCount() > 0) {
            return "WARNING";
        }
        return "COMPLETED";
    }

    private String resolveBatchStatusType(String batchStatus) {
        if ("WARNING".equals(batchStatus)) {
            return "warn";
        }
        return "success";
    }

    private String buildBatchNo() {
        return "DEB" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private java.util.List<PaymentDayEndAlertItemDTO> buildAlerts(PaymentDayEndOverviewDTO overview) {
        java.util.List<PaymentDayEndAlertItemDTO> alerts = new ArrayList<>();
        alerts.add(buildAlert(
                "CHANNEL_ABNORMAL",
                "渠道回调未收口",
                defaultZero(overview.getOpenChannelAbnormalCount()),
                "存在支付成功但渠道回调未成功收口的交易，需要优先核对通知日志和渠道结果。",
                "进入支付交易异常中心核对待回调与通知日志",
                "/payment-issues?issueType=待回调未收口"
        ));
        alerts.add(buildAlert(
                "INTERNAL_ABNORMAL",
                "内部事件未收口",
                defaultZero(overview.getOpenInternalAbnormalCount()),
                "存在支付成功但内部事件未正常发布的交易，需要核对事件出站和下游收口。",
                "进入支付事件出站页重发失败事件并确认下游消费结果",
                "/payment-events?publishStatus=FAILED"
        ));
        alerts.add(buildAlert(
                "PENDING_REFUND",
                "退款待收口",
                defaultZero(overview.getOpenPendingRefundCount()),
                "存在退款处理中或待审核记录，需要继续推进逆向资金收口。",
                "进入退款单管理页核对失败与处理中退款单",
                "/refunds?refundStatus=PROCESSING"
        ));
        return alerts;
    }

    private PaymentDayEndAlertItemDTO buildAlert(String alertType,
                                                 String alertTitle,
                                                 Integer affectedCount,
                                                 String alertMessage,
                                                 String suggestedAction,
                                                 String actionRoute) {
        PaymentDayEndAlertItemDTO alert = new PaymentDayEndAlertItemDTO();
        int count = defaultZero(affectedCount);
        alert.setAlertType(alertType);
        alert.setAlertTitle(alertTitle);
        alert.setAffectedCount(count);
        alert.setAlertMessage(alertMessage);
        alert.setSuggestedAction(suggestedAction);
        alert.setActionRoute(actionRoute);
        if (count > 0) {
            alert.setSeverityLevel(count > 5 ? "P1" : "P2");
            alert.setSeverityLevelType(count > 5 ? "danger" : "warn");
        } else {
            alert.setSeverityLevel("P3");
            alert.setSeverityLevelType("success");
        }
        return alert;
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
