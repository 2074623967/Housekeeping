package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentConfigService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付配置中心默认实现。
 */
@Service
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final PaymentConfigMapper paymentConfigMapper;

    public PaymentConfigServiceImpl(PaymentConfigMapper paymentConfigMapper) {
        this.paymentConfigMapper = paymentConfigMapper;
    }

    @Override
    public PaymentConfigOverviewDTO overview() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        overview.setChannels(paymentConfigMapper.findChannels());
        overview.setRouteRules(paymentConfigMapper.findRouteRules());
        overview.setProtocols(paymentConfigMapper.findProtocols());
        overview.setProtocolTypeOptions(paymentConfigMapper.findProtocolTypeOptions());
        overview.setReturnCodeMappings(paymentConfigMapper.findReturnCodeMappings());
        overview.setGateways(paymentConfigMapper.findGateways());
        overview.setControlPolicies(paymentConfigMapper.findControlPolicies());
        overview.setAlertProviders(paymentConfigMapper.findAlertProviders());
        overview.setIssueDutyRosters(paymentConfigMapper.findIssueDutyRosters());
        return overview;
    }

    @Override
    public String exportGovernanceSnapshotCsv(String section) {
        String normalizedSection = normalizeExportSection(section);
        PaymentConfigOverviewDTO overview = overview();
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("配置域,主键编码,次级编码,配置名称,状态,治理摘要,适用范围,风控/限制,运维建议,更新时间\n");
        appendChannelRows(builder, normalizedSection, overview.getChannels());
        appendRouteRuleRows(builder, normalizedSection, overview.getRouteRules());
        appendProtocolRows(builder, normalizedSection, overview.getProtocols());
        appendReturnCodeRows(builder, normalizedSection, overview.getReturnCodeMappings());
        appendGatewayRows(builder, normalizedSection, overview.getGateways());
        appendControlPolicyRows(builder, normalizedSection, overview.getControlPolicies());
        appendAlertProviderRows(builder, normalizedSection, overview.getAlertProviders());
        appendIssueDutyRosterRows(builder, normalizedSection, overview.getIssueDutyRosters());
        return builder.toString();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleChannel(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateChannelStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付渠道配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public List<PaymentRouteRuleReviewDTO> submitRouteRuleStatusReview(
            PaymentRouteRuleStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("路由规则启停状态不能为空");
        }
        String ruleCode = requireText(request.getRuleCode(), "路由规则编码不能为空");
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        PaymentRouteRuleConfigEntity current = paymentConfigMapper.findRouteRuleByCodeForUpdate(ruleCode);
        if (current == null) {
            throw new IllegalArgumentException("支付路由规则不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "支付路由规则已启用" : "支付路由规则已停用");
        }
        ensureRouteRuleBaseline(current);
        PaymentRouteRuleConfigEntity candidate = request.getEnabled().booleanValue()
                ? buildRouteRuleEntity(toUpsertRequest(current), ruleCode)
                : copyRouteRuleEntity(current);
        candidate.setStatus(candidateStatus);
        candidate.setStatusType(resolveStatusType(request.getEnabled()));
        return submitExistingRouteRuleReview(candidate, requester, reason, "STATUS_CHANGE", null);
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO createRouteRule(PaymentRouteRuleUpsertRequestDTO request) {
        PaymentRouteRuleConfigEntity entity = buildRouteRuleEntity(request, null);
        String changeReason = resolveChangeReason(request.getChangeReason(), "新增路由规则");
        if (paymentConfigMapper.findRouteRuleByCode(entity.getRuleCode()) != null) {
            throw new IllegalArgumentException("支付路由规则编码已存在");
        }
        paymentConfigMapper.insertRouteRule(entity);
        recordRouteRuleVersion(entity, "CREATE", null, changeReason);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO updateRouteRule(String ruleCode, PaymentRouteRuleUpsertRequestDTO request) {
        String normalizedRuleCode = requireText(ruleCode, "路由规则编码不能为空");
        PaymentRouteRuleConfigEntity current = paymentConfigMapper.findRouteRuleByCodeForUpdate(normalizedRuleCode);
        if (current == null) {
            throw new IllegalArgumentException("支付路由规则不存在");
        }
        PaymentRouteRuleConfigEntity entity = buildRouteRuleEntity(request, normalizedRuleCode);
        String changeReason = resolveChangeReason(request.getChangeReason(), "编辑路由规则");
        ensureRouteRuleBaseline(current);
        paymentConfigMapper.updateRouteRule(entity);
        recordRouteRuleVersion(entity, "UPDATE", null, changeReason);
        return overview();
    }

    @Override
    public PaymentRouteRuleValidationDTO validateRouteRule(PaymentRouteRuleValidationRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getMatchExpression())) {
            throw new IllegalArgumentException("路由规则表达式不能为空");
        }
        String expression = request.getMatchExpression().trim();
        boolean valid = PaymentRouteExpressionEvaluator.isValid(expression);
        PaymentRouteRuleValidationDTO result = new PaymentRouteRuleValidationDTO();
        result.setValid(Boolean.valueOf(valid));
        if (!valid) {
            result.setMatched(Boolean.FALSE);
            result.setMessage("表达式语法或字段不合法");
            return result;
        }
        PaymentRouteContextDTO context = new PaymentRouteContextDTO();
        context.setPaymentMethod(request.getPaymentMethod());
        context.setRequestedChannelCode(request.getRequestedChannelCode());
        context.setPayScene(request.getPayScene());
        context.setTerminal(request.getTerminal());
        context.setAmount(request.getAmount());
        context.setCustomerName(request.getCustomerName());
        boolean matched = PaymentRouteExpressionEvaluator.matches(context, expression);
        result.setMatched(Boolean.valueOf(matched));
        result.setMessage(matched ? "表达式合法，样例上下文命中" : "表达式合法，样例上下文未命中");
        return result;
    }

    @Override
    public List<PaymentRouteRuleConflictDTO> listRouteRuleConflicts() {
        return detectRouteRuleConflicts(paymentConfigMapper.findRouteRules());
    }

    private List<PaymentRouteRuleConflictDTO> detectRouteRuleConflicts(List<PaymentRouteRuleConfigDTO> rules) {
        List<PaymentRouteRuleConfigDTO> enabledRules = new ArrayList<PaymentRouteRuleConfigDTO>();
        for (PaymentRouteRuleConfigDTO rule : rules) {
            if (STATUS_ENABLED.equals(rule.getStatus()) && rolloutPercentageOf(rule) > 0) {
                enabledRules.add(rule);
            }
        }
        List<PaymentRouteRuleConflictDTO> conflicts = new ArrayList<PaymentRouteRuleConflictDTO>();
        for (PaymentRouteRuleConfigDTO rule : enabledRules) {
            if (!PaymentRouteExpressionEvaluator.isValid(rule.getMatchExpression())) {
                conflicts.add(buildInvalidExpressionConflict(rule));
            }
        }
        for (int leftIndex = 0; leftIndex < enabledRules.size(); leftIndex++) {
            PaymentRouteRuleConfigDTO left = enabledRules.get(leftIndex);
            if (!PaymentRouteExpressionEvaluator.isValid(left.getMatchExpression())) {
                continue;
            }
            for (int rightIndex = leftIndex + 1; rightIndex < enabledRules.size(); rightIndex++) {
                PaymentRouteRuleConfigDTO right = enabledRules.get(rightIndex);
                if (!PaymentRouteExpressionEvaluator.isValid(right.getMatchExpression())) {
                    continue;
                }
                PaymentRouteRuleConflictDTO conflict = detectRouteRuleConflict(left, right);
                if (conflict != null) {
                    conflicts.add(conflict);
                }
            }
        }
        return conflicts;
    }

    @Override
    public List<PaymentRouteRuleReviewDTO> listRouteRuleReviews() {
        return paymentConfigMapper.findRouteRuleReviews();
    }

    @Override
    @Transactional
    public List<PaymentRouteRuleReviewDTO> submitRouteRuleReview(PaymentRouteRuleReviewSubmitRequestDTO request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("路由规则候选快照不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        PaymentRouteRuleUpsertRequestDTO candidate = request.getCandidate();
        String ruleCode = requireText(candidate.getRuleCode(), "路由规则编码不能为空");
        PaymentRouteRuleConfigEntity current = paymentConfigMapper.findRouteRuleByCodeForUpdate(ruleCode);
        PaymentRouteRuleConfigEntity candidateEntity = buildRouteRuleEntity(candidate, current == null ? null : ruleCode);
        String reason = requireReviewText(candidate.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.countPendingRouteRuleReviews(ruleCode) > 0) {
            throw new IllegalArgumentException("该路由规则已有待复核申请");
        }
        Integer baseVersionNo = null;
        String operationType = "CREATE";
        if (current != null) {
            ensureRouteRuleBaseline(current);
            baseVersionNo = paymentConfigMapper.findLatestRouteRuleVersionNo(ruleCode);
            operationType = "UPDATE";
        }
        PaymentRouteRuleReviewEntity review = buildRouteRuleReview(
                candidateEntity, requester, reason, operationType, baseVersionNo, null);
        try {
            paymentConfigMapper.insertRouteRuleReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该路由规则已有待复核申请", exception);
        }
        return paymentConfigMapper.findRouteRuleReviews();
    }

    @Override
    @Transactional
    public List<PaymentRouteRuleReviewDTO> approveRouteRuleReview(
            String requestNo, PaymentRouteRuleReviewActionRequestDTO request) {
        PaymentRouteRuleReviewEntity review = requirePendingRouteRuleReview(requestNo);
        String reviewer = requireDistinctReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())
                && !"ROLLBACK".equals(review.getOperationType())) {
            throw new IllegalArgumentException("路由规则复核操作类型不支持");
        }
        if ("ROLLBACK".equals(review.getOperationType())
                && (review.getSourceVersionNo() == null || review.getSourceVersionNo().intValue() <= 0)) {
            throw new IllegalArgumentException("路由规则回滚候选缺少目标历史版本");
        }
        PaymentRouteRuleConfigEntity candidate = "STATUS_CHANGE".equals(review.getOperationType())
                && STATUS_DISABLED.equals(review.getStatus())
                ? copyRouteRuleEntity(review)
                : buildRouteRuleEntity(toUpsertRequest(review), review.getRuleCode());
        PaymentRouteRuleConfigEntity current = paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("规则已存在，复核申请基线已失效");
            }
        } else {
            if (current == null) {
                throw new IllegalArgumentException("规则不存在，复核申请基线已失效");
            }
            Integer latestVersionNo = paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode());
            if (!sameInteger(review.getBaseVersionNo(), latestVersionNo)) {
                throw new IllegalArgumentException("规则已产生新版本，请重新提交复核申请");
            }
        }
        ensureCandidateHasNoBlocker(candidate);
        if (current == null) {
            paymentConfigMapper.insertRouteRule(candidate);
        } else {
            paymentConfigMapper.updateRouteRule(candidate);
        }
        String versionChangeType = "REVIEW_RELEASE";
        Integer sourceVersionNo = null;
        if ("STATUS_CHANGE".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_STATUS_CHANGE";
        } else if ("ROLLBACK".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_ROLLBACK";
            sourceVersionNo = review.getSourceVersionNo();
        }
        recordRouteRuleVersion(candidate, versionChangeType, sourceVersionNo, review.getRequestReason());
        completeRouteRuleReview(review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findRouteRuleReviews();
    }

    @Override
    @Transactional
    public List<PaymentRouteRuleReviewDTO> rejectRouteRuleReview(
            String requestNo, PaymentRouteRuleReviewActionRequestDTO request) {
        PaymentRouteRuleReviewEntity review = requirePendingRouteRuleReview(requestNo);
        String reviewer = requireDistinctReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeRouteRuleReview(review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findRouteRuleReviews();
    }

    @Override
    public List<PaymentRouteRuleVersionDTO> listRouteRuleVersions(String ruleCode) {
        String normalizedRuleCode = requireText(ruleCode, "路由规则编码不能为空");
        if (paymentConfigMapper.findRouteRuleByCode(normalizedRuleCode) == null) {
            throw new IllegalArgumentException("支付路由规则不存在");
        }
        return paymentConfigMapper.findRouteRuleVersions(normalizedRuleCode);
    }

    @Override
    @Transactional
    public List<PaymentRouteRuleReviewDTO> submitRouteRuleRollbackReview(
            String ruleCode, PaymentRouteRuleRollbackRequestDTO request) {
        String normalizedRuleCode = requireText(ruleCode, "路由规则编码不能为空");
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        PaymentRouteRuleConfigEntity current = paymentConfigMapper.findRouteRuleByCodeForUpdate(normalizedRuleCode);
        if (current == null) {
            throw new IllegalArgumentException("支付路由规则不存在");
        }
        ensureRouteRuleBaseline(current);
        PaymentRouteRuleVersionEntity target = paymentConfigMapper.findRouteRuleVersion(
                normalizedRuleCode,
                request.getVersionNo()
        );
        if (target == null) {
            throw new IllegalArgumentException("支付路由规则历史版本不存在");
        }
        PaymentRouteRuleUpsertRequestDTO rollbackRequest = new PaymentRouteRuleUpsertRequestDTO();
        rollbackRequest.setRuleCode(normalizedRuleCode);
        rollbackRequest.setRuleName(target.getRuleName());
        rollbackRequest.setMatchScene(target.getMatchScene());
        rollbackRequest.setMatchExpression(target.getMatchExpression());
        rollbackRequest.setTargetChannelCode(target.getTargetChannelCode());
        rollbackRequest.setFallbackChannelCode(target.getFallbackChannelCode());
        rollbackRequest.setRolloutPercentage(target.getRolloutPercentage());
        rollbackRequest.setRolloutSalt(target.getRolloutSalt());
        rollbackRequest.setPriority(target.getPriority());
        rollbackRequest.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(target.getStatus())));
        PaymentRouteRuleConfigEntity candidate = buildRouteRuleEntity(rollbackRequest, normalizedRuleCode);
        return submitExistingRouteRuleReview(
                candidate, requester, reason, "ROLLBACK", request.getVersionNo());
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleProtocol(PaymentProtocolStatusRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("支付协议启停状态不能为空");
        }
        String protocolCode = requireText(request.getProtocolCode(), "协议编码不能为空");
        String changeReason = requireProtocolChangeReason(request.getChangeReason());
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(protocolCode);
        if (current == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        String status = resolveStatus(request.getEnabled());
        if (status.equals(current.getStatus())) {
            throw new IllegalArgumentException(STATUS_ENABLED.equals(status)
                    ? "支付协议已启用" : "支付协议已停用");
        }
        ensureProtocolBaseline(current);
        int affectedRows = paymentConfigMapper.updateProtocolStatus(
                protocolCode,
                status,
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        current.setStatus(status);
        current.setStatusType(resolveStatusType(request.getEnabled()));
        recordProtocolVersion(current, "STATUS_CHANGE", null, changeReason);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO createProtocol(PaymentProtocolUpsertRequestDTO request) {
        PaymentProtocolConfigEntity entity = buildProtocolEntity(request, null);
        String changeReason = requireProtocolChangeReason(request.getChangeReason());
        if (paymentConfigMapper.findProtocolByCode(entity.getProtocolCode()) != null) {
            throw new IllegalArgumentException("支付协议编码已存在");
        }
        paymentConfigMapper.insertProtocol(entity);
        recordProtocolVersion(entity, "CREATE", null, changeReason);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO updateProtocol(String protocolCode, PaymentProtocolUpsertRequestDTO request) {
        String normalizedProtocolCode = requireText(protocolCode, "协议编码不能为空");
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(normalizedProtocolCode);
        if (current == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        PaymentProtocolConfigEntity entity = buildProtocolEntity(request, normalizedProtocolCode);
        String changeReason = requireProtocolChangeReason(request.getChangeReason());
        ensureProtocolBaseline(current);
        paymentConfigMapper.updateProtocol(entity);
        recordProtocolVersion(entity, "UPDATE", null, changeReason);
        return overview();
    }

    @Override
    public List<PaymentProtocolVersionDTO> listProtocolVersions(String protocolCode) {
        String normalizedProtocolCode = requireText(protocolCode, "协议编码不能为空");
        if (paymentConfigMapper.findProtocolByCode(normalizedProtocolCode) == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        return paymentConfigMapper.findProtocolVersions(normalizedProtocolCode);
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO rollbackProtocol(
            String protocolCode, PaymentProtocolRollbackRequestDTO request) {
        String normalizedProtocolCode = requireText(protocolCode, "协议编码不能为空");
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        if (request.getBaseVersionNo() == null || request.getBaseVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("当前协议版本号必须大于0");
        }
        String changeReason = requireProtocolChangeReason(request.getChangeReason());
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(normalizedProtocolCode);
        if (current == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        ensureProtocolBaseline(current);
        Integer latestVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(normalizedProtocolCode);
        if (!sameInteger(request.getBaseVersionNo(), latestVersionNo)) {
            throw new IllegalArgumentException("支付协议已产生新版本，请刷新后重试");
        }
        PaymentProtocolVersionEntity target = paymentConfigMapper.findProtocolVersion(
                normalizedProtocolCode, request.getVersionNo());
        if (target == null) {
            throw new IllegalArgumentException("支付协议历史版本不存在");
        }
        PaymentProtocolConfigEntity restored = copyProtocolEntity(target);
        paymentConfigMapper.updateProtocol(restored);
        recordProtocolVersion(restored, "ROLLBACK", request.getVersionNo(), changeReason);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO createIssueDutyRoster(PaymentIssueDutyRosterUpsertRequestDTO request) {
        PaymentIssueDutyRosterEntity entity = buildIssueDutyRosterEntity(request, null);
        if (paymentConfigMapper.findIssueDutyRosterByCode(entity.getRosterCode()) != null) {
            throw new IllegalArgumentException("异常告警值班路由编码已存在");
        }
        paymentConfigMapper.insertIssueDutyRoster(entity);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO updateIssueDutyRoster(String rosterCode, PaymentIssueDutyRosterUpsertRequestDTO request) {
        String normalizedRosterCode = requireText(rosterCode, "值班路由编码不能为空");
        if (paymentConfigMapper.findIssueDutyRosterByCode(normalizedRosterCode) == null) {
            throw new IllegalArgumentException("异常告警值班路由配置不存在");
        }
        PaymentIssueDutyRosterEntity entity = buildIssueDutyRosterEntity(request, normalizedRosterCode);
        paymentConfigMapper.updateIssueDutyRoster(entity);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleReturnCodeMapping(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        String subCode = requireSubCode(request);
        int affectedRows = paymentConfigMapper.updateReturnCodeMappingStatus(
                configCode,
                subCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("渠道返回码映射配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleGateway(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateGatewayStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付网关接入配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleControlPolicy(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateControlPolicyStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleAlertProvider(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateAlertProviderStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("告警通知供应商配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO runControlPolicySelfCheck(PaymentConfigToggleRequestDTO request) {
        String sourceAppId = requireConfigCode(request);
        PaymentControlPolicyDTO controlPolicy = paymentConfigMapper.findControlPolicyBySourceAppId(sourceAppId);
        if (controlPolicy == null) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        SelfCheckResult selfCheckResult = evaluateControlPolicy(controlPolicy);
        paymentConfigMapper.updateControlPolicySelfCheck(
                sourceAppId,
                selfCheckResult.status,
                selfCheckResult.statusType,
                selfCheckResult.message
        );
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleIssueDutyRoster(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateIssueDutyRosterStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("异常告警值班路由配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentControlPolicySelfCheckSummaryDTO runAllEnabledControlPolicySelfChecks() {
        List<PaymentControlPolicySelfCheckItemDTO> items = paymentConfigMapper.findEnabledControlPolicySelfCheckItems();
        int passCount = 0;
        int warnCount = 0;
        int failCount = 0;
        for (PaymentControlPolicySelfCheckItemDTO item : items) {
            PaymentControlPolicyDTO controlPolicy = paymentConfigMapper.findControlPolicyBySourceAppId(item.getSourceAppId());
            if (controlPolicy == null) {
                failCount++;
                continue;
            }
            SelfCheckResult selfCheckResult = evaluateControlPolicy(controlPolicy);
            paymentConfigMapper.updateControlPolicySelfCheck(
                    item.getSourceAppId(),
                    selfCheckResult.status,
                    selfCheckResult.statusType,
                    selfCheckResult.message
            );
            if ("PASS".equals(selfCheckResult.status)) {
                passCount++;
            } else if ("WARN".equals(selfCheckResult.status)) {
                warnCount++;
            } else {
                failCount++;
            }
        }
        PaymentControlPolicySelfCheckSummaryDTO summary = new PaymentControlPolicySelfCheckSummaryDTO();
        summary.setProcessedCount(items.size());
        summary.setPassCount(passCount);
        summary.setWarnCount(warnCount);
        summary.setFailCount(failCount);
        return summary;
    }

    private SelfCheckResult evaluateControlPolicy(PaymentControlPolicyDTO controlPolicy) {
        Set<String> allowedChannels = splitToSet(controlPolicy.getAllowedChannelCodes());
        Set<String> allowedMethods = normalizePaymentMethods(splitToSet(controlPolicy.getAllowedPaymentMethods()));
        Set<String> allowedMerchants = splitToSet(controlPolicy.getAllowedMerchantNos());
        if (allowedMethods.isEmpty() || allowedChannels.isEmpty() || allowedMerchants.isEmpty()) {
            return new SelfCheckResult("FAIL", "danger", "支付方式、渠道或商户授权为空，禁止进入严格模式提交");
        }

        List<PaymentChannelConfigDTO> channels = paymentConfigMapper.findChannels();
        Set<String> enabledChannelCodes = new HashSet<String>();
        Set<String> enabledPaymentMethods = new HashSet<String>();
        for (PaymentChannelConfigDTO channel : channels) {
            if ("ENABLED".equals(channel.getStatus()) && allowedChannels.contains(channel.getChannelCode())) {
                enabledChannelCodes.add(channel.getChannelCode());
                enabledPaymentMethods.add(normalizePaymentMethod(channel.getPaymentMethod()));
            }
        }

        StringJoiner warningJoiner = new StringJoiner("；");
        if (!enabledChannelCodes.containsAll(allowedChannels)) {
            warningJoiner.add("存在未启用或不存在的授权渠道");
        }
        if (!enabledPaymentMethods.containsAll(allowedMethods)) {
            warningJoiner.add("存在未被启用渠道覆盖的支付方式");
        }
        if (!hasConfiguredMerchants(controlPolicy, allowedMerchants, channels)) {
            warningJoiner.add("存在未配置到启用渠道的授权商户号");
        }
        if (!hasEnabledGatewayForAnyChannel(allowedChannels)) {
            warningJoiner.add("未找到覆盖授权渠道的启用网关");
        }
        if ("开启".equals(controlPolicy.getTokenAuthRequired())
                && !StringUtils.hasText(controlPolicy.getAccessTokenValue())) {
            warningJoiner.add("令牌鉴权已开启但访问令牌为空");
        }

        if (warningJoiner.length() > 0) {
            return new SelfCheckResult("WARN", "warn", warningJoiner.toString());
        }
        return new SelfCheckResult("PASS", "success", "支付方式、渠道和网关接入均已通过自检");
    }

    private boolean hasEnabledGatewayForAnyChannel(Set<String> allowedChannels) {
        List<PaymentGatewayConfigDTO> gateways = paymentConfigMapper.findGateways();
        for (PaymentGatewayConfigDTO gateway : gateways) {
            if ("ENABLED".equals(gateway.getStatus())
                    && hasAnyConfiguredValue(gateway.getChannelScope(), allowedChannels)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 控制策略里的授权商户号既可能是渠道商户号，也可能是来源应用层面的业务商户号。
     * 当策略值与渠道商户号完全不在一个命名空间时，只要来源应用显式配置了非空商户号，就不应误判为缺失。
     */
    private boolean hasConfiguredMerchants(PaymentControlPolicyDTO controlPolicy,
                                           Set<String> allowedMerchants,
                                           List<PaymentChannelConfigDTO> channels) {
        Set<String> configuredMerchants = new HashSet<String>();
        for (PaymentChannelConfigDTO channel : channels) {
            if ("ENABLED".equals(channel.getStatus()) && StringUtils.hasText(channel.getMerchantNo())) {
                configuredMerchants.add(channel.getMerchantNo().trim());
            }
        }
        if (configuredMerchants.containsAll(allowedMerchants)) {
            return true;
        }
        if (isSourceAppMerchantScope(allowedMerchants, configuredMerchants)) {
            return hasAnyConfiguredValue(controlPolicy.getAllowedMerchantNos(), allowedMerchants);
        }
        return false;
    }

    private boolean hasAnyConfiguredValue(String configuredValues, Set<String> expectedValues) {
        for (String configuredValue : splitToSet(configuredValues)) {
            if (expectedValues.contains(configuredValue)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> splitToSet(String configuredValues) {
        Set<String> result = new HashSet<String>();
        if (!StringUtils.hasText(configuredValues)) {
            return result;
        }
        for (String configuredValue : Arrays.asList(configuredValues.split(","))) {
            if (StringUtils.hasText(configuredValue)) {
                result.add(configuredValue.trim());
            }
        }
        return result;
    }

    private boolean isSourceAppMerchantScope(Set<String> allowedMerchants, Set<String> configuredMerchants) {
        if (allowedMerchants.isEmpty() || configuredMerchants.isEmpty()) {
            return false;
        }
        for (String allowedMerchant : allowedMerchants) {
            if (configuredMerchants.contains(allowedMerchant)) {
                return false;
            }
        }
        return true;
    }

    private Set<String> normalizePaymentMethods(Set<String> paymentMethods) {
        Set<String> normalizedPaymentMethods = new HashSet<String>();
        for (String paymentMethod : paymentMethods) {
            normalizedPaymentMethods.add(normalizePaymentMethod(paymentMethod));
        }
        return normalizedPaymentMethods;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (!StringUtils.hasText(paymentMethod)) {
            return "";
        }
        String normalizedPaymentMethod = paymentMethod.trim().toUpperCase(Locale.ROOT);
        if ("银行卡".equals(paymentMethod.trim()) || "银行转账".equals(paymentMethod.trim())) {
            return "BANK_CARD";
        }
        if (normalizedPaymentMethod.contains("WX") || "微信支付".equals(paymentMethod.trim())) {
            return "WECHAT_PAY";
        }
        if (normalizedPaymentMethod.contains("ALI") || "支付宝".equals(paymentMethod.trim())) {
            return "ALIPAY";
        }
        return normalizedPaymentMethod;
    }

    private String requireConfigCode(PaymentConfigToggleRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new IllegalArgumentException("配置编码不能为空");
        }
        return request.getConfigCode().trim();
    }

    private String requireSubCode(PaymentConfigToggleRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getSubCode())) {
            throw new IllegalArgumentException("配置子编码不能为空");
        }
        return request.getSubCode().trim();
    }

    private void appendChannelRows(StringBuilder builder,
                                   String section,
                                   List<PaymentChannelConfigDTO> channels) {
        if (!shouldExportSection(section, "CHANNELS") || channels == null) {
            return;
        }
        for (PaymentChannelConfigDTO channel : channels) {
            appendCsvRow(builder,
                    "支付渠道",
                    channel.getChannelCode(),
                    channel.getMerchantNo(),
                    channel.getChannelName(),
                    channel.getStatus(),
                    safeJoin(" / ",
                            "支付方式=" + defaultText(channel.getPaymentMethod()),
                            "优先级=" + valueOf(channel.getPriority()),
                            "路由权重=" + valueOf(channel.getRouteWeight()),
                            "单日限额=" + defaultText(channel.getDailyLimit())),
                    safeJoin(" / ",
                            "场景=" + defaultText(channel.getSceneScope()),
                            "商户应用=" + defaultText(channel.getMerchantAppId())),
                    safeJoin(" / ",
                            "风控标签=" + defaultText(channel.getRiskControlTag()),
                            "退款时效=" + defaultText(channel.getRefundWindow()),
                            "验签时间窗=" + defaultText(channel.getNotifySignWindow())),
                    safeJoin(" / ",
                            "回调地址=" + defaultText(channel.getCallbackNotifyUrl()),
                            "证书档案=" + defaultText(channel.getCertificateProfile())),
                    channel.getUpdatedAt());
        }
    }

    private void appendRouteRuleRows(StringBuilder builder,
                                     String section,
                                     List<PaymentRouteRuleConfigDTO> routeRules) {
        if (!shouldExportSection(section, "ROUTE_RULES") || routeRules == null) {
            return;
        }
        for (PaymentRouteRuleConfigDTO routeRule : routeRules) {
            appendCsvRow(builder,
                    "路由规则",
                    routeRule.getRuleCode(),
                    routeRule.getTargetChannelCode(),
                    routeRule.getRuleName(),
                    routeRule.getStatus(),
                    safeJoin(" / ",
                            "匹配表达式=" + defaultText(routeRule.getMatchExpression()),
                            "优先级=" + valueOf(routeRule.getPriority()),
                            "灰度=" + rolloutPercentageOf(routeRule) + "%",
                            "分桶盐=" + defaultText(routeRule.getRolloutSalt())),
                    safeJoin(" / ",
                            "业务场景=" + defaultText(routeRule.getMatchScene()),
                            "兜底渠道=" + defaultText(routeRule.getFallbackChannelCode())),
                    "需确认目标渠道启停与规则优先级是否一致",
                    "路由异常时先联查目标渠道、兜底渠道和命中表达式",
                    routeRule.getUpdatedAt());
        }
    }

    private void appendProtocolRows(StringBuilder builder,
                                    String section,
                                    List<PaymentProtocolConfigDTO> protocols) {
        if (!shouldExportSection(section, "PROTOCOLS") || protocols == null) {
            return;
        }
        for (PaymentProtocolConfigDTO protocol : protocols) {
            appendCsvRow(builder,
                    "支付协议",
                    protocol.getProtocolCode(),
                    protocol.getTemplateCode(),
                    protocol.getProtocolName(),
                    protocol.getStatus(),
                    safeJoin(" / ",
                            "协议类型=" + defaultText(protocol.getProtocolTypeName()),
                            "模板版本=" + defaultText(protocol.getTemplateVersion()),
                            "优先级=" + valueOf(protocol.getPriority())),
                    safeJoin(" / ",
                            "适用场景=" + defaultText(protocol.getSceneScope()),
                            "适用渠道=" + defaultText(protocol.getChannelScope())),
                    safeJoin(" / ",
                            "签约模式=" + defaultText(protocol.getSignMode()),
                            "风控标签=" + defaultText(protocol.getRiskControlTag())),
                    safeJoin(" / ",
                            "签章服务商=" + defaultText(protocol.getESignatureProvider()),
                            "商户确认=" + defaultText(protocol.getMerchantAckRequired())),
                    protocol.getUpdatedAt());
        }
    }

    private void appendReturnCodeRows(StringBuilder builder,
                                      String section,
                                      List<PaymentChannelReturnCodeConfigDTO> returnCodeMappings) {
        if (!shouldExportSection(section, "RETURN_CODES") || returnCodeMappings == null) {
            return;
        }
        for (PaymentChannelReturnCodeConfigDTO mapping : returnCodeMappings) {
            appendCsvRow(builder,
                    "返回码映射",
                    mapping.getChannelCode(),
                    mapping.getChannelReturnCode(),
                    mapping.getStandardizedCode(),
                    mapping.getStatus(),
                    safeJoin(" / ",
                            "标准文案=" + defaultText(mapping.getStandardizedMessage()),
                            "映射版本=" + defaultText(mapping.getMappingVersion())),
                    safeJoin(" / ",
                            "归档状态=" + defaultText(mapping.getArchiveStatus()),
                            "可重试=" + defaultText(mapping.getRetryable())),
                    "人工介入=" + defaultText(mapping.getManualInterventionRequired()),
                    defaultText(mapping.getHandlingSuggestion()),
                    mapping.getUpdatedAt());
        }
    }

    private void appendGatewayRows(StringBuilder builder,
                                   String section,
                                   List<PaymentGatewayConfigDTO> gateways) {
        if (!shouldExportSection(section, "GATEWAYS") || gateways == null) {
            return;
        }
        for (PaymentGatewayConfigDTO gateway : gateways) {
            appendCsvRow(builder,
                    "支付网关",
                    gateway.getGatewayCode(),
                    gateway.getAccessMode(),
                    gateway.getGatewayName(),
                    gateway.getStatus(),
                    safeJoin(" / ",
                            "发布阶段=" + defaultText(gateway.getReleaseStage()),
                            "超时=" + defaultText(gateway.getTimeoutMs()),
                            "重试策略=" + defaultText(gateway.getRetryPolicy())),
                    safeJoin(" / ",
                            "渠道范围=" + defaultText(gateway.getChannelScope()),
                            "环境范围=" + defaultText(gateway.getEnvironmentScope())),
                    safeJoin(" / ",
                            "证书状态=" + defaultText(gateway.getCertificateStatus()),
                            "灰度策略=" + defaultText(gateway.getGrayStrategy())),
                    safeJoin(" / ",
                            "回调白名单=" + defaultText(gateway.getCallbackWhitelist()),
                            "适配器=" + defaultText(gateway.getAdapterRegistry())),
                    gateway.getUpdatedAt());
        }
    }

    private void appendControlPolicyRows(StringBuilder builder,
                                         String section,
                                         List<PaymentControlPolicyDTO> controlPolicies) {
        if (!shouldExportSection(section, "CONTROL_POLICIES") || controlPolicies == null) {
            return;
        }
        for (PaymentControlPolicyDTO controlPolicy : controlPolicies) {
            appendCsvRow(builder,
                    "支付控制策略",
                    controlPolicy.getSourceAppId(),
                    null,
                    controlPolicy.getSourceAppName(),
                    controlPolicy.getStatus(),
                    safeJoin(" / ",
                            "分钟限流=" + valueOf(controlPolicy.getMinuteSubmitLimit()),
                            "接口限流=" + valueOf(controlPolicy.getInterfaceMinuteSubmitLimit()),
                            "严格模式=" + defaultText(controlPolicy.getStrictMode())),
                    safeJoin(" / ",
                            "支付方式=" + defaultText(controlPolicy.getAllowedPaymentMethods()),
                            "渠道=" + defaultText(controlPolicy.getAllowedChannelCodes()),
                            "商户号=" + defaultText(controlPolicy.getAllowedMerchantNos())),
                    safeJoin(" / ",
                            "令牌鉴权=" + defaultText(controlPolicy.getTokenAuthRequired()),
                            "自检状态=" + defaultText(controlPolicy.getSelfCheckStatus())),
                    defaultText(controlPolicy.getSelfCheckMessage()),
                    controlPolicy.getUpdatedAt());
        }
    }

    private void appendAlertProviderRows(StringBuilder builder,
                                         String section,
                                         List<PaymentAlertProviderConfigDTO> alertProviders) {
        if (!shouldExportSection(section, "ALERT_PROVIDERS") || alertProviders == null) {
            return;
        }
        for (PaymentAlertProviderConfigDTO alertProvider : alertProviders) {
            appendCsvRow(builder,
                    "告警供应商",
                    alertProvider.getProviderCode(),
                    alertProvider.getChannelCode(),
                    alertProvider.getProviderName(),
                    alertProvider.getStatus(),
                    safeJoin(" / ",
                            "模板=" + defaultText(alertProvider.getTemplateCode()),
                            "优先级=" + valueOf(alertProvider.getRoutePriority())),
                    safeJoin(" / ",
                            "接入端点=" + defaultText(alertProvider.getEndpointAlias()),
                            "路由规则=" + defaultText(alertProvider.getRouteRule())),
                    safeJoin(" / ",
                            "重试策略=" + defaultText(alertProvider.getRetryPolicy()),
                            "限流策略=" + defaultText(alertProvider.getRateLimitPolicy())),
                    "结合异常严重等级与通道容量复核派发优先级",
                    alertProvider.getUpdatedAt());
        }
    }

    private void appendIssueDutyRosterRows(StringBuilder builder,
                                           String section,
                                           List<PaymentIssueDutyRosterDTO> issueDutyRosters) {
        if (!shouldExportSection(section, "ISSUE_DUTY_ROSTERS") || issueDutyRosters == null) {
            return;
        }
        for (PaymentIssueDutyRosterDTO issueDutyRoster : issueDutyRosters) {
            appendCsvRow(builder,
                    "值班路由",
                    issueDutyRoster.getRosterCode(),
                    issueDutyRoster.getSeverity(),
                    issueDutyRoster.getIssueType(),
                    issueDutyRoster.getStatus(),
                    safeJoin(" / ",
                            "责任组=" + defaultText(issueDutyRoster.getResponsibilityGroup()),
                            "接收人=" + defaultText(issueDutyRoster.getReceiver())),
                    safeJoin(" / ",
                            "通道=" + defaultText(issueDutyRoster.getNotifyChannels()),
                            "班次=" + defaultText(issueDutyRoster.getScheduleTag()),
                            "日期策略=" + defaultText(issueDutyRoster.getApplicabilityDesc())),
                    safeJoin(" / ",
                            "升级等级=" + defaultText(issueDutyRoster.getEscalationLevel()),
                            "升级超时=" + valueOf(issueDutyRoster.getEscalationTimeoutMinutes())),
                    safeJoin(" / ",
                            "升级接收人=" + defaultText(issueDutyRoster.getEscalationReceiver()),
                            "时间窗=" + defaultText(issueDutyRoster.getEffectiveWindow())),
                    issueDutyRoster.getUpdatedAt());
        }
    }

    private void appendCsvRow(StringBuilder builder,
                              String configDomain,
                              String primaryCode,
                              String secondaryCode,
                              String configName,
                              String status,
                              String governanceSummary,
                              String scopeSummary,
                              String riskSummary,
                              String actionSuggestion,
                              String updatedAt) {
        builder.append(csvCell(configDomain)).append(',')
                .append(csvCell(primaryCode)).append(',')
                .append(csvCell(secondaryCode)).append(',')
                .append(csvCell(configName)).append(',')
                .append(csvCell(status)).append(',')
                .append(csvCell(governanceSummary)).append(',')
                .append(csvCell(scopeSummary)).append(',')
                .append(csvCell(riskSummary)).append(',')
                .append(csvCell(actionSuggestion)).append(',')
                .append(csvCell(updatedAt)).append('\n');
    }

    private boolean shouldExportSection(String normalizedSection, String currentSection) {
        return "ALL".equals(normalizedSection) || currentSection.equals(normalizedSection);
    }

    private String normalizeExportSection(String section) {
        if (!StringUtils.hasText(section)) {
            return "ALL";
        }
        String normalized = section.trim().toUpperCase();
        Set<String> allowedSections = new HashSet<String>(Arrays.asList(
                "ALL",
                "CHANNELS",
                "ROUTE_RULES",
                "PROTOCOLS",
                "RETURN_CODES",
                "GATEWAYS",
                "CONTROL_POLICIES",
                "ALERT_PROVIDERS",
                "ISSUE_DUTY_ROSTERS"
        ));
        if (!allowedSections.contains(normalized)) {
            throw new IllegalArgumentException("导出分区不合法");
        }
        return normalized;
    }

    private String safeJoin(String delimiter, String... values) {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (String value : values) {
            if (StringUtils.hasText(value) && !value.endsWith("=-")) {
                joiner.add(value);
            }
        }
        return joiner.toString();
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String valueOf(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private PaymentProtocolConfigEntity buildProtocolEntity(PaymentProtocolUpsertRequestDTO request,
                                                            String overrideProtocolCode) {
        if (request == null) {
            throw new IllegalArgumentException("支付协议请求不能为空");
        }
        PaymentProtocolConfigEntity entity = new PaymentProtocolConfigEntity();
        entity.setProtocolCode(StringUtils.hasText(overrideProtocolCode)
                ? overrideProtocolCode.trim()
                : requireText(request.getProtocolCode(), "协议编码不能为空"));
        entity.setProtocolName(requireText(request.getProtocolName(), "协议名称不能为空"));
        entity.setProtocolType(requireText(request.getProtocolType(), "协议类型不能为空"));
        entity.setProtocolTypeName(resolveProtocolTypeName(request.getProtocolType(), request.getProtocolTypeName()));
        entity.setTemplateCode(requireText(request.getTemplateCode(), "协议模板编码不能为空"));
        entity.setTemplateName(requireText(request.getTemplateName(), "协议模板名称不能为空"));
        entity.setTemplateVersion(requireText(request.getTemplateVersion(), "模板版本不能为空"));
        entity.setSignMode(requireText(request.getSignMode(), "签约模式不能为空"));
        entity.setSignElementSpec(requireText(request.getSignElementSpec(), "签约要素配置不能为空"));
        entity.setESignatureProvider(requireText(request.getESignatureProvider(), "电子签章服务商不能为空"));
        entity.setSceneScope(requireText(request.getSceneScope(), "适用场景不能为空"));
        entity.setChannelScope(requireText(request.getChannelScope(), "适用渠道不能为空"));
        entity.setMerchantAckRequired(requireText(request.getMerchantAckRequired(), "商户确认要求不能为空"));
        entity.setRiskControlTag(requireText(request.getRiskControlTag(), "风控标签不能为空"));
        entity.setProtocolBody(requireText(request.getProtocolBody(), "协议正文不能为空"));
        entity.setPriority(resolvePriority(request.getPriority()));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private void ensureProtocolBaseline(PaymentProtocolConfigEntity current) {
        Integer latestVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(current.getProtocolCode());
        if (latestVersionNo == null || latestVersionNo.intValue() == 0) {
            recordProtocolVersion(current, "BASELINE", null, "存量协议变更前基线");
        }
    }

    private void recordProtocolVersion(PaymentProtocolConfigEntity protocol,
                                       String changeType,
                                       Integer sourceVersionNo,
                                       String changeReason) {
        Integer latestVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(protocol.getProtocolCode());
        int nextVersionNo = latestVersionNo == null ? 1 : latestVersionNo.intValue() + 1;
        PaymentProtocolVersionEntity version = new PaymentProtocolVersionEntity();
        version.setProtocolCode(protocol.getProtocolCode());
        version.setVersionNo(Integer.valueOf(nextVersionNo));
        version.setProtocolName(protocol.getProtocolName());
        version.setProtocolType(protocol.getProtocolType());
        version.setProtocolTypeName(protocol.getProtocolTypeName());
        version.setTemplateCode(protocol.getTemplateCode());
        version.setTemplateName(protocol.getTemplateName());
        version.setTemplateVersion(protocol.getTemplateVersion());
        version.setSignMode(protocol.getSignMode());
        version.setSignElementSpec(protocol.getSignElementSpec());
        version.setESignatureProvider(protocol.getESignatureProvider());
        version.setSceneScope(protocol.getSceneScope());
        version.setChannelScope(protocol.getChannelScope());
        version.setMerchantAckRequired(protocol.getMerchantAckRequired());
        version.setRiskControlTag(protocol.getRiskControlTag());
        version.setProtocolBody(protocol.getProtocolBody());
        version.setStatus(protocol.getStatus());
        version.setStatusType(protocol.getStatusType());
        version.setPriority(protocol.getPriority());
        version.setChangeType(changeType);
        version.setSourceVersionNo(sourceVersionNo);
        version.setChangeReason(changeReason);
        paymentConfigMapper.insertProtocolVersion(version);
    }

    private PaymentProtocolConfigEntity copyProtocolEntity(PaymentProtocolVersionEntity version) {
        PaymentProtocolConfigEntity protocol = new PaymentProtocolConfigEntity();
        protocol.setProtocolCode(version.getProtocolCode());
        protocol.setProtocolName(version.getProtocolName());
        protocol.setProtocolType(version.getProtocolType());
        protocol.setProtocolTypeName(version.getProtocolTypeName());
        protocol.setTemplateCode(version.getTemplateCode());
        protocol.setTemplateName(version.getTemplateName());
        protocol.setTemplateVersion(version.getTemplateVersion());
        protocol.setSignMode(version.getSignMode());
        protocol.setSignElementSpec(version.getSignElementSpec());
        protocol.setESignatureProvider(version.getESignatureProvider());
        protocol.setSceneScope(version.getSceneScope());
        protocol.setChannelScope(version.getChannelScope());
        protocol.setMerchantAckRequired(version.getMerchantAckRequired());
        protocol.setRiskControlTag(version.getRiskControlTag());
        protocol.setProtocolBody(version.getProtocolBody());
        protocol.setStatus(version.getStatus());
        protocol.setStatusType(version.getStatusType());
        protocol.setPriority(version.getPriority());
        return protocol;
    }

    private String requireProtocolChangeReason(String changeReason) {
        String reason = requireText(changeReason, "协议变更原因不能为空");
        if (reason.length() > 255) {
            throw new IllegalArgumentException("协议变更原因不能超过255个字符");
        }
        return reason;
    }

    private PaymentRouteRuleConfigEntity buildRouteRuleEntity(PaymentRouteRuleUpsertRequestDTO request,
                                                               String overrideRuleCode) {
        if (request == null) {
            throw new IllegalArgumentException("支付路由规则请求不能为空");
        }
        PaymentRouteRuleConfigEntity entity = new PaymentRouteRuleConfigEntity();
        entity.setRuleCode(StringUtils.hasText(overrideRuleCode)
                ? overrideRuleCode.trim()
                : requireText(request.getRuleCode(), "路由规则编码不能为空"));
        entity.setRuleName(requireText(request.getRuleName(), "路由规则名称不能为空"));
        entity.setMatchScene(requireText(request.getMatchScene(), "匹配场景不能为空"));
        String expression = requireText(request.getMatchExpression(), "匹配表达式不能为空");
        if (!PaymentRouteExpressionEvaluator.isValid(expression)) {
            throw new IllegalArgumentException("路由规则表达式不合法");
        }
        entity.setMatchExpression(expression);
        String targetChannelCode = requireText(request.getTargetChannelCode(), "目标渠道不能为空");
        if (paymentConfigMapper.findChannelForRouting(targetChannelCode) == null) {
            throw new IllegalArgumentException("目标渠道配置不存在");
        }
        entity.setTargetChannelCode(targetChannelCode);
        String fallbackChannelCode = StringUtils.hasText(request.getFallbackChannelCode())
                ? request.getFallbackChannelCode().trim() : "";
        if (StringUtils.hasText(fallbackChannelCode)
                && paymentConfigMapper.findChannelForRouting(fallbackChannelCode) == null) {
            throw new IllegalArgumentException("兜底渠道配置不存在");
        }
        if (targetChannelCode.equalsIgnoreCase(fallbackChannelCode)) {
            throw new IllegalArgumentException("目标渠道与兜底渠道不能相同");
        }
        entity.setFallbackChannelCode(fallbackChannelCode);
        Integer rolloutPercentage = request.getRolloutPercentage() == null
                ? Integer.valueOf(100) : request.getRolloutPercentage();
        if (rolloutPercentage.intValue() < 0 || rolloutPercentage.intValue() > 100) {
            throw new IllegalArgumentException("路由规则灰度比例必须在0到100之间");
        }
        entity.setRolloutPercentage(rolloutPercentage);
        String rolloutSalt = requireText(request.getRolloutSalt(), "路由规则分桶盐不能为空");
        if (rolloutSalt.length() > 64) {
            throw new IllegalArgumentException("路由规则分桶盐不能超过64个字符");
        }
        entity.setRolloutSalt(rolloutSalt);
        entity.setPriority(resolveRoutePriority(request.getPriority()));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private void ensureRouteRuleBaseline(PaymentRouteRuleConfigEntity current) {
        Integer latestVersionNo = paymentConfigMapper.findLatestRouteRuleVersionNo(current.getRuleCode());
        if (latestVersionNo == null || latestVersionNo.intValue() == 0) {
            recordRouteRuleVersion(current, "BASELINE", null, "存量规则变更前基线");
        }
    }

    private void recordRouteRuleVersion(PaymentRouteRuleConfigEntity rule,
                                        String changeType,
                                        Integer sourceVersionNo,
                                        String changeReason) {
        Integer latestVersionNo = paymentConfigMapper.findLatestRouteRuleVersionNo(rule.getRuleCode());
        int nextVersionNo = latestVersionNo == null ? 1 : latestVersionNo.intValue() + 1;
        PaymentRouteRuleVersionEntity version = new PaymentRouteRuleVersionEntity();
        version.setRuleCode(rule.getRuleCode());
        version.setVersionNo(Integer.valueOf(nextVersionNo));
        version.setRuleName(rule.getRuleName());
        version.setMatchScene(rule.getMatchScene());
        version.setMatchExpression(rule.getMatchExpression());
        version.setTargetChannelCode(rule.getTargetChannelCode());
        version.setFallbackChannelCode(rule.getFallbackChannelCode());
        version.setRolloutPercentage(rule.getRolloutPercentage());
        version.setRolloutSalt(rule.getRolloutSalt());
        version.setStatus(rule.getStatus());
        version.setStatusType(rule.getStatusType());
        version.setPriority(rule.getPriority());
        version.setChangeType(changeType);
        version.setSourceVersionNo(sourceVersionNo);
        version.setChangeReason(changeReason);
        paymentConfigMapper.insertRouteRuleVersion(version);
    }

    private String resolveChangeReason(String changeReason, String defaultReason) {
        String resolvedReason = StringUtils.hasText(changeReason) ? changeReason.trim() : defaultReason;
        if (resolvedReason.length() > 255) {
            throw new IllegalArgumentException("路由规则变更原因不能超过255个字符");
        }
        return resolvedReason;
    }

    private PaymentRouteRuleReviewEntity buildRouteRuleReview(PaymentRouteRuleConfigEntity candidate,
                                                               String requester,
                                                               String reason,
                                                               String operationType,
                                                               Integer baseVersionNo,
                                                               Integer sourceVersionNo) {
        PaymentRouteRuleReviewEntity review = new PaymentRouteRuleReviewEntity();
        review.setRequestNo("RRV" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        review.setRuleCode(candidate.getRuleCode());
        review.setBaseVersionNo(baseVersionNo);
        review.setSourceVersionNo(sourceVersionNo);
        review.setOperationType(operationType);
        review.setRuleName(candidate.getRuleName());
        review.setMatchScene(candidate.getMatchScene());
        review.setMatchExpression(candidate.getMatchExpression());
        review.setTargetChannelCode(candidate.getTargetChannelCode());
        review.setFallbackChannelCode(candidate.getFallbackChannelCode());
        review.setRolloutPercentage(candidate.getRolloutPercentage());
        review.setRolloutSalt(candidate.getRolloutSalt());
        review.setStatus(candidate.getStatus());
        review.setStatusType(candidate.getStatusType());
        review.setPriority(candidate.getPriority());
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private List<PaymentRouteRuleReviewDTO> submitExistingRouteRuleReview(
            PaymentRouteRuleConfigEntity candidate,
            String requester,
            String reason,
            String operationType,
            Integer sourceVersionNo) {
        String ruleCode = candidate.getRuleCode();
        if (paymentConfigMapper.countPendingRouteRuleReviews(ruleCode) > 0) {
            throw new IllegalArgumentException("该路由规则已有待复核申请");
        }
        Integer baseVersionNo = paymentConfigMapper.findLatestRouteRuleVersionNo(ruleCode);
        PaymentRouteRuleReviewEntity review = buildRouteRuleReview(
                candidate, requester, reason, operationType, baseVersionNo, sourceVersionNo);
        try {
            paymentConfigMapper.insertRouteRuleReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该路由规则已有待复核申请", exception);
        }
        return paymentConfigMapper.findRouteRuleReviews();
    }

    private PaymentRouteRuleReviewEntity requirePendingRouteRuleReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentRouteRuleReviewEntity review = paymentConfigMapper.findRouteRuleReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("路由规则复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("路由规则复核申请已完成");
        }
        return review;
    }

    private String requireDistinctReviewer(PaymentRouteRuleReviewEntity review,
                                           PaymentRouteRuleReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeRouteRuleReview(String requestNo,
                                         String reviewStatus,
                                         String reviewStatusType,
                                         String reviewer,
                                         String reviewComment) {
        int affectedRows = paymentConfigMapper.completeRouteRuleReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("路由规则复核状态已变化，请刷新后重试");
        }
    }

    private PaymentRouteRuleUpsertRequestDTO toUpsertRequest(PaymentRouteRuleReviewEntity review) {
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode(review.getRuleCode());
        request.setRuleName(review.getRuleName());
        request.setMatchScene(review.getMatchScene());
        request.setMatchExpression(review.getMatchExpression());
        request.setTargetChannelCode(review.getTargetChannelCode());
        request.setFallbackChannelCode(review.getFallbackChannelCode());
        request.setRolloutPercentage(review.getRolloutPercentage());
        request.setRolloutSalt(review.getRolloutSalt());
        request.setPriority(review.getPriority());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getStatus())));
        request.setChangeReason(review.getRequestReason());
        return request;
    }

    private PaymentRouteRuleUpsertRequestDTO toUpsertRequest(PaymentRouteRuleConfigEntity rule) {
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode(rule.getRuleCode());
        request.setRuleName(rule.getRuleName());
        request.setMatchScene(rule.getMatchScene());
        request.setMatchExpression(rule.getMatchExpression());
        request.setTargetChannelCode(rule.getTargetChannelCode());
        request.setFallbackChannelCode(rule.getFallbackChannelCode());
        request.setRolloutPercentage(rule.getRolloutPercentage());
        request.setRolloutSalt(rule.getRolloutSalt());
        request.setPriority(rule.getPriority());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(rule.getStatus())));
        return request;
    }

    private PaymentRouteRuleConfigEntity copyRouteRuleEntity(PaymentRouteRuleConfigEntity rule) {
        PaymentRouteRuleConfigEntity copy = new PaymentRouteRuleConfigEntity();
        copy.setRuleCode(rule.getRuleCode());
        copy.setRuleName(rule.getRuleName());
        copy.setMatchScene(rule.getMatchScene());
        copy.setMatchExpression(rule.getMatchExpression());
        copy.setTargetChannelCode(rule.getTargetChannelCode());
        copy.setFallbackChannelCode(rule.getFallbackChannelCode());
        copy.setRolloutPercentage(rule.getRolloutPercentage());
        copy.setRolloutSalt(rule.getRolloutSalt());
        copy.setStatus(rule.getStatus());
        copy.setStatusType(rule.getStatusType());
        copy.setPriority(rule.getPriority());
        return copy;
    }

    private PaymentRouteRuleConfigEntity copyRouteRuleEntity(PaymentRouteRuleReviewEntity review) {
        PaymentRouteRuleConfigEntity copy = new PaymentRouteRuleConfigEntity();
        copy.setRuleCode(review.getRuleCode());
        copy.setRuleName(review.getRuleName());
        copy.setMatchScene(review.getMatchScene());
        copy.setMatchExpression(review.getMatchExpression());
        copy.setTargetChannelCode(review.getTargetChannelCode());
        copy.setFallbackChannelCode(review.getFallbackChannelCode());
        copy.setRolloutPercentage(review.getRolloutPercentage());
        copy.setRolloutSalt(review.getRolloutSalt());
        copy.setStatus(review.getStatus());
        copy.setStatusType(review.getStatusType());
        copy.setPriority(review.getPriority());
        return copy;
    }

    private void ensureCandidateHasNoBlocker(PaymentRouteRuleConfigEntity candidate) {
        List<PaymentRouteRuleConfigDTO> candidateRules = new ArrayList<PaymentRouteRuleConfigDTO>();
        for (PaymentRouteRuleConfigDTO rule : paymentConfigMapper.findRouteRules()) {
            if (!candidate.getRuleCode().equalsIgnoreCase(rule.getRuleCode())) {
                candidateRules.add(rule);
            }
        }
        candidateRules.add(toConfigDto(candidate));
        for (PaymentRouteRuleConflictDTO conflict : detectRouteRuleConflicts(candidateRules)) {
            boolean candidateInvolved = candidate.getRuleCode().equalsIgnoreCase(conflict.getRuleCode())
                    || candidate.getRuleCode().equalsIgnoreCase(conflict.getConflictingRuleCode());
            if (candidateInvolved && "BLOCKER".equals(conflict.getSeverity())) {
                throw new IllegalArgumentException("候选规则存在阻断级冲突：" + conflict.getMessage());
            }
        }
    }

    private PaymentRouteRuleConfigDTO toConfigDto(PaymentRouteRuleConfigEntity entity) {
        PaymentRouteRuleConfigDTO dto = new PaymentRouteRuleConfigDTO();
        dto.setRuleCode(entity.getRuleCode());
        dto.setRuleName(entity.getRuleName());
        dto.setMatchScene(entity.getMatchScene());
        dto.setMatchExpression(entity.getMatchExpression());
        dto.setTargetChannelCode(entity.getTargetChannelCode());
        dto.setFallbackChannelCode(entity.getFallbackChannelCode());
        dto.setRolloutPercentage(entity.getRolloutPercentage());
        dto.setRolloutSalt(entity.getRolloutSalt());
        dto.setStatus(entity.getStatus());
        dto.setStatusType(entity.getStatusType());
        dto.setPriority(entity.getPriority());
        return dto;
    }

    private boolean sameInteger(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private int rolloutPercentageOf(PaymentRouteRuleConfigDTO rule) {
        return rule.getRolloutPercentage() == null ? 100 : rule.getRolloutPercentage().intValue();
    }

    private String requireReviewIdentity(String identity, String message) {
        String normalized = requireText(identity, message);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("人员声明不能超过64个字符");
        }
        return normalized;
    }

    private String requireReviewText(String text, String message) {
        String normalized = requireText(text, message);
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("复核说明不能超过255个字符");
        }
        return normalized;
    }

    private PaymentRouteRuleConflictDTO detectRouteRuleConflict(PaymentRouteRuleConfigDTO left,
                                                                 PaymentRouteRuleConfigDTO right) {
        String overlappingScene = resolveOverlappingScene(left.getMatchScene(), right.getMatchScene());
        if (overlappingScene == null) {
            return null;
        }
        boolean sameExpression = PaymentRouteExpressionEvaluator.canonicalize(left.getMatchExpression())
                .equals(PaymentRouteExpressionEvaluator.canonicalize(right.getMatchExpression()));
        int leftPriority = priorityOf(left);
        int rightPriority = priorityOf(right);
        if (sameExpression && leftPriority == rightPriority) {
            if (hasSameRouteDestination(left, right)) {
                boolean sameSceneScope = sceneSubsumes(left.getMatchScene(), right.getMatchScene())
                        && sceneSubsumes(right.getMatchScene(), left.getMatchScene());
                return buildRouteRuleConflict(
                        left, right, overlappingScene,
                        sameSceneScope ? "DUPLICATE_RULE" : "OVERLAPPING_DUPLICATE",
                        "WARNING", "warn",
                        sameSceneScope
                                ? "场景、优先级、表达式和目标渠道完全相同，重复规则会增加维护成本"
                                : "交集场景内的优先级、表达式和目标渠道相同，存在重复匹配"
                );
            }
            return buildRouteRuleConflict(
                    left, right, overlappingScene, "ROUTE_DIVERGENCE", "BLOCKER", "danger",
                    "同场景、同优先级和同表达式指向不同渠道，运行结果依赖数据库记录顺序"
            );
        }
        if (sameExpression) {
            PaymentRouteRuleConfigDTO shadowing = leftPriority < rightPriority ? left : right;
            PaymentRouteRuleConfigDTO shadowed = leftPriority < rightPriority ? right : left;
            boolean fullyShadowed = rolloutPercentageOf(shadowing) >= 100
                    && sceneSubsumes(shadowing.getMatchScene(), shadowed.getMatchScene());
            return buildRouteRuleConflict(
                    shadowed, shadowing, overlappingScene,
                    fullyShadowed ? "SHADOWED_RULE" : "PARTIAL_SHADOW",
                    "WARNING", "warn",
                    fullyShadowed
                            ? "全部适用场景和表达式均被更高优先级规则覆盖，当前规则不会进入实际路由"
                            : "交集场景和表达式会先命中更高优先级规则，当前规则仅能在其他场景生效"
            );
        }
        if (leftPriority == rightPriority) {
            return buildRouteRuleConflict(
                    left, right, overlappingScene, "PRIORITY_COLLISION", "REVIEW", "info",
                    "场景存在交集且优先级相同；表达式是否互斥需人工复核"
            );
        }
        return null;
    }

    private PaymentRouteRuleConflictDTO buildInvalidExpressionConflict(PaymentRouteRuleConfigDTO rule) {
        return buildRouteRuleConflict(
                rule, null, rule.getMatchScene(), "INVALID_EXPRESSION", "BLOCKER", "danger",
                "启用规则的表达式无法解析，运行时会按不命中跳过"
        );
    }

    private PaymentRouteRuleConflictDTO buildRouteRuleConflict(PaymentRouteRuleConfigDTO rule,
                                                                PaymentRouteRuleConfigDTO conflictingRule,
                                                                String overlappingScene,
                                                                String conflictType,
                                                                String severity,
                                                                String severityType,
                                                                String message) {
        PaymentRouteRuleConflictDTO conflict = new PaymentRouteRuleConflictDTO();
        conflict.setConflictType(conflictType);
        conflict.setSeverity(severity);
        conflict.setSeverityType(severityType);
        conflict.setRuleCode(rule.getRuleCode());
        conflict.setRuleName(rule.getRuleName());
        conflict.setPriority(rule.getPriority());
        if (conflictingRule != null) {
            conflict.setConflictingRuleCode(conflictingRule.getRuleCode());
            conflict.setConflictingRuleName(conflictingRule.getRuleName());
            conflict.setConflictingPriority(conflictingRule.getPriority());
        }
        conflict.setOverlappingScene(overlappingScene);
        conflict.setMessage(message);
        return conflict;
    }

    private String resolveOverlappingScene(String leftScene, String rightScene) {
        Set<String> leftTokens = normalizeSceneTokens(leftScene);
        Set<String> rightTokens = normalizeSceneTokens(rightScene);
        if (leftTokens.isEmpty()) {
            return StringUtils.hasText(rightScene) ? rightScene.trim() : "全部场景";
        }
        if (rightTokens.isEmpty()) {
            return leftScene.trim();
        }
        Set<String> overlap = new java.util.TreeSet<String>(leftTokens);
        overlap.retainAll(rightTokens);
        if (overlap.isEmpty()) {
            return null;
        }
        return String.join(" / ", overlap);
    }

    private boolean sceneSubsumes(String broaderScene, String narrowerScene) {
        Set<String> broaderTokens = normalizeSceneTokens(broaderScene);
        Set<String> narrowerTokens = normalizeSceneTokens(narrowerScene);
        return broaderTokens.isEmpty()
                || (!narrowerTokens.isEmpty() && broaderTokens.containsAll(narrowerTokens));
    }

    private Set<String> normalizeSceneTokens(String scene) {
        Set<String> tokens = new java.util.TreeSet<String>();
        if (!StringUtils.hasText(scene)) {
            return tokens;
        }
        for (String token : scene.split("/")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if ("mobile_web".equals(normalized) || "mobile".equals(normalized) || "h5".equals(normalized)) {
                normalized = "h5";
            } else if ("pc_web".equals(normalized) || "pc".equals(normalized) || "web".equals(normalized)) {
                normalized = "pc";
            } else if ("app_native".equals(normalized) || "app".equals(normalized)) {
                normalized = "app";
            }
            if (StringUtils.hasText(normalized)) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private boolean hasSameRouteDestination(PaymentRouteRuleConfigDTO left, PaymentRouteRuleConfigDTO right) {
        return normalizeCode(left.getTargetChannelCode()).equals(normalizeCode(right.getTargetChannelCode()))
                && normalizeCode(left.getFallbackChannelCode()).equals(normalizeCode(right.getFallbackChannelCode()));
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private int priorityOf(PaymentRouteRuleConfigDTO rule) {
        return rule.getPriority() == null ? 99 : rule.getPriority().intValue();
    }

    private PaymentIssueDutyRosterEntity buildIssueDutyRosterEntity(PaymentIssueDutyRosterUpsertRequestDTO request,
                                                                    String overrideRosterCode) {
        if (request == null) {
            throw new IllegalArgumentException("异常告警值班路由请求不能为空");
        }
        PaymentIssueDutyRosterEntity entity = new PaymentIssueDutyRosterEntity();
        entity.setRosterCode(StringUtils.hasText(overrideRosterCode)
                ? overrideRosterCode.trim()
                : requireText(request.getRosterCode(), "值班路由编码不能为空"));
        entity.setIssueType(requireText(request.getIssueType(), "异常类型不能为空"));
        entity.setSeverity(resolveSeverity(request.getSeverity()));
        entity.setResponsibilityGroup(requireText(request.getResponsibilityGroup(), "责任组不能为空"));
        entity.setReceiver(requireText(request.getReceiver(), "值班接收人不能为空"));
        entity.setNotifyChannels(requireText(request.getNotifyChannels(), "通知通道不能为空"));
        entity.setEscalationLevel(requireText(request.getEscalationLevel(), "升级等级不能为空"));
        entity.setEscalationReceiver(requireText(request.getEscalationReceiver(), "升级接收人不能为空"));
        entity.setEscalationPolicy(requireText(request.getEscalationPolicy(), "升级策略不能为空"));
        entity.setEscalationTimeoutMinutes(resolveEscalationTimeoutMinutes(request.getEscalationTimeoutMinutes()));
        entity.setScheduleTag(requireText(request.getScheduleTag(), "班次标签不能为空"));
        int effectiveStartHour = resolveDutyRosterHour(request.getEffectiveStartHour(), 0, "班次生效开始小时不能为空");
        int effectiveEndHour = resolveDutyRosterHour(request.getEffectiveEndHour(), 23, "班次生效结束小时不能为空");
        entity.setEffectiveStartHour(Integer.valueOf(effectiveStartHour));
        entity.setEffectiveEndHour(Integer.valueOf(effectiveEndHour));
        entity.setWeekdayScope(resolveWeekdayScope(request.getWeekdayScope()));
        entity.setHolidayStrategy(resolveHolidayStrategy(request.getHolidayStrategy()));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private String requireText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private Integer resolvePriority(Integer priority) {
        if (priority == null) {
            return 99;
        }
        if (priority.intValue() < 0) {
            throw new IllegalArgumentException("协议优先级不能小于0");
        }
        return priority;
    }

    private Integer resolveRoutePriority(Integer priority) {
        if (priority == null) {
            return 99;
        }
        if (priority.intValue() < 0) {
            throw new IllegalArgumentException("路由规则优先级不能小于0");
        }
        return priority;
    }

    private int resolveDutyRosterHour(Integer hour, int defaultHour, String message) {
        if (hour == null) {
            return defaultHour;
        }
        int normalizedHour = hour.intValue();
        if (normalizedHour < 0 || normalizedHour > 23) {
            throw new IllegalArgumentException(message);
        }
        return normalizedHour;
    }

    private String resolveSeverity(String severity) {
        String normalizedSeverity = requireText(severity, "严重等级不能为空");
        if (!"P1".equals(normalizedSeverity) && !"P2".equals(normalizedSeverity) && !"P3".equals(normalizedSeverity)) {
            throw new IllegalArgumentException("严重等级仅支持 P1/P2/P3");
        }
        return normalizedSeverity;
    }

    private String resolveWeekdayScope(String weekdayScope) {
        if (!StringUtils.hasText(weekdayScope)) {
            return "1,2,3,4,5,6,7";
        }
        String[] segments = weekdayScope.split(",");
        boolean[] seen = new boolean[8];
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            String trimmed = segment == null ? "" : segment.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            int weekday;
            try {
                weekday = Integer.parseInt(trimmed);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("适用星期范围仅支持 1-7");
            }
            if (weekday < 1 || weekday > 7) {
                throw new IllegalArgumentException("适用星期范围仅支持 1-7");
            }
            if (seen[weekday]) {
                continue;
            }
            seen[weekday] = true;
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(weekday);
        }
        if (builder.length() == 0) {
            throw new IllegalArgumentException("适用星期范围不能为空");
        }
        return builder.toString();
    }

    private String resolveHolidayStrategy(String holidayStrategy) {
        if (!StringUtils.hasText(holidayStrategy)) {
            return "ALL_DAYS";
        }
        String normalizedHolidayStrategy = holidayStrategy.trim();
        if (!"ALL_DAYS".equals(normalizedHolidayStrategy)
                && !"WORKDAY_ONLY".equals(normalizedHolidayStrategy)
                && !"NON_WORKDAY_ONLY".equals(normalizedHolidayStrategy)) {
            throw new IllegalArgumentException("日期策略仅支持 ALL_DAYS/WORKDAY_ONLY/NON_WORKDAY_ONLY");
        }
        return normalizedHolidayStrategy;
    }

    private Integer resolveEscalationTimeoutMinutes(Integer escalationTimeoutMinutes) {
        if (escalationTimeoutMinutes == null) {
            return Integer.valueOf(30);
        }
        int normalizedTimeoutMinutes = escalationTimeoutMinutes.intValue();
        if (normalizedTimeoutMinutes < 5 || normalizedTimeoutMinutes > 1440) {
            throw new IllegalArgumentException("升级超时分钟数必须在 5-1440 之间");
        }
        return Integer.valueOf(normalizedTimeoutMinutes);
    }

    private String resolveProtocolTypeName(String protocolType, String requestProtocolTypeName) {
        String normalizedProtocolType = requireText(protocolType, "协议类型不能为空");
        List<PaymentProtocolTypeOptionDTO> protocolTypeOptions = paymentConfigMapper.findProtocolTypeOptions();
        for (PaymentProtocolTypeOptionDTO protocolTypeOption : protocolTypeOptions) {
            if (normalizedProtocolType.equals(protocolTypeOption.getProtocolType())) {
                return protocolTypeOption.getProtocolTypeName();
            }
        }
        throw new IllegalArgumentException("协议类型未在字典中定义");
    }

    private String resolveStatus(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private String resolveStatusType(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? "danger" : "success";
    }

    private static final class SelfCheckResult {
        private final String status;
        private final String statusType;
        private final String message;

        private SelfCheckResult(String status, String statusType, String message) {
            this.status = status;
            this.statusType = statusType;
            this.message = message;
        }
    }
}
