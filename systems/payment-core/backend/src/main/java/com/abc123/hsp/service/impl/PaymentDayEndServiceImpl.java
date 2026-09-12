package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndPatrolResultDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.dto.PaymentDayEndAutoRunResultDTO;
import com.abc123.hsp.dto.PaymentDayEndAlertItemDTO;
import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.entity.PaymentDayEndBatchEntity;
import com.abc123.hsp.mapper.PaymentDayEndMapper;
import com.abc123.hsp.service.PaymentDayEndService;
import com.abc123.hsp.service.PaymentTaskLeaseService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 支付日终处理服务默认实现。
 */
@Service
public class PaymentDayEndServiceImpl implements PaymentDayEndService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final int TRIGGERED_BY_MAX_LENGTH = 64;
    private static final int DAY_END_LEASE_SECONDS = 300;
    private static final String DAY_END_LEASE_PREFIX = "PAYMENT_DAY_END:";
    private static final String RUN_MODE_AUTO = "AUTO";
    private static final String AUTO_TRIGGERED_BY = "payment-day-end-scheduler";
    private static final String PATROL_TRIGGERED_BY = "payment-day-end-patrol-scheduler";

    private final PaymentDayEndMapper paymentDayEndMapper;
    private final PaymentTaskLeaseService paymentTaskLeaseService;
    private final TransactionTemplate runTransactionTemplate;
    private final String leaseOwnerSuffix = UUID.randomUUID().toString().replace("-", "");

    public PaymentDayEndServiceImpl(PaymentDayEndMapper paymentDayEndMapper) {
        this(paymentDayEndMapper, null, (TransactionTemplate) null);
    }

    @Autowired
    public PaymentDayEndServiceImpl(PaymentDayEndMapper paymentDayEndMapper,
                                    PaymentTaskLeaseService paymentTaskLeaseService,
                                    PlatformTransactionManager transactionManager) {
        this(paymentDayEndMapper, paymentTaskLeaseService, new TransactionTemplate(transactionManager));
    }

    PaymentDayEndServiceImpl(PaymentDayEndMapper paymentDayEndMapper,
                             PaymentTaskLeaseService paymentTaskLeaseService,
                             TransactionTemplate runTransactionTemplate) {
        this.paymentDayEndMapper = paymentDayEndMapper;
        this.paymentTaskLeaseService = paymentTaskLeaseService;
        this.runTransactionTemplate = runTransactionTemplate;
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
            overview.setOpenPendingPaymentCount(0);
            overview.setOpenMissingPaymentSuccessTimeCount(0);
            overview.setOpenPendingRefundCount(0);
            overview.setLatestBatchStatus("未跑批");
            overview.setLatestPaymentSuccessGapCount(0);
            overview.setLatestPaymentSuccessGapAmount("¥0.00");
            overview.setLatestPendingPaymentAmount("¥0.00");
            overview.setLatestPendingRefundAmount("¥0.00");
        }
        overview.setAlerts(buildAlerts(overview));
        overview.setRecentBatches(enrichRecentBatches(paymentDayEndMapper.findRecentBatches()));
        applyReconciliationReadiness(overview);
        return overview;
    }

    @Override
    public PaymentDayEndOverviewDTO run(PaymentDayEndRunRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("日终处理请求不能为空");
        }
        String triggeredBy = normalizeTriggeredBy(request.getTriggeredBy());
        String runMode = normalizeManualRunMode(request.getRunMode());
        String bizDate = resolveBizDate(request);
        executeWithLease(bizDate, runMode, triggeredBy, false);
        return overview();
    }

    @Override
    public PaymentDayEndAutoRunResultDTO runAutomatically() {
        String bizDate = previousBusinessDay();
        DayEndExecutionOutcome outcome = executeWithLease(
                bizDate, RUN_MODE_AUTO, AUTO_TRIGGERED_BY, true);
        PaymentDayEndAutoRunResultDTO result = new PaymentDayEndAutoRunResultDTO();
        result.setExecuted(outcome.batch != null);
        result.setSkipReason(outcome.skipReason);
        result.setBizDate(bizDate);
        result.setBatchNo(outcome.batch == null ? null : outcome.batch.getBatchNo());
        result.setBatchStatus(outcome.batch == null ? null : outcome.batch.getBatchStatus());
        return result;
    }

    @Override
    public PaymentDayEndPatrolResultDTO inspectPreviousBusinessDay() {
        String bizDate = previousBusinessDay();
        if (paymentTaskLeaseService == null) {
            return inspectBusinessDay(bizDate);
        }
        String leaseOwner = PATROL_TRIGGERED_BY + ":" + leaseOwnerSuffix;
        PaymentTaskLeaseService.Lease lease = paymentTaskLeaseService.tryAcquire(
                DAY_END_LEASE_PREFIX + bizDate, leaseOwner, DAY_END_LEASE_SECONDS);
        if (lease == null) {
            PaymentDayEndPatrolResultDTO result = new PaymentDayEndPatrolResultDTO();
            result.setInspected(false);
            result.setSkipReason(PaymentDayEndAutoRunResultDTO.SKIP_REASON_LEASE_BUSY);
            result.setBizDate(bizDate);
            return result;
        }
        try (PaymentTaskLeaseService.Lease ignored = lease) {
            return inspectBusinessDay(bizDate);
        }
    }

    private PaymentDayEndPatrolResultDTO inspectBusinessDay(String bizDate) {
        PaymentDayEndPatrolResultDTO result = new PaymentDayEndPatrolResultDTO();
        result.setInspected(true);
        result.setBizDate(bizDate);
        result.setBatchCount(defaultZero(paymentDayEndMapper.countBatchesByDate(bizDate)));
        result.setAutoBatchCount(defaultZero(paymentDayEndMapper.countAutoBatchesByDate(bizDate)));
        return result;
    }

    private String previousBusinessDay() {
        return LocalDate.now(BUSINESS_ZONE_ID).minusDays(1).format(DATE_FORMATTER);
    }

    private DayEndExecutionOutcome executeWithLease(String bizDate,
                                                    String runMode,
                                                    String triggeredBy,
                                                    boolean skipWhenLeaseBusy) {
        if (paymentTaskLeaseService == null) {
            return executeRun(bizDate, runMode, triggeredBy);
        }
        String leaseOwner = triggeredBy + ":" + leaseOwnerSuffix;
        PaymentTaskLeaseService.Lease lease = paymentTaskLeaseService.tryAcquire(
                DAY_END_LEASE_PREFIX + bizDate, leaseOwner, DAY_END_LEASE_SECONDS);
        if (lease == null) {
            if (skipWhenLeaseBusy) {
                return DayEndExecutionOutcome.skipped(
                        PaymentDayEndAutoRunResultDTO.SKIP_REASON_LEASE_BUSY);
            }
            throw new IllegalArgumentException("该业务日正在执行日终处理，请稍后重试");
        }
        try (PaymentTaskLeaseService.Lease ignored = lease) {
            return runTransactionTemplate.execute(
                    status -> executeRun(bizDate, runMode, triggeredBy));
        }
    }

    private DayEndExecutionOutcome executeRun(String bizDate, String runMode, String triggeredBy) {
        if (!shouldExecuteForBizDate(bizDate, runMode)) {
            return DayEndExecutionOutcome.skipped(
                    PaymentDayEndAutoRunResultDTO.SKIP_REASON_EXISTING_BATCH);
        }
        PaymentDayEndBatchEntity entity = new PaymentDayEndBatchEntity();
        entity.setBatchNo(buildBatchNo());
        entity.setBizDate(bizDate);
        entity.setRunMode(runMode);
        entity.setPaymentTotalCount(defaultZero(paymentDayEndMapper.countPaymentsByDate(bizDate)));
        entity.setPaymentSuccessCount(defaultZero(paymentDayEndMapper.countSuccessPaymentsByDate(bizDate)));
        entity.setPaymentSuccessAmount(defaultAmount(paymentDayEndMapper.sumSuccessPaymentAmountByDate(bizDate)));
        entity.setChannelSuccessCount(defaultZero(paymentDayEndMapper.countChannelSuccessByDate(bizDate)));
        entity.setChannelSuccessAmount(defaultAmount(paymentDayEndMapper.sumChannelSuccessAmountByDate(bizDate)));
        entity.setInternalSuccessCount(defaultZero(paymentDayEndMapper.countInternalSuccessByDate(bizDate)));
        entity.setInternalSuccessAmount(defaultAmount(paymentDayEndMapper.sumInternalSuccessAmountByDate(bizDate)));
        entity.setPaymentSuccessGapCount(defaultZero(paymentDayEndMapper.countPaymentSuccessGapByDate(bizDate)));
        entity.setPaymentSuccessGapAmount(defaultAmount(paymentDayEndMapper.sumPaymentSuccessGapAmountByDate(bizDate)));
        entity.setPendingPaymentCount(defaultZero(paymentDayEndMapper.countPendingPaymentsByDate(bizDate)));
        entity.setPendingPaymentAmount(defaultAmount(paymentDayEndMapper.sumPendingPaymentAmountByDate(bizDate)));
        entity.setMissingPaymentSuccessTimeCount(defaultZero(
                paymentDayEndMapper.countMissingPaymentSuccessTimeByDate(bizDate)));
        entity.setRefundSuccessCount(defaultZero(paymentDayEndMapper.countSuccessRefundsByDate(bizDate)));
        entity.setRefundSuccessAmount(defaultAmount(paymentDayEndMapper.sumSuccessRefundAmountByDate(bizDate)));
        entity.setChannelAbnormalCount(defaultZero(paymentDayEndMapper.countChannelAbnormalByDate(bizDate)));
        entity.setInternalAbnormalCount(defaultZero(paymentDayEndMapper.countInternalAbnormalByDate(bizDate)));
        entity.setPendingRefundCount(defaultZero(paymentDayEndMapper.countPendingRefundByDate(bizDate)));
        entity.setPendingRefundAmount(defaultAmount(paymentDayEndMapper.sumPendingRefundAmountByDate(bizDate)));
        entity.setTriggeredBy(triggeredBy);
        entity.setSummaryComment(buildSummaryComment(entity));
        entity.setBatchStatus(resolveBatchStatus(entity));
        entity.setBatchStatusType(resolveBatchStatusType(entity.getBatchStatus()));
        paymentDayEndMapper.insertBatch(entity);
        return DayEndExecutionOutcome.executed(entity);
    }

    private boolean shouldExecuteForBizDate(String bizDate, String runMode) {
        int existingBatchCount = defaultZero(paymentDayEndMapper.countBatchesByDate(bizDate));
        if (RUN_MODE_AUTO.equals(runMode)) {
            return existingBatchCount == 0;
        }
        if (existingBatchCount > 0 && "MANUAL".equals(runMode)) {
            throw new IllegalArgumentException("该业务日已有日终批次，请使用RERUN重检");
        }
        if (existingBatchCount == 0 && "RERUN".equals(runMode)) {
            throw new IllegalArgumentException("该业务日尚无日终批次，首次执行请使用MANUAL");
        }
        return true;
    }

    @Override
    public String exportBatchesCsv() {
        java.util.List<PaymentDayEndBatchListItemDTO> batches =
                enrichRecentBatches(CsvExportRowLimiter.requireWithinLimit(
                        paymentDayEndMapper.findAllBatchesForExport()));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("批次号,业务日期,运行方式,批次状态,支付总单量,支付成功单量,支付成功金额,渠道成功单量,渠道成功金额,内部成功单量,内部成功金额,成功差异单量,成功差异金额,待收口支付,待收口支付金额,成功时间缺失,退款成功单量,退款成功金额,渠道异常,内部异常,待收口退款,待收口退款金额,对账准入,准入说明,备注,触发人,创建时间,完成时间\n");
        for (PaymentDayEndBatchListItemDTO batch : batches) {
            builder.append(csvCell(batch.getBatchNo())).append(',')
                    .append(csvCell(batch.getBizDate())).append(',')
                    .append(csvCell(batch.getRunMode())).append(',')
                    .append(csvCell(batch.getBatchStatus())).append(',')
                    .append(csvCell(valueOf(batch.getPaymentTotalCount()))).append(',')
                    .append(csvCell(valueOf(batch.getPaymentSuccessCount()))).append(',')
                    .append(csvCell(batch.getPaymentSuccessAmount())).append(',')
                    .append(csvCell(valueOf(batch.getChannelSuccessCount()))).append(',')
                    .append(csvCell(batch.getChannelSuccessAmount())).append(',')
                    .append(csvCell(valueOf(batch.getInternalSuccessCount()))).append(',')
                    .append(csvCell(batch.getInternalSuccessAmount())).append(',')
                    .append(csvCell(valueOf(batch.getPaymentSuccessGapCount()))).append(',')
                    .append(csvCell(batch.getPaymentSuccessGapAmount())).append(',')
                    .append(csvCell(valueOf(batch.getPendingPaymentCount()))).append(',')
                    .append(csvCell(batch.getPendingPaymentAmount())).append(',')
                    .append(csvCell(valueOf(batch.getMissingPaymentSuccessTimeCount()))).append(',')
                    .append(csvCell(valueOf(batch.getRefundSuccessCount()))).append(',')
                    .append(csvCell(batch.getRefundSuccessAmount())).append(',')
                    .append(csvCell(valueOf(batch.getChannelAbnormalCount()))).append(',')
                    .append(csvCell(valueOf(batch.getInternalAbnormalCount()))).append(',')
                    .append(csvCell(valueOf(batch.getPendingRefundCount()))).append(',')
                    .append(csvCell(batch.getPendingRefundAmount())).append(',')
                    .append(csvCell(batch.getReconciliationReadinessStatus())).append(',')
                    .append(csvCell(batch.getReconciliationReadinessSummary())).append(',')
                    .append(csvCell(batch.getSummaryComment())).append(',')
                    .append(csvCell(batch.getTriggeredBy())).append(',')
                    .append(csvCell(batch.getCreatedAt())).append(',')
                    .append(csvCell(batch.getCompletedAt())).append('\n');
        }
        return builder.toString();
    }

    private String resolveBizDate(PaymentDayEndRunRequestDTO request) {
        String rawBizDate = request == null ? null : request.getBizDate();
        if (!StringUtils.hasText(rawBizDate)) {
            return LocalDate.now(BUSINESS_ZONE_ID).minusDays(1).format(DATE_FORMATTER);
        }
        try {
            LocalDate bizDate = LocalDate.parse(rawBizDate.trim(), DATE_FORMATTER);
            if (!bizDate.isBefore(LocalDate.now(BUSINESS_ZONE_ID))) {
                throw new IllegalArgumentException("业务日期必须早于上海当前日期");
            }
            return bizDate.format(DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("业务日期格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeTriggeredBy(String triggeredBy) {
        if (!StringUtils.hasText(triggeredBy)) {
            throw new IllegalArgumentException("日终处理触发人不能为空");
        }
        for (int index = 0; index < triggeredBy.length(); index++) {
            if (Character.isISOControl(triggeredBy.charAt(index))) {
                throw new IllegalArgumentException("日终处理触发人不能包含控制字符");
            }
        }
        String normalized = triggeredBy.trim();
        if (normalized.length() > TRIGGERED_BY_MAX_LENGTH) {
            throw new IllegalArgumentException("日终处理触发人不能超过64个字符");
        }
        return normalized;
    }

    private String normalizeManualRunMode(String runMode) {
        if (!StringUtils.hasText(runMode)) {
            return "MANUAL";
        }
        String normalized = runMode.trim().toUpperCase(Locale.ROOT);
        if (!"MANUAL".equals(normalized) && !"RERUN".equals(normalized)) {
            throw new IllegalArgumentException("手工日终运行方式只支持MANUAL或RERUN");
        }
        return normalized;
    }

    private String buildSummaryComment(PaymentDayEndBatchEntity entity) {
        if (entity.getPaymentSuccessGapCount() == 0
                && entity.getPendingPaymentCount() == 0
                && entity.getMissingPaymentSuccessTimeCount() == 0
                && entity.getChannelAbnormalCount() == 0
                && entity.getInternalAbnormalCount() == 0
                && entity.getPendingRefundCount() == 0) {
            return "支付状态、支付成功、渠道回调、内部事件和退款状态均已完成日终收口，可进入次日对账。";
        }
        return String.format(
                "成功差异%d笔（%s），待收口支付%d笔（%s），成功时间缺失%d笔，渠道异常%d笔，内部事件异常%d笔，待收口退款%d笔（%s），需在对账与差错环节继续跟进。",
                entity.getPaymentSuccessGapCount(),
                formatAmount(entity.getPaymentSuccessGapAmount()),
                entity.getPendingPaymentCount(),
                formatAmount(entity.getPendingPaymentAmount()),
                entity.getMissingPaymentSuccessTimeCount(),
                entity.getChannelAbnormalCount(),
                entity.getInternalAbnormalCount(),
                entity.getPendingRefundCount(),
                formatAmount(entity.getPendingRefundAmount())
        );
    }

    private String resolveBatchStatus(PaymentDayEndBatchEntity entity) {
        if (entity.getPaymentSuccessGapCount() > 0
                || entity.getPendingPaymentCount() > 0
                || entity.getMissingPaymentSuccessTimeCount() > 0
                || entity.getChannelAbnormalCount() > 0
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
                "PENDING_PAYMENT",
                "支付待收口",
                defaultZero(overview.getOpenPendingPaymentCount()),
                "存在尚未完成支付、等待渠道结果或等待风控复核的交易，当前不能进入正式对账。",
                "进入支付单管理页处理日终待收口支付",
                "/payments?status=DAY_END_PENDING",
                1
        ));
        alerts.add(buildAlert(
                "PAYMENT_SUCCESS_TIME_MISSING",
                "支付成功时间缺失",
                defaultZero(overview.getOpenMissingPaymentSuccessTimeCount()),
                "存在已成功但缺少成功时间的支付单，无法可信归属支付账期，当前不能进入正式对账。",
                "进入支付单管理页核对成功时间缺失记录",
                "/payments?status=SUCCESS_TIME_MISSING",
                1
        ));
        alerts.add(buildAlert(
                "PAYMENT_SUCCESS_GAP",
                "支付成功差异",
                defaultZero(overview.getLatestPaymentSuccessGapCount()),
                "存在支付成功但渠道回调、内部事件或订单应收仍不一致的交易，当前不能进入正式对账。",
                "进入支付交易异常中心联查回调、事件和超额成功收款",
                "/payment-issues",
                1
        ));
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
                "/payment-events?publishStatus=FAILED_OR_DEAD_LETTER",
                2
        ));
        alerts.add(buildAlert(
                "PENDING_REFUND",
                "退款待收口",
                defaultZero(overview.getOpenPendingRefundCount()),
                "存在退款处理中或待审核记录，需要继续推进逆向资金收口。",
                "进入退款单管理页核对待审核与处理中退款单",
                "/refunds?refundStatus=PENDING",
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
        Integer pendingPaymentFact = overview.getOpenPendingPaymentCount();
        Integer missingSuccessTimeFact = overview.getOpenMissingPaymentSuccessTimeCount();
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
        if (pendingPaymentFact == null || missingSuccessTimeFact == null) {
            overview.setReconciliationReadinessStatus("日终事实不完整");
            overview.setReconciliationReadinessType("danger");
            overview.setReconciliationReadinessSummary("最近批次未采集待收口支付或支付成功时间完整性事实，不能据此判断可以进入正式对账。");
            overview.setReconciliationSuggestedAction("使用 RERUN 重检该业务日，生成包含待收口支付和成功时间完整性的新事实快照。");
            overview.setReconciliationBlockingOwner("支付研发 / 财务值班");
            return;
        }
        int pendingPaymentCount = defaultZero(pendingPaymentFact);
        int missingSuccessTimeCount = defaultZero(missingSuccessTimeFact);
        if (pendingPaymentCount > 0 || missingSuccessTimeCount > 0
                || channelAbnormalCount > 0 || internalAbnormalCount > 0 || successGapCount > 0) {
            overview.setReconciliationReadinessStatus("禁止进入对账");
            overview.setReconciliationReadinessType("danger");
            overview.setReconciliationReadinessSummary(String.format(
                    "最近业务日仍有待收口支付 %d 笔、成功时间缺失 %d 笔、渠道异常 %d 笔、内部事件异常 %d 笔、支付成功差异 %d 笔，继续进入正式对账会放大差错范围。",
                    pendingPaymentCount,
                    missingSuccessTimeCount,
                    channelAbnormalCount,
                    internalAbnormalCount,
                    successGapCount
            ));
            overview.setReconciliationSuggestedAction("优先处理待支付、待风控复核、成功时间缺失、渠道回调、事件出站和支付成功差异，确认主链路事实收口后再推进对账。");
            overview.setReconciliationBlockingOwner("支付研发 / 风控运营 / 渠道运营 / 财务值班");
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
            if (batch.getPendingPaymentCount() == null
                    || batch.getMissingPaymentSuccessTimeCount() == null) {
                batch.setReconciliationReadinessStatus("UNKNOWN");
                batch.setReconciliationReadinessType("danger");
                batch.setReconciliationReadinessSummary("历史批次未采集待收口支付或成功时间完整性事实，需重检后判断。");
                continue;
            }
            int pendingPaymentCount = defaultZero(batch.getPendingPaymentCount());
            int missingSuccessTimeCount = defaultZero(batch.getMissingPaymentSuccessTimeCount());
            int channelAbnormalCount = defaultZero(batch.getChannelAbnormalCount());
            int internalAbnormalCount = defaultZero(batch.getInternalAbnormalCount());
            int successGapCount = defaultZero(batch.getPaymentSuccessGapCount());
            int pendingRefundCount = defaultZero(batch.getPendingRefundCount());
            if (pendingPaymentCount > 0 || missingSuccessTimeCount > 0
                    || channelAbnormalCount > 0 || internalAbnormalCount > 0 || successGapCount > 0) {
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

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private String valueOf(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class DayEndExecutionOutcome {

        private final PaymentDayEndBatchEntity batch;
        private final String skipReason;

        private DayEndExecutionOutcome(PaymentDayEndBatchEntity batch, String skipReason) {
            this.batch = batch;
            this.skipReason = skipReason;
        }

        private static DayEndExecutionOutcome executed(PaymentDayEndBatchEntity batch) {
            return new DayEndExecutionOutcome(batch, null);
        }

        private static DayEndExecutionOutcome skipped(String skipReason) {
            return new DayEndExecutionOutcome(null, skipReason);
        }
    }
}
