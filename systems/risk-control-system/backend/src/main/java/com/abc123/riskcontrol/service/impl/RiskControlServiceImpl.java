package com.abc123.riskcontrol.service.impl;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.DashboardMetricDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.PageResultDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;
import com.abc123.riskcontrol.dto.RiskOpsSystemControlDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.RiskSummaryDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import com.abc123.riskcontrol.entity.BlocklistEntity;
import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.LimitRuleEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import com.abc123.riskcontrol.entity.RiskPolicyEntity;
import com.abc123.riskcontrol.service.RiskControlService;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 风控业务编排实现。
 */
@Service
public class RiskControlServiceImpl implements RiskControlService {

    private static final String CONTROL_BIG_AMOUNT_REVIEW = "CONTROL_BIG_AMOUNT_REVIEW";

    private final RiskControlDao dao;
    private final RiskOpsConfigService riskOpsConfigService;

    public RiskControlServiceImpl(RiskControlDao dao, RiskOpsConfigService riskOpsConfigService) {
        this.dao = dao;
        this.riskOpsConfigService = riskOpsConfigService;
    }

    @Override
    public RiskSummaryDTO summary() {
        RiskSummaryDTO summary = new RiskSummaryDTO();
        summary.setMetrics(Arrays.asList(
                metric("启用风险策略", String.valueOf(dao.countEnabledPolicies()), "danger", "策略"),
                metric("启用限额规则", String.valueOf(dao.countEnabledLimits()), "warn", "限额"),
                metric("待审复核单", String.valueOf(dao.countPendingReviews()), "info", "复核"),
                metric("累计拦截事件", String.valueOf(dao.countInterceptedEvents()), "success", "拦截")
        ));
        summary.setHighlights(Arrays.asList(
                "风险策略负责高危支付、设备异常、身份异常等基础拦截策略。",
                "限额规则与运营配置域分工：配置域维护业务参数，风控域执行场景限额和拦截。",
                "人工复核单承接高风险交易的人工判定，后续接 payment-core 与 settlement-system。"
        ));
        return summary;
    }

    @Override
    public PageResultDTO<RiskPolicyDTO> policies() {
        return page(dao.findPolicies());
    }

    @Override
    public PageResultDTO<LimitRuleDTO> limitRules() {
        return page(dao.findLimitRules());
    }

    @Override
    public PageResultDTO<BlocklistDTO> blocklists() {
        return page(dao.findBlocklists());
    }

    @Override
    public PageResultDTO<InterceptEventDTO> interceptEvents() {
        return page(dao.findInterceptEvents());
    }

    @Override
    public PageResultDTO<ReviewOrderDTO> reviewOrders() {
        return page(dao.findReviewOrders());
    }

    @Override
    public PageResultDTO<MonitorRuleDTO> monitorRules() {
        return page(dao.findMonitorRules());
    }

    @Override
    @Transactional
    public RiskDecisionResultDTO evaluatePaymentDecision(RiskDecisionRequestDTO request) {
        validateDecisionRequest(request);
        ReviewOrderEntity latestReviewOrder = dao.findLatestReviewOrderByBusinessNo(request.getBusinessNo().trim());
        if (latestReviewOrder != null) {
            if ("APPROVED".equalsIgnoreCase(latestReviewOrder.getStatus())) {
                return passResult(
                        "人工复核已通过，允许进入支付主链路",
                        latestReviewOrder.getReviewNo(),
                        latestReviewOrder.getRiskTag());
            }
            if ("REJECTED".equalsIgnoreCase(latestReviewOrder.getStatus())) {
                return rejectResult(
                        "人工复核已拒绝，当前支付单不允许继续提交",
                        latestReviewOrder.getReviewNo(),
                        latestReviewOrder.getRiskTag());
            }
            if ("PENDING".equalsIgnoreCase(latestReviewOrder.getStatus())) {
                return reviewResult(
                        "存在待处理人工复核单，请先完成审核",
                        latestReviewOrder.getReviewNo(),
                        latestReviewOrder.getRiskTag(),
                        null);
            }
        }

        DecisionHit blocklistHit = evaluateBlocklist(request);
        if (blocklistHit != null) {
            return buildDecisionResult(request, blocklistHit);
        }

        DecisionHit opsControlHit = evaluateOpsSystemControl(request);
        if (opsControlHit != null) {
            return buildDecisionResult(request, opsControlHit);
        }

        DecisionHit limitHit = evaluateLimitRule(request);
        if (limitHit != null) {
            return buildDecisionResult(request, limitHit);
        }

        DecisionHit policyHit = evaluatePolicy(request);
        if (policyHit != null) {
            return buildDecisionResult(request, policyHit);
        }

        return passResult("未命中黑名单、限额和高危策略，允许进入支付主链路", null, null);
    }

