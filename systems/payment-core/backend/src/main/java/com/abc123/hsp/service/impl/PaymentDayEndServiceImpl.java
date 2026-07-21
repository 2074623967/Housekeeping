package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.dto.PaymentDayEndAlertItemDTO;
import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
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
            overview.setLatestPaymentSuccessGapCount(0);
            overview.setLatestPaymentSuccessGapAmount("¥0.00");
            overview.setLatestPendingRefundAmount("¥0.00");
        }
        overview.setAlerts(buildAlerts(overview));
        overview.setRecentBatches(enrichRecentBatches(paymentDayEndMapper.findRecentBatches()));
        applyReconciliationReadiness(overview);
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
        entity.setChannelSuccessCount(defaultZero(paymentDayEndMapper.countChannelSuccessByDate(bizDate)));
        entity.setChannelSuccessAmount(defaultAmount(paymentDayEndMapper.sumChannelSuccessAmountByDate(bizDate)));
        entity.setInternalSuccessCount(defaultZero(paymentDayEndMapper.countInternalSuccessByDate(bizDate)));
        entity.setInternalSuccessAmount(defaultAmount(paymentDayEndMapper.sumInternalSuccessAmountByDate(bizDate)));
        entity.setPaymentSuccessGapCount(defaultZero(paymentDayEndMapper.countPaymentSuccessGapByDate(bizDate)));
        entity.setPaymentSuccessGapAmount(defaultAmount(paymentDayEndMapper.sumPaymentSuccessGapAmountByDate(bizDate)));
        entity.setRefundSuccessCount(defaultZero(paymentDayEndMapper.countSuccessRefundsByDate(bizDate)));
        entity.setRefundSuccessAmount(defaultAmount(paymentDayEndMapper.sumSuccessRefundAmountByDate(bizDate)));
        entity.setChannelAbnormalCount(defaultZero(paymentDayEndMapper.countChannelAbnormalByDate(bizDate)));
        entity.setInternalAbnormalCount(defaultZero(paymentDayEndMapper.countInternalAbnormalByDate(bizDate)));
        entity.setPendingRefundCount(defaultZero(paymentDayEndMapper.countPendingRefundByDate(bizDate)));
        entity.setPendingRefundAmount(defaultAmount(paymentDayEndMapper.sumPendingRefundAmountByDate(bizDate)));
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
            return "支付成功、渠道回调、内部事件和退款状态均已完成日终收口，可进入次日对账。";
        }
        return String.format(
                "成功差异%d笔（%s），渠道异常%d笔，内部事件异常%d笔，待收口退款%d笔（%s），需在对账与差错环节继续跟进。",
                entity.getPaymentSuccessGapCount(),
                formatAmount(entity.getPaymentSuccessGapAmount()),
                entity.getChannelAbnormalCount(),
                entity.getInternalAbnormalCount(),
                entity.getPendingRefundCount(),
                formatAmount(entity.getPendingRefundAmount())
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
                "/payment-issues?issueType=待回调未收口",
                3
        ));
        alerts.add(buildAlert(
                "INTERNAL_ABNORMAL",
                "内部事件未收口",
                defaultZero(overview.getOpenInternalAbnormalCount()),
                "存在支付成功但内部事件未正常发布的交易，需要核对事件出站和下游收口。",
                "进入支付事件出站页重发失败事件并确认下游消费结果",
                "/payment-events?publishStatus=FAILED",
                2
        ));
        alerts.add(buildAlert(
                "PENDING_REFUND",
                "退款待收口",
                defaultZero(overview.getOpenPendingRefundCount()),
                "存在退款处理中或待审核记录，需要继续推进逆向资金收口。",
                "进入退款单管理页核对失败与处理中退款单",
                "/refunds?refundStatus=PROCESSING",
                2
        ));
        return alerts;
    }

    private PaymentDayEndAlertItemDTO buildAlert(String alertType,
                                                 String alertTitle,
                                                 Integer affectedCount,
                                                 String alertMessage,
                                                 String suggestedAction,
                                                 String actionRoute,
                                                 int p1Threshold) {
        PaymentDayEndAlertItemDTO alert = new PaymentDayEndAlertItemDTO();
        int count = defaultZero(affectedCount);
        alert.setAlertType(alertType);
        alert.setAlertTitle(alertTitle);
        alert.setAffectedCount(count);
        alert.setAlertMessage(alertMessage);
        alert.setSuggestedAction(suggestedAction);
        alert.setActionRoute(actionRoute);
        if (count > 0) {
            alert.setSeverityLevel(count >= p1Threshold ? "P1" : "P2");
            alert.setSeverityLevelType(count >= p1Threshold ? "danger" : "warn");
        } else {
            alert.setSeverityLevel("P3");
            alert.setSeverityLevelType("success");
        }
        return alert;
    }

    /**
     * 为总览生成“是否可进入正式对账”的统一判断口径。
     */
    private void applyReconciliationReadiness(PaymentDayEndOverviewDTO overview) {
        int channelAbnormalCount = defaultZero(overview.getOpenChannelAbnormalCount());
        int internalAbnormalCount = defaultZero(overview.getOpenInternalAbnormalCount());
        int successGapCount = defaultZero(overview.getLatestPaymentSuccessGapCount());
        int pendingRefundCount = defaultZero(overview.getOpenPendingRefundCount());
        if (!StringUtils.hasText(overview.getLatestBizDate())) {
            overview.setReconciliationReadinessStatus("未生成日终事实");
            overview.setReconciliationReadinessType("danger");
            overview.setReconciliationReadinessSummary("尚未生成最近业务日的日终事实快照，当前不能进入正式对账。");
            overview.setReconciliationSuggestedAction("先执行支付日终处理跑批，补齐渠道成功、内部事件成功和差异事实快照。");
            overview.setReconciliationBlockingOwner("支付研发 / 财务值班");
            return;
        }
        if (channelAbnormalCount > 0 || internalAbnormalCount > 0 || successGapCount > 0) {
            overview.setReconciliationReadinessStatus("禁止进入对账");
            overview.setReconciliationReadinessType("danger");
            overview.setReconciliationReadinessSummary(String.format(
                    "最近业务日仍有渠道异常 %d 笔、内部事件异常 %d 笔、支付成功差异 %d 笔，继续进入正式对账会放大差错范围。",
                    channelAbnormalCount,
                    internalAbnormalCount,
                    successGapCount
            ));
            overview.setReconciliationSuggestedAction("优先处理渠道回调、事件出站和支付成功差异，确认主链路事实收口后再推进对账。");
            overview.setReconciliationBlockingOwner("支付研发 / 渠道运营 / 财务值班");
            return;
        }
        if (pendingRefundCount > 0) {
            overview.setReconciliationReadinessStatus("有条件进入对账");
            overview.setReconciliationReadinessType("warn");
            overview.setReconciliationReadinessSummary(String.format(
                    "主链路支付事实已收口，但仍有 %d 笔退款待收口，需要财务在对账时同步关注逆向资金差异。",
                    pendingRefundCount
            ));
            overview.setReconciliationSuggestedAction("允许进入正式对账，但需同步跟踪退款处理中、待审核和失败补退记录。");
            overview.setReconciliationBlockingOwner("退款运营 / 财务值班");
            return;
        }
        overview.setReconciliationReadinessStatus("可进入对账");
        overview.setReconciliationReadinessType("success");
        overview.setReconciliationReadinessSummary("最近业务日的渠道成功、内部事件成功和支付成功差异事实已完成前置收口，可进入正式对账。");
        overview.setReconciliationSuggestedAction("进入正式对账流程，并继续关注次日新增的退款和补单动作。");
        overview.setReconciliationBlockingOwner("财务值班");
    }

    /**
     * 为最近批次补齐对账准入结论，方便按批次复盘。
     */
    private java.util.List<PaymentDayEndBatchListItemDTO> enrichRecentBatches(java.util.List<PaymentDayEndBatchListItemDTO> batches) {
        for (PaymentDayEndBatchListItemDTO batch : batches) {
            int channelAbnormalCount = defaultZero(batch.getChannelAbnormalCount());
            int internalAbnormalCount = defaultZero(batch.getInternalAbnormalCount());
            int successGapCount = defaultZero(batch.getPaymentSuccessGapCount());
            int pendingRefundCount = defaultZero(batch.getPendingRefundCount());
            if (channelAbnormalCount > 0 || internalAbnormalCount > 0 || successGapCount > 0) {
                batch.setReconciliationReadinessStatus("BLOCKED");
                batch.setReconciliationReadinessType("danger");
                batch.setReconciliationReadinessSummary("存在主链路未收口事实，不能直接进入正式对账。");
                continue;
            }
            if (pendingRefundCount > 0) {
                batch.setReconciliationReadinessStatus("CONDITIONAL");
                batch.setReconciliationReadinessType("warn");
                batch.setReconciliationReadinessSummary("主链路已收口，但仍需跟踪逆向退款差异。");
                continue;
            }
            batch.setReconciliationReadinessStatus("READY");
            batch.setReconciliationReadinessType("success");
            batch.setReconciliationReadinessSummary("前置事实已收口，可进入正式对账。");
        }
        return batches;
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatAmount(BigDecimal amount) {
        return "¥" + defaultAmount(amount).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
    }
}