    @Override
    @Transactional
    public RiskSummaryDTO togglePolicy(ToggleRequestDTO request) {
        if (dao.updatePolicyStatus(requiredCode(request, "风险策略"), status(request), statusType(request)) != 1) {
            throw new BusinessException("风险策略不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleLimitRule(ToggleRequestDTO request) {
        if (dao.updateLimitRuleStatus(requiredCode(request, "限额规则"), status(request), statusType(request)) != 1) {
            throw new BusinessException("限额规则不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleBlocklist(ToggleRequestDTO request) {
        if (dao.updateBlocklistStatus(requiredCode(request, "黑名单"), status(request), statusType(request)) != 1) {
            throw new BusinessException("黑名单不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public RiskSummaryDTO toggleMonitorRule(ToggleRequestDTO request) {
        if (dao.updateMonitorRuleStatus(requiredCode(request, "监控规则"), status(request), statusType(request)) != 1) {
            throw new BusinessException("监控规则不存在");
        }
        return summary();
    }

    @Override
    @Transactional
    public PageResultDTO<ReviewOrderDTO> reviewAction(RiskReviewActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getReviewNo())) {
            throw new BusinessException("复核单号不能为空");
        }
        if (!StringUtils.hasText(request.getAction())) {
            throw new BusinessException("审核动作不能为空");
        }
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("审核动作仅支持 APPROVE 或 REJECT");
        }
        String status = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        String statusType = "APPROVE".equals(action) ? "success" : "danger";
        if (dao.updateReviewOrder(request.getReviewNo().trim(), status, statusType, "风险审核员A") != 1) {
            throw new BusinessException("复核单不存在");
        }
        return reviewOrders();
    }

    private DecisionHit evaluateBlocklist(RiskDecisionRequestDTO request) {
        List<BlocklistEntity> blocklists = dao.findEnabledBlocklists();
        for (BlocklistEntity entity : blocklists) {
            if (!subjectMatched(entity, request)) {
                continue;
            }
            String action = normalizeAction(entity.getActionType());
            return new DecisionHit(
                    action,
                    entity.getBlockCode(),
                    "高",
                    entity.getReason(),
                    "REVIEW".equals(action) ? "命中黑名单，需人工复核后再决定是否放行" : "命中黑名单，已直接拦截支付提交",
                    buildReviewItem(entity, request));
        }
        return null;
    }

    private DecisionHit evaluateLimitRule(RiskDecisionRequestDTO request) {
        String sceneCode = StringUtils.hasText(request.getSceneCode()) ? request.getSceneCode().trim() : "PAY_CONSUME";
        for (LimitRuleEntity entity : dao.findEnabledLimitRulesByScene(sceneCode)) {
            BigDecimal limitValue = parseAmount(entity.getLimitValue());
            if (limitValue.compareTo(BigDecimal.ZERO) <= 0 || request.getAmount().compareTo(limitValue) <= 0) {
                continue;
            }
            return new DecisionHit(
                    "REVIEW",
                    entity.getRuleCode(),
                    "中",
                    entity.getRuleName() + "超阈值",
                    "交易金额超过风控限额，需人工复核后决定是否放行",
                    buildReviewItemFromLimit(entity, request));
        }
        return null;
    }

    private DecisionHit evaluateOpsSystemControl(RiskDecisionRequestDTO request) {
        RiskOpsConfigSnapshotDTO snapshot = riskOpsConfigService.loadEffectiveSnapshot(
                safeTrim(request.getPayScene()),
                safeTrim(request.getSceneCode()),
                safeTrim(request.getTerminal()));
        if (snapshot == null || snapshot.getEnabledSystemControls() == null) {
            return null;
        }
        for (RiskOpsSystemControlDTO control : snapshot.getEnabledSystemControls()) {
            if (!CONTROL_BIG_AMOUNT_REVIEW.equalsIgnoreCase(control.getControlCode())) {
                continue;
            }
            BigDecimal threshold = parseAmount(control.getControlValue());
            if (threshold.compareTo(BigDecimal.ZERO) <= 0 || request.getAmount().compareTo(threshold) <= 0) {
                return null;
            }
            return new DecisionHit(
                    "REVIEW",
                    control.getControlCode(),
                    defaultText(control.getRiskLevel()),
                    control.getControlName(),
                    "命中运营配置下发的大额支付人工复核阈值，需先转人工审核",
                    buildReviewItemFromOpsControl(control, request, threshold));
        }
        return null;
    }

    private DecisionHit evaluatePolicy(RiskDecisionRequestDTO request) {
        for (RiskPolicyEntity entity : dao.findEnabledPoliciesForDecision()) {
            if (!policyMatched(entity, request)) {
                continue;
            }
            String action = normalizeAction(entity.getHitAction());
            return new DecisionHit(
                    action,
                    entity.getPolicyCode(),
                    entity.getRiskLevel(),
                    entity.getPolicyName(),
                    "REVIEW".equals(action) ? "命中高危策略，需人工复核" : "命中高危策略，已直接拦截",
                    buildReviewItemFromPolicy(entity, request));
        }
        return null;
    }

    private boolean subjectMatched(BlocklistEntity entity, RiskDecisionRequestDTO request) {
        String subjectType = safeUpper(entity.getSubjectType());
        String subjectValue = safeTrim(entity.getSubjectValue());
        if ("DEVICE".equals(subjectType)) {
            return subjectValue.equalsIgnoreCase(safeTrim(request.getClientDeviceId()));
        }
        if ("PHONE".equals(subjectType)) {
            return subjectValue.equalsIgnoreCase(safeTrim(request.getPayerPhone()));
        }
        if ("MERCHANT".equals(subjectType)) {
            return subjectValue.equalsIgnoreCase(safeTrim(request.getMerchantNo()));
        }
        if ("IP".equals(subjectType)) {
            return subjectValue.equalsIgnoreCase(safeTrim(request.getClientIp()));
        }
        return false;
    }

    private boolean policyMatched(RiskPolicyEntity entity, RiskDecisionRequestDTO request) {
        if ("POLICY_DEVICE_RISK".equalsIgnoreCase(entity.getPolicyCode())) {
            return safeUpper(request.getClientDeviceId()).contains("RISK")
                    || safeTrim(request.getClientIp()).startsWith("10.9.");
        }
        if ("POLICY_REALNAME_WARN".equalsIgnoreCase(entity.getPolicyCode())) {
            return request.getAmount().compareTo(new BigDecimal("2000")) >= 0
                    && StringUtils.hasText(request.getPayerPhone())
                    && request.getPayerPhone().trim().endsWith("8888");
        }
        if ("POLICY_PAYOUT_REGULATE".equalsIgnoreCase(entity.getPolicyCode())) {
            return safeUpper(request.getPayScene()).contains("REGULATE");
        }
        return false;
    }

    private RiskDecisionResultDTO buildDecisionResult(RiskDecisionRequestDTO request, DecisionHit hit) {
        if ("REVIEW".equals(hit.action)) {
            ReviewOrderEntity reviewOrder = buildOrReuseReviewOrder(request, hit);
            InterceptEventEntity interceptEvent = buildOrReuseInterceptEvent(request, hit, "转人工复核");
            return reviewResult(hit.message, reviewOrder.getReviewNo(), hit.riskTag, interceptEvent.getEventNo());
        }
        if ("REJECT".equals(hit.action)) {
            return rejectResult(hit.message, null, hit.riskTag);
        }
        InterceptEventEntity interceptEvent = buildOrReuseInterceptEvent(request, hit, "已拦截");
        RiskDecisionResultDTO result = new RiskDecisionResultDTO();
        result.setDecision("INTERCEPT");
        result.setDecisionType("danger");
        result.setHitCode(hit.hitCode);
        result.setRiskTag(hit.riskTag);
        result.setEventNo(interceptEvent.getEventNo());
        result.setMessage(hit.message);
        return result;
    }

    private ReviewOrderEntity buildOrReuseReviewOrder(RiskDecisionRequestDTO request, DecisionHit hit) {
        ReviewOrderEntity latestReviewOrder = dao.findLatestReviewOrderByBusinessNo(request.getBusinessNo().trim());
        if (latestReviewOrder != null && "PENDING".equalsIgnoreCase(latestReviewOrder.getStatus())) {
            return latestReviewOrder;
        }
        ReviewOrderEntity entity = new ReviewOrderEntity();
        entity.setReviewNo("REVIEW-" + System.currentTimeMillis());
        entity.setBusinessNo(request.getBusinessNo().trim());
        entity.setRiskTag(hit.riskTag);
        entity.setReviewItem(hit.reviewItem);
        entity.setStatus("PENDING");
        entity.setStatusType("warn");
        dao.insertReviewOrder(entity);
        return entity;
    }

    private InterceptEventEntity buildOrReuseInterceptEvent(
            RiskDecisionRequestDTO request,
            DecisionHit hit,
            String decisionResult) {
        InterceptEventEntity existing = dao.findLatestInterceptEvent(request.getBusinessNo().trim(), hit.hitCode, decisionResult);
        if (existing != null) {
            return existing;
        }
        InterceptEventEntity entity = new InterceptEventEntity();
        entity.setEventNo("RISK-EVT-" + System.currentTimeMillis());
        entity.setPaymentOrderId(request.getBusinessNo().trim());
        entity.setHitPolicy(hit.hitCode);
        entity.setRiskLevel(hit.riskLevel);
        entity.setDecisionResult(decisionResult);
        entity.setSourceSystem(StringUtils.hasText(request.getSourceSystem()) ? request.getSourceSystem().trim() : "payment-core");
        dao.insertInterceptEvent(entity);
        return entity;
    }

    private void validateDecisionRequest(RiskDecisionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getBusinessNo())) {
            throw new BusinessException("业务单号不能为空");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("交易金额不能为空且必须大于零");
        }
    }

    private RiskDecisionResultDTO passResult(String message, String reviewNo, String riskTag) {
        RiskDecisionResultDTO result = new RiskDecisionResultDTO();
        result.setDecision("PASS");
        result.setDecisionType("success");
        result.setReviewNo(reviewNo);
        result.setRiskTag(riskTag);
        result.setMessage(message);
        return result;
    }

    private RiskDecisionResultDTO rejectResult(String message, String reviewNo, String riskTag) {
        RiskDecisionResultDTO result = new RiskDecisionResultDTO();
        result.setDecision("REJECT");
        result.setDecisionType("danger");
        result.setReviewNo(reviewNo);
        result.setRiskTag(riskTag);
        result.setMessage(message);
        return result;
    }

    private RiskDecisionResultDTO reviewResult(String message, String reviewNo, String riskTag, String eventNo) {
        RiskDecisionResultDTO result = new RiskDecisionResultDTO();
        result.setDecision("REVIEW");
        result.setDecisionType("warn");
        result.setReviewNo(reviewNo);
        result.setRiskTag(riskTag);
        result.setEventNo(eventNo);
        result.setMessage(message);
        return result;
    }

    private DashboardMetricDTO metric(String title, String value, String badgeType, String badgeText) {
        DashboardMetricDTO metric = new DashboardMetricDTO();
        metric.setTitle(title);
        metric.setValue(value);
        metric.setBadgeType(badgeType);
        metric.setBadgeText(badgeText);
        return metric;
    }

    private <T> PageResultDTO<T> page(List<T> records) {
        return new PageResultDTO<>(records, records.size(), 1, Math.max(records.size(), 1));
    }

    private String requiredCode(ToggleRequestDTO request, String label) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new BusinessException(label + "编码不能为空");
        }
        return request.getConfigCode().trim();
    }

    private String status(ToggleRequestDTO request) {
        return Boolean.TRUE.equals(request.getEnabled()) ? "ENABLED" : "DISABLED";
    }

    private String statusType(ToggleRequestDTO request) {
        return Boolean.TRUE.equals(request.getEnabled()) ? "success" : "danger";
    }

    private BigDecimal parseAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text.trim());
    }

    private String normalizeAction(String action) {
        String normalized = safeUpper(action);
        if ("REVIEW".equals(normalized) || "INTERCEPT".equals(normalized) || "REJECT".equals(normalized)) {
            return normalized;
        }
        return "INTERCEPT";
    }

    private String buildReviewItem(BlocklistEntity entity, RiskDecisionRequestDTO request) {
        return entity.getReason() + "，请核验业务单号 " + request.getBusinessNo() + " 的提交背景和付款人身份";
    }

    private String buildReviewItemFromLimit(LimitRuleEntity entity, RiskDecisionRequestDTO request) {
        return entity.getRuleName() + "，本次金额 " + request.getAmount().toPlainString()
                + " 超过阈值 " + entity.getLimitValue() + "，请确认支付合理性";
    }

    private String buildReviewItemFromPolicy(RiskPolicyEntity entity, RiskDecisionRequestDTO request) {
        return entity.getPolicyName() + "，请核验业务单号 " + request.getBusinessNo()
                + "、终端 " + defaultText(request.getTerminal()) + " 和渠道 " + defaultText(request.getChannelCode());
    }

    private String buildReviewItemFromOpsControl(
            RiskOpsSystemControlDTO control,
            RiskDecisionRequestDTO request,
            BigDecimal threshold) {
        return defaultText(control.getControlName()) + "，本次金额 " + request.getAmount().toPlainString()
                + " 超过运营配置阈值 " + threshold.toPlainString() + "，请确认大额支付真实性与业务合理性";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeUpper(String value) {
        return safeTrim(value).toUpperCase(Locale.ROOT);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private static final class DecisionHit {

        private final String action;
        private final String hitCode;
        private final String riskLevel;
        private final String riskTag;
        private final String message;
        private final String reviewItem;

        private DecisionHit(String action,
                            String hitCode,
                            String riskLevel,
                            String riskTag,
                            String message,
                            String reviewItem) {
            this.action = action;
            this.hitCode = hitCode;
            this.riskLevel = riskLevel;
            this.riskTag = riskTag;
            this.message = message;
            this.reviewItem = reviewItem;
        }
    }
}
