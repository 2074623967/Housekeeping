package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentChannelReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentChannelUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentChannelVersionDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewDTO;
import com.abc123.hsp.dto.PaymentAlertProviderStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayVersionDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentControlPolicyStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyVersionDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
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
import com.abc123.hsp.dto.PaymentReturnCodeImportItemDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeStatusReviewRequestDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentIssueDutyRosterReviewEntity;
import com.abc123.hsp.entity.PaymentAlertProviderConfigEntity;
import com.abc123.hsp.entity.PaymentAlertProviderReviewEntity;
import com.abc123.hsp.entity.PaymentChannelConfigEntity;
import com.abc123.hsp.entity.PaymentChannelReviewEntity;
import com.abc123.hsp.entity.PaymentChannelVersionEntity;
import com.abc123.hsp.entity.PaymentControlPolicyEntity;
import com.abc123.hsp.entity.PaymentControlPolicyReviewEntity;
import com.abc123.hsp.entity.PaymentControlPolicyVersionEntity;
import com.abc123.hsp.entity.PaymentGatewayConfigEntity;
import com.abc123.hsp.entity.PaymentGatewayReviewEntity;
import com.abc123.hsp.entity.PaymentGatewayVersionEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolReviewEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import com.abc123.hsp.entity.PaymentReturnCodeImportReviewEntity;
import com.abc123.hsp.entity.PaymentReturnCodeReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentConfigService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
    private static final int RETURN_CODE_IMPORT_MAX_BYTES = 512 * 1024;
    private static final int RETURN_CODE_IMPORT_MAX_ROWS = 500;
    private static final List<String> RETURN_CODE_IMPORT_HEADERS = Collections.unmodifiableList(Arrays.asList(
            "channelCode",
            "channelReturnCode",
            "standardizedCode",
            "standardizedMessage",
            "handlingSuggestion",
            "retryable",
            "manualInterventionRequired",
            "mappingVersion",
            "status",
            "priority"));

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
    public List<PaymentChannelReviewDTO> submitChannelStatusReview(
            PaymentChannelStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("支付渠道启停状态不能为空");
        }
        String channelCode = requireImportText(request.getChannelCode(), "渠道编码不能为空", 64)
                .toLowerCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingChannelReviewForUpdate(channelCode) != null) {
            throw new IllegalArgumentException("该支付渠道已有待复核申请");
        }
        PaymentChannelConfigEntity current = paymentConfigMapper.findChannelByCodeForUpdate(channelCode);
        if (current == null) {
            throw new IllegalArgumentException("支付渠道配置不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "支付渠道已启用" : "支付渠道已停用");
        }
        ensureChannelBaseline(current);
        PaymentChannelReviewEntity review = new PaymentChannelReviewEntity();
        review.setRequestNo("CSR" + UUID.randomUUID().toString().replace("-", ""));
        copyChannelToReview(current, review);
        review.setOperationType("STATUS_CHANGE");
        review.setBaseVersionNo(paymentConfigMapper.findLatestChannelVersionNo(channelCode));
        review.setPendingChannelCode(channelCode);
        review.setBaseFingerprint(fingerprintChannel(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        try {
            paymentConfigMapper.insertChannelReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付渠道已有待复核申请", exception);
        }
        return paymentConfigMapper.findChannelReviews();
    }

    @Override
    @Transactional
    public List<PaymentChannelReviewDTO> submitChannelReview(PaymentChannelReviewSubmitRequestDTO request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("支付渠道候选快照不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String channelCode = requireImportText(
                request.getCandidate().getChannelCode(), "渠道编码不能为空", 64).toLowerCase(Locale.ROOT);
        String reason = requireReviewText(request.getCandidate().getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingChannelReviewForUpdate(channelCode) != null) {
            throw new IllegalArgumentException("该支付渠道已有待复核申请");
        }
        PaymentChannelConfigEntity current = paymentConfigMapper.findChannelByCodeForUpdate(channelCode);
        PaymentChannelConfigEntity candidate = buildChannelEntity(request.getCandidate(), current, channelCode);
        PaymentChannelReviewEntity review = new PaymentChannelReviewEntity();
        review.setRequestNo("CSR" + UUID.randomUUID().toString().replace("-", ""));
        copyChannelToReview(candidate, review);
        review.setOperationType(current == null ? "CREATE" : "UPDATE");
        if (current != null) {
            ensureChannelBaseline(current);
            review.setBaseVersionNo(paymentConfigMapper.findLatestChannelVersionNo(channelCode));
        }
        review.setPendingChannelCode(channelCode);
        review.setBaseFingerprint(current == null ? null : fingerprintChannel(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertChannelReview(review);
        return paymentConfigMapper.findChannelReviews();
    }

    @Override
    public List<PaymentChannelReviewDTO> listChannelReviews() {
        return paymentConfigMapper.findChannelReviews();
    }

    @Override
    @Transactional
    public List<PaymentChannelReviewDTO> approveChannelReview(
            String requestNo, PaymentChannelReviewActionRequestDTO request) {
        PaymentChannelReviewEntity review = requirePendingChannelReview(requestNo);
        String reviewer = requireDistinctChannelReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())
                && !"ROLLBACK".equals(review.getOperationType())) {
            throw new IllegalArgumentException("支付渠道复核操作类型不支持");
        }
        if ("ROLLBACK".equals(review.getOperationType())
                && (review.getSourceVersionNo() == null || review.getSourceVersionNo().intValue() <= 0)) {
            throw new IllegalArgumentException("支付渠道回滚候选缺少目标历史版本");
        }
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("支付渠道候选状态不支持");
        }
        PaymentChannelConfigEntity current = paymentConfigMapper.findChannelByCodeForUpdate(review.getChannelCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("支付渠道已存在，复核申请基线已失效");
            }
        } else {
            Integer latestVersionNo = paymentConfigMapper.findLatestChannelVersionNo(review.getChannelCode());
            if (current == null || review.getBaseFingerprint() == null
                    || !review.getBaseFingerprint().equals(fingerprintChannel(current))
                    || !sameInteger(review.getBaseVersionNo(), latestVersionNo)) {
                throw new IllegalArgumentException("支付渠道配置已变化，请重新提交复核申请");
            }
        }
        PaymentChannelConfigEntity candidate = buildChannelEntity(toChannelUpsertRequest(review), null,
                review.getChannelCode());
        int affectedRows = createOperation
                ? paymentConfigMapper.insertChannel(candidate)
                : paymentConfigMapper.updateChannel(candidate);
        if (affectedRows == 0) {
            throw new IllegalArgumentException(createOperation ? "支付渠道新增失败" : "支付渠道配置不存在");
        }
        String versionChangeType = "REVIEW_RELEASE";
        Integer sourceVersionNo = null;
        if ("STATUS_CHANGE".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_STATUS_CHANGE";
        } else if ("ROLLBACK".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_ROLLBACK";
            sourceVersionNo = review.getSourceVersionNo();
        }
        recordChannelVersion(candidate, versionChangeType, sourceVersionNo, review.getRequestReason());
        completeChannelReview(review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findChannelReviews();
    }

    @Override
    @Transactional
    public List<PaymentChannelReviewDTO> rejectChannelReview(
            String requestNo, PaymentChannelReviewActionRequestDTO request) {
        PaymentChannelReviewEntity review = requirePendingChannelReview(requestNo);
        String reviewer = requireDistinctChannelReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeChannelReview(review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findChannelReviews();
    }

    @Override
    public List<PaymentChannelVersionDTO> listChannelVersions(String channelCode) {
        String normalizedChannelCode = requireImportText(channelCode, "渠道编码不能为空", 64)
                .toLowerCase(Locale.ROOT);
        if (paymentConfigMapper.findChannelByCode(normalizedChannelCode) == null) {
            throw new IllegalArgumentException("支付渠道配置不存在");
        }
        return paymentConfigMapper.findChannelVersions(normalizedChannelCode);
    }

    @Override
    @Transactional
    public List<PaymentChannelReviewDTO> submitChannelRollbackReview(
            String channelCode, PaymentChannelRollbackRequestDTO request) {
        String normalizedChannelCode = requireImportText(channelCode, "渠道编码不能为空", 64)
                .toLowerCase(Locale.ROOT);
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "回滚原因不能为空");
        if (paymentConfigMapper.findPendingChannelReviewForUpdate(normalizedChannelCode) != null) {
            throw new IllegalArgumentException("该支付渠道已有待复核申请");
        }
        PaymentChannelConfigEntity current = paymentConfigMapper.findChannelByCodeForUpdate(normalizedChannelCode);
        if (current == null) {
            throw new IllegalArgumentException("支付渠道配置不存在");
        }
        ensureChannelBaseline(current);
        PaymentChannelVersionEntity target = paymentConfigMapper.findChannelVersion(
                normalizedChannelCode, request.getVersionNo());
        if (target == null) {
            throw new IllegalArgumentException("支付渠道历史版本不存在");
        }
        PaymentChannelConfigEntity candidate = copyChannelEntity(target);
        PaymentChannelReviewEntity review = new PaymentChannelReviewEntity();
        review.setRequestNo("CSR" + UUID.randomUUID().toString().replace("-", ""));
        copyChannelToReview(candidate, review);
        review.setOperationType("ROLLBACK");
        review.setBaseVersionNo(paymentConfigMapper.findLatestChannelVersionNo(normalizedChannelCode));
        review.setSourceVersionNo(request.getVersionNo());
        review.setPendingChannelCode(normalizedChannelCode);
        review.setBaseFingerprint(fingerprintChannel(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertChannelReview(review);
        return paymentConfigMapper.findChannelReviews();
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
    public List<PaymentProtocolReviewDTO> submitProtocolStatusReview(
            PaymentProtocolStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("支付协议启停状态不能为空");
        }
        String protocolCode = requireText(request.getProtocolCode(), "协议编码不能为空");
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
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
        PaymentProtocolConfigEntity candidate = copyProtocolEntity(current);
        candidate.setStatus(status);
        candidate.setStatusType(resolveStatusType(request.getEnabled()));
        return submitExistingProtocolReview(
                candidate, requester, changeReason, "STATUS_CHANGE", null);
    }

    @Override
    public List<PaymentProtocolReviewDTO> listProtocolReviews() {
        return paymentConfigMapper.findProtocolReviews();
    }

    @Override
    @Transactional
    public List<PaymentProtocolReviewDTO> submitProtocolReview(PaymentProtocolReviewSubmitRequestDTO request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("支付协议候选快照不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        PaymentProtocolUpsertRequestDTO candidate = request.getCandidate();
        String protocolCode = requireText(candidate.getProtocolCode(), "协议编码不能为空");
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(protocolCode);
        PaymentProtocolConfigEntity candidateEntity = buildProtocolEntity(
                candidate, current == null ? null : protocolCode);
        String reason = requireProtocolChangeReason(candidate.getChangeReason());
        if (paymentConfigMapper.countPendingProtocolReviews(protocolCode) > 0) {
            throw new IllegalArgumentException("该支付协议已有待复核申请");
        }
        Integer baseVersionNo = null;
        String operationType = "CREATE";
        if (current != null) {
            ensureProtocolBaseline(current);
            baseVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(protocolCode);
            operationType = "UPDATE";
        }
        PaymentProtocolReviewEntity review = buildProtocolReview(
                candidateEntity, requester, reason, operationType, baseVersionNo, null);
        insertProtocolReview(review);
        return paymentConfigMapper.findProtocolReviews();
    }

    @Override
    @Transactional
    public List<PaymentProtocolReviewDTO> approveProtocolReview(
            String requestNo, PaymentProtocolReviewActionRequestDTO request) {
        PaymentProtocolReviewEntity review = requirePendingProtocolReview(requestNo);
        String reviewer = requireDistinctProtocolReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())
                && !"ROLLBACK".equals(review.getOperationType())) {
            throw new IllegalArgumentException("支付协议复核操作类型不支持");
        }
        if ("ROLLBACK".equals(review.getOperationType())
                && (review.getSourceVersionNo() == null || review.getSourceVersionNo().intValue() <= 0)) {
            throw new IllegalArgumentException("支付协议回滚候选缺少目标历史版本");
        }
        PaymentProtocolConfigEntity candidate = copyProtocolEntity(review);
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(review.getProtocolCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("协议已存在，复核申请基线已失效");
            }
        } else {
            if (current == null) {
                throw new IllegalArgumentException("协议不存在，复核申请基线已失效");
            }
            Integer latestVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(review.getProtocolCode());
            if (!sameInteger(review.getBaseVersionNo(), latestVersionNo)) {
                throw new IllegalArgumentException("协议已产生新版本，请重新提交复核申请");
            }
        }
        if (current == null) {
            paymentConfigMapper.insertProtocol(candidate);
        } else {
            paymentConfigMapper.updateProtocol(candidate);
        }
        String versionChangeType = "REVIEW_RELEASE";
        Integer sourceVersionNo = null;
        if ("STATUS_CHANGE".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_STATUS_CHANGE";
        } else if ("ROLLBACK".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_ROLLBACK";
            sourceVersionNo = review.getSourceVersionNo();
        }
        recordProtocolVersion(candidate, versionChangeType, sourceVersionNo, review.getRequestReason());
        completeProtocolReview(review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findProtocolReviews();
    }

    @Override
    @Transactional
    public List<PaymentProtocolReviewDTO> rejectProtocolReview(
            String requestNo, PaymentProtocolReviewActionRequestDTO request) {
        PaymentProtocolReviewEntity review = requirePendingProtocolReview(requestNo);
        String reviewer = requireDistinctProtocolReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeProtocolReview(review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findProtocolReviews();
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
    public List<PaymentProtocolReviewDTO> submitProtocolRollbackReview(
            String protocolCode, PaymentProtocolRollbackRequestDTO request) {
        String normalizedProtocolCode = requireText(protocolCode, "协议编码不能为空");
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String changeReason = requireProtocolChangeReason(request.getChangeReason());
        PaymentProtocolConfigEntity current = paymentConfigMapper.findProtocolByCodeForUpdate(normalizedProtocolCode);
        if (current == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        ensureProtocolBaseline(current);
        PaymentProtocolVersionEntity target = paymentConfigMapper.findProtocolVersion(
                normalizedProtocolCode, request.getVersionNo());
        if (target == null) {
            throw new IllegalArgumentException("支付协议历史版本不存在");
        }
        PaymentProtocolConfigEntity candidate = copyProtocolEntity(target);
        return submitExistingProtocolReview(
                candidate, requester, changeReason, "ROLLBACK", request.getVersionNo());
    }

    @Override
    @Transactional
    public List<PaymentIssueDutyRosterReviewDTO> createIssueDutyRoster(
            PaymentIssueDutyRosterUpsertRequestDTO request) {
        return submitIssueDutyRosterReview(request, null);
    }

    @Override
    @Transactional
    public List<PaymentIssueDutyRosterReviewDTO> updateIssueDutyRoster(
            String rosterCode, PaymentIssueDutyRosterUpsertRequestDTO request) {
        return submitIssueDutyRosterReview(request, rosterCode);
    }

    @Override
    public List<PaymentIssueDutyRosterReviewDTO> listIssueDutyRosterReviews() {
        return paymentConfigMapper.findIssueDutyRosterReviews();
    }

    @Override
    @Transactional
    public List<PaymentIssueDutyRosterReviewDTO> approveIssueDutyRosterReview(
            String requestNo, PaymentIssueDutyRosterReviewActionRequestDTO request) {
        PaymentIssueDutyRosterReviewEntity review = requirePendingIssueDutyRosterReview(requestNo);
        String reviewer = requireDistinctIssueDutyRosterReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())) {
            throw new IllegalArgumentException("异常告警值班路由复核操作类型不支持");
        }
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("异常告警值班路由候选状态不支持");
        }
        PaymentIssueDutyRosterEntity current = paymentConfigMapper.findIssueDutyRosterByCodeForUpdate(
                review.getRosterCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("异常告警值班路由已存在，复核申请基线已失效");
            }
        } else if (current == null || review.getBaseFingerprint() == null
                || !review.getBaseFingerprint().equals(fingerprintIssueDutyRoster(current))) {
            throw new IllegalArgumentException("异常告警值班路由配置已变化，请重新提交复核申请");
        }
        PaymentIssueDutyRosterEntity candidate = buildIssueDutyRosterEntity(
                toIssueDutyRosterUpsertRequest(review), review.getRosterCode());
        int affectedRows;
        try {
            affectedRows = createOperation
                    ? paymentConfigMapper.insertIssueDutyRoster(candidate)
                    : paymentConfigMapper.updateIssueDutyRoster(candidate);
        } catch (DuplicateKeyException exception) {
            if (createOperation) {
                throw new IllegalArgumentException("异常告警值班路由已存在，复核申请基线已失效", exception);
            }
            throw exception;
        }
        if (affectedRows == 0) {
            throw new IllegalArgumentException(createOperation
                    ? "异常告警值班路由新增失败" : "异常告警值班路由配置不存在");
        }
        completeIssueDutyRosterReview(
                review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findIssueDutyRosterReviews();
    }

    @Override
    @Transactional
    public List<PaymentIssueDutyRosterReviewDTO> rejectIssueDutyRosterReview(
            String requestNo, PaymentIssueDutyRosterReviewActionRequestDTO request) {
        PaymentIssueDutyRosterReviewEntity review = requirePendingIssueDutyRosterReview(requestNo);
        String reviewer = requireDistinctIssueDutyRosterReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeIssueDutyRosterReview(
                review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findIssueDutyRosterReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeReviewDTO> submitReturnCodeStatusReview(
            PaymentReturnCodeStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("渠道返回码启停状态不能为空");
        }
        String channelCode = requireImportText(request.getChannelCode(), "渠道编码不能为空", 64)
                .toLowerCase(Locale.ROOT);
        String channelReturnCode = requireImportText(
                request.getChannelReturnCode(), "渠道返回码不能为空", 64).toUpperCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        String pendingMappingKey = sha256(returnCodeMappingKey(channelCode, channelReturnCode));
        if (paymentConfigMapper.findPendingReturnCodeReviewForUpdate(pendingMappingKey) != null) {
            throw new IllegalArgumentException("该渠道返回码已有待复核申请");
        }
        PaymentChannelReturnCodeConfigDTO current = paymentConfigMapper.findReturnCodeMappingForUpdate(
                channelCode, channelReturnCode);
        if (current == null) {
            throw new IllegalArgumentException("渠道返回码映射配置不存在");
        }
        if ("ARCHIVED".equals(current.getArchiveStatus())) {
            throw new IllegalArgumentException("已归档渠道返回码禁止提交启停复核");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "渠道返回码映射已启用" : "渠道返回码映射已停用");
        }
        PaymentReturnCodeReviewEntity review = new PaymentReturnCodeReviewEntity();
        review.setRequestNo("RCS" + UUID.randomUUID().toString().replace("-", ""));
        review.setChannelCode(current.getChannelCode());
        review.setChannelReturnCode(current.getChannelReturnCode());
        review.setStandardizedCode(current.getStandardizedCode());
        review.setStandardizedMessage(current.getStandardizedMessage());
        review.setMappingVersion(current.getMappingVersion());
        review.setPendingMappingKey(pendingMappingKey);
        review.setBaseFingerprint(fingerprintReturnCodeMapping(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        try {
            paymentConfigMapper.insertReturnCodeReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该渠道返回码已有待复核申请", exception);
        }
        return paymentConfigMapper.findReturnCodeReviews();
    }

    @Override
    public List<PaymentReturnCodeReviewDTO> listReturnCodeReviews() {
        return paymentConfigMapper.findReturnCodeReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeReviewDTO> approveReturnCodeReview(
            String requestNo, PaymentReturnCodeReviewActionRequestDTO request) {
        PaymentReturnCodeReviewEntity review = requirePendingReturnCodeReview(requestNo);
        String reviewer = requireDistinctReturnCodeReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("渠道返回码候选状态不支持");
        }
        PaymentChannelReturnCodeConfigDTO current = paymentConfigMapper.findReturnCodeMappingForUpdate(
                review.getChannelCode(), review.getChannelReturnCode());
        if (current == null || !review.getBaseFingerprint().equals(fingerprintReturnCodeMapping(current))) {
            throw new IllegalArgumentException("渠道返回码映射已变化，请重新提交复核申请");
        }
        int affectedRows = paymentConfigMapper.updateReturnCodeMappingStatus(
                review.getChannelCode(),
                review.getChannelReturnCode(),
                review.getCandidateStatus(),
                review.getCandidateStatusType());
        if (affectedRows == 0) {
            throw new IllegalArgumentException("渠道返回码映射配置不存在");
        }
        completeReturnCodeReview(
                review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findReturnCodeReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeReviewDTO> rejectReturnCodeReview(
            String requestNo, PaymentReturnCodeReviewActionRequestDTO request) {
        PaymentReturnCodeReviewEntity review = requirePendingReturnCodeReview(requestNo);
        String reviewer = requireDistinctReturnCodeReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeReturnCodeReview(
                review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findReturnCodeReviews();
    }

    @Override
    public PaymentReturnCodeImportResultDTO previewReturnCodeImport(PaymentReturnCodeImportRequestDTO request) {
        return buildReturnCodeImportResult(
                request,
                paymentConfigMapper.findReturnCodeMappings(),
                resolveReturnCodeImportChannels());
    }

    @Override
    public List<PaymentReturnCodeImportReviewDTO> listReturnCodeImportReviews() {
        return paymentConfigMapper.findReturnCodeImportReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeImportReviewDTO> submitReturnCodeImportReview(
            PaymentReturnCodeImportReviewSubmitRequestDTO request) {
        String requester = requireReviewIdentity(
                request == null ? null : request.getRequester(), "申请人声明不能为空");
        String changeReason = requireReviewText(request.getChangeReason(), "导入原因不能为空");
        String expectedBaseline = requireImportText(
                request.getBaselineFingerprint(), "请先执行预检并携带基线指纹", 64);
        PaymentReturnCodeImportRequestDTO importRequest = returnCodeImportRequest(request.getCsvContent());
        if (paymentConfigMapper.findPendingReturnCodeImportReviewForUpdate() != null) {
            throw new IllegalArgumentException("已有待复核的返回码导入申请");
        }
        List<PaymentChannelReturnCodeConfigDTO> currentMappings =
                paymentConfigMapper.findReturnCodeMappingsForUpdate();
        PaymentReturnCodeImportResultDTO result = buildReturnCodeImportResult(
                importRequest, currentMappings, resolveReturnCodeImportChannels());
        if (!expectedBaseline.equals(result.getBaselineFingerprint())) {
            throw new IllegalArgumentException("返回码配置已变化，请重新预检后再提交复核");
        }
        if (!Boolean.TRUE.equals(result.getValid())) {
            throw new IllegalArgumentException("CSV 存在无效行，未提交复核申请");
        }
        if (result.getCreateCount().intValue() + result.getUpdateCount().intValue() == 0) {
            throw new IllegalArgumentException("CSV 与当前返回码配置一致，无需提交复核");
        }
        PaymentReturnCodeImportReviewEntity review = new PaymentReturnCodeImportReviewEntity();
        review.setRequestNo("RCR" + UUID.randomUUID().toString().replace("-", ""));
        review.setPendingImportKey("RETURN_CODE_IMPORT");
        review.setCsvContent(normalizeCsvContent(request.getCsvContent()));
        review.setSourceSha256(sha256(review.getCsvContent()));
        review.setBaselineFingerprint(result.getBaselineFingerprint());
        review.setTotalCount(result.getTotalCount());
        review.setCreateCount(result.getCreateCount());
        review.setUpdateCount(result.getUpdateCount());
        review.setUnchangedCount(result.getUnchangedCount());
        review.setRequester(requester);
        review.setRequestReason(changeReason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        try {
            paymentConfigMapper.insertReturnCodeImportReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("已有待复核的返回码导入申请", exception);
        }
        return paymentConfigMapper.findReturnCodeImportReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeImportReviewDTO> approveReturnCodeImportReview(
            String requestNo, PaymentReturnCodeImportReviewActionRequestDTO request) {
        PaymentReturnCodeImportReviewEntity review = requirePendingReturnCodeImportReview(requestNo);
        String reviewer = requireDistinctReturnCodeImportReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        String storedCsv = normalizeCsvContent(review.getCsvContent());
        if (!sha256(storedCsv).equals(review.getSourceSha256())) {
            throw new IllegalArgumentException("返回码导入候选内容校验失败，请驳回并重新提交");
        }
        List<PaymentChannelReturnCodeConfigDTO> currentMappings =
                paymentConfigMapper.findReturnCodeMappingsForUpdate();
        PaymentReturnCodeImportResultDTO result = buildReturnCodeImportResult(
                returnCodeImportRequest(storedCsv),
                currentMappings,
                resolveReturnCodeImportChannels());
        if (!review.getBaselineFingerprint().equals(result.getBaselineFingerprint())) {
            throw new IllegalArgumentException("返回码配置已变化，请驳回并重新预检后提交");
        }
        if (!Boolean.TRUE.equals(result.getValid())) {
            throw new IllegalArgumentException("返回码导入候选已失效，请驳回并重新提交");
        }
        int appliedCount = 0;
        try {
            for (PaymentReturnCodeImportItemDTO item : result.getItems()) {
                if ("CREATE".equals(item.getAction()) || "UPDATE".equals(item.getAction())) {
                    paymentConfigMapper.upsertReturnCodeMapping(item.getCandidate());
                    appliedCount++;
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("返回码配置发生并发冲突，请重新预检后再应用", exception);
        }
        List<PaymentChannelReturnCodeConfigDTO> resultingMappings = paymentConfigMapper.findReturnCodeMappings();
        String resultFingerprint = fingerprintReturnCodeMappings(resultingMappings);
        String batchNo = "RCI" + UUID.randomUUID().toString().replace("-", "");
        result.setApplied(Boolean.TRUE);
        result.setAppliedCount(Integer.valueOf(appliedCount));
        result.setBatchNo(batchNo);
        result.setResultFingerprint(resultFingerprint);
        paymentConfigMapper.insertReturnCodeImportBatch(
                batchNo,
                review.getRequester(),
                review.getRequestReason(),
                review.getSourceSha256(),
                result.getBaselineFingerprint(),
                resultFingerprint,
                result.getTotalCount(),
                result.getCreateCount(),
                result.getUpdateCount(),
                result.getUnchangedCount(),
                result.getAppliedCount(),
                appliedCount == 0 ? "NO_CHANGE" : "APPLIED",
                appliedCount == 0 ? "info" : "success");
        completeReturnCodeImportReview(
                review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment, batchNo);
        return paymentConfigMapper.findReturnCodeImportReviews();
    }

    @Override
    @Transactional
    public List<PaymentReturnCodeImportReviewDTO> rejectReturnCodeImportReview(
            String requestNo, PaymentReturnCodeImportReviewActionRequestDTO request) {
        PaymentReturnCodeImportReviewEntity review = requirePendingReturnCodeImportReview(requestNo);
        String reviewer = requireDistinctReturnCodeImportReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeReturnCodeImportReview(
                review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment, null);
        return paymentConfigMapper.findReturnCodeImportReviews();
    }

    @Override
    @Transactional
    public List<PaymentGatewayReviewDTO> submitGatewayStatusReview(
            PaymentGatewayStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("支付网关启停状态不能为空");
        }
        String gatewayCode = requireImportText(request.getGatewayCode(), "支付网关编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingGatewayReviewForUpdate(gatewayCode) != null) {
            throw new IllegalArgumentException("该支付网关已有待复核申请");
        }
        PaymentGatewayConfigEntity current = paymentConfigMapper.findGatewayByCodeForUpdate(gatewayCode);
        if (current == null) {
            throw new IllegalArgumentException("支付网关接入配置不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "支付网关已启用" : "支付网关已停用");
        }
        ensureGatewayBaseline(current);
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo("GWR" + UUID.randomUUID().toString().replace("-", ""));
        copyGatewayToReview(current, review);
        review.setOperationType("STATUS_CHANGE");
        review.setBaseVersionNo(paymentConfigMapper.findLatestGatewayVersionNo(gatewayCode));
        review.setPendingGatewayCode(gatewayCode);
        review.setBaseFingerprint(fingerprintGateway(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        try {
            paymentConfigMapper.insertGatewayReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付网关已有待复核申请", exception);
        }
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    @Transactional
    public List<PaymentGatewayReviewDTO> submitGatewayReview(PaymentGatewayReviewSubmitRequestDTO request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("支付网关候选快照不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String gatewayCode = requireImportText(
                request.getCandidate().getGatewayCode(), "支付网关编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        String reason = requireReviewText(request.getCandidate().getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingGatewayReviewForUpdate(gatewayCode) != null) {
            throw new IllegalArgumentException("该支付网关已有待复核申请");
        }
        PaymentGatewayConfigEntity current = paymentConfigMapper.findGatewayByCodeForUpdate(gatewayCode);
        PaymentGatewayConfigEntity candidate = buildGatewayEntity(request.getCandidate(), gatewayCode);
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo("GWR" + UUID.randomUUID().toString().replace("-", ""));
        copyGatewayToReview(candidate, review);
        review.setOperationType(current == null ? "CREATE" : "UPDATE");
        if (current != null) {
            ensureGatewayBaseline(current);
            review.setBaseVersionNo(paymentConfigMapper.findLatestGatewayVersionNo(gatewayCode));
        }
        review.setPendingGatewayCode(gatewayCode);
        review.setBaseFingerprint(current == null ? null : fingerprintGateway(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertGatewayReview(review);
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    public List<PaymentGatewayReviewDTO> listGatewayReviews() {
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    @Transactional
    public List<PaymentGatewayReviewDTO> approveGatewayReview(
            String requestNo, PaymentGatewayReviewActionRequestDTO request) {
        PaymentGatewayReviewEntity review = requirePendingGatewayReview(requestNo);
        String reviewer = requireDistinctGatewayReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())
                && !"ROLLBACK".equals(review.getOperationType())) {
            throw new IllegalArgumentException("支付网关复核操作类型不支持");
        }
        if ("ROLLBACK".equals(review.getOperationType())
                && (review.getSourceVersionNo() == null || review.getSourceVersionNo().intValue() <= 0)) {
            throw new IllegalArgumentException("支付网关回滚候选缺少目标历史版本");
        }
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("支付网关候选状态不支持");
        }
        PaymentGatewayConfigEntity current = paymentConfigMapper.findGatewayByCodeForUpdate(review.getGatewayCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("支付网关已存在，复核申请基线已失效");
            }
        } else {
            Integer latestVersionNo = paymentConfigMapper.findLatestGatewayVersionNo(review.getGatewayCode());
            if (current == null || review.getBaseFingerprint() == null
                    || !review.getBaseFingerprint().equals(fingerprintGateway(current))
                    || !sameInteger(review.getBaseVersionNo(), latestVersionNo)) {
                throw new IllegalArgumentException("支付网关配置已变化，请重新提交复核申请");
            }
        }
        PaymentGatewayConfigEntity candidate = buildGatewayEntity(
                toGatewayUpsertRequest(review), review.getGatewayCode());
        int affectedRows;
        try {
            affectedRows = createOperation
                    ? paymentConfigMapper.insertGateway(candidate)
                    : paymentConfigMapper.updateGateway(candidate);
        } catch (DuplicateKeyException exception) {
            if (createOperation) {
                throw new IllegalArgumentException("支付网关已存在，复核申请基线已失效", exception);
            }
            throw exception;
        }
        if (affectedRows == 0) {
            throw new IllegalArgumentException(createOperation ? "支付网关新增失败" : "支付网关接入配置不存在");
        }
        String versionChangeType = "REVIEW_RELEASE";
        Integer sourceVersionNo = null;
        if ("STATUS_CHANGE".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_STATUS_CHANGE";
        } else if ("ROLLBACK".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_ROLLBACK";
            sourceVersionNo = review.getSourceVersionNo();
        }
        recordGatewayVersion(candidate, versionChangeType, sourceVersionNo, review.getRequestReason());
        completeGatewayReview(review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    @Transactional
    public List<PaymentGatewayReviewDTO> rejectGatewayReview(
            String requestNo, PaymentGatewayReviewActionRequestDTO request) {
        PaymentGatewayReviewEntity review = requirePendingGatewayReview(requestNo);
        String reviewer = requireDistinctGatewayReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeGatewayReview(review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    public List<PaymentGatewayVersionDTO> listGatewayVersions(String gatewayCode) {
        String normalizedGatewayCode = requireImportText(gatewayCode, "支付网关编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        if (paymentConfigMapper.findGatewayByCode(normalizedGatewayCode) == null) {
            throw new IllegalArgumentException("支付网关接入配置不存在");
        }
        return paymentConfigMapper.findGatewayVersions(normalizedGatewayCode);
    }

    @Override
    @Transactional
    public List<PaymentGatewayReviewDTO> submitGatewayRollbackReview(
            String gatewayCode, PaymentGatewayRollbackRequestDTO request) {
        String normalizedGatewayCode = requireImportText(gatewayCode, "支付网关编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "回滚原因不能为空");
        if (paymentConfigMapper.findPendingGatewayReviewForUpdate(normalizedGatewayCode) != null) {
            throw new IllegalArgumentException("该支付网关已有待复核申请");
        }
        PaymentGatewayConfigEntity current =
                paymentConfigMapper.findGatewayByCodeForUpdate(normalizedGatewayCode);
        if (current == null) {
            throw new IllegalArgumentException("支付网关接入配置不存在");
        }
        ensureGatewayBaseline(current);
        PaymentGatewayVersionEntity target = paymentConfigMapper.findGatewayVersion(
                normalizedGatewayCode, request.getVersionNo());
        if (target == null) {
            throw new IllegalArgumentException("支付网关历史版本不存在");
        }
        PaymentGatewayConfigEntity candidate = copyGatewayEntity(target);
        PaymentGatewayReviewEntity review = new PaymentGatewayReviewEntity();
        review.setRequestNo("GWR" + UUID.randomUUID().toString().replace("-", ""));
        copyGatewayToReview(candidate, review);
        review.setOperationType("ROLLBACK");
        review.setBaseVersionNo(paymentConfigMapper.findLatestGatewayVersionNo(normalizedGatewayCode));
        review.setSourceVersionNo(request.getVersionNo());
        review.setPendingGatewayCode(normalizedGatewayCode);
        review.setBaseFingerprint(fingerprintGateway(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertGatewayReview(review);
        return paymentConfigMapper.findGatewayReviews();
    }

    @Override
    @Transactional
    public List<PaymentControlPolicyReviewDTO> submitControlPolicyStatusReview(
            PaymentControlPolicyStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("支付控制策略启停状态不能为空");
        }
        String sourceAppId = requireImportText(request.getSourceAppId(), "来源应用标识不能为空", 64)
                .toLowerCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingControlPolicyReviewForUpdate(sourceAppId) != null) {
            throw new IllegalArgumentException("该支付控制策略已有待复核申请");
        }
        PaymentControlPolicyEntity current =
                paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate(sourceAppId);
        if (current == null) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "支付控制策略已启用" : "支付控制策略已停用");
        }
        ensureControlPolicyBaseline(current);
        PaymentControlPolicyReviewEntity review = new PaymentControlPolicyReviewEntity();
        review.setRequestNo("CPR" + UUID.randomUUID().toString().replace("-", ""));
        copyControlPolicyToReview(current, review);
        review.setOperationType("STATUS_CHANGE");
        review.setBaseVersionNo(paymentConfigMapper.findLatestControlPolicyVersionNo(sourceAppId));
        review.setPendingSourceAppId(sourceAppId);
        review.setBaseFingerprint(fingerprintControlPolicy(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertControlPolicyReview(review);
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    @Transactional
    public List<PaymentControlPolicyReviewDTO> submitControlPolicyReview(
            PaymentControlPolicyReviewSubmitRequestDTO request) {
        if (request == null || request.getCandidate() == null) {
            throw new IllegalArgumentException("支付控制策略候选快照不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String sourceAppId = requireImportText(
                request.getCandidate().getSourceAppId(), "来源应用标识不能为空", 64)
                .toLowerCase(Locale.ROOT);
        String reason = requireReviewText(request.getCandidate().getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingControlPolicyReviewForUpdate(sourceAppId) != null) {
            throw new IllegalArgumentException("该支付控制策略已有待复核申请");
        }
        PaymentControlPolicyEntity current =
                paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate(sourceAppId);
        PaymentControlPolicyEntity candidate =
                buildControlPolicyEntity(request.getCandidate(), current, sourceAppId);
        PaymentControlPolicyReviewEntity review = new PaymentControlPolicyReviewEntity();
        review.setRequestNo("CPR" + UUID.randomUUID().toString().replace("-", ""));
        copyControlPolicyToReview(candidate, review);
        review.setOperationType(current == null ? "CREATE" : "UPDATE");
        if (current != null) {
            ensureControlPolicyBaseline(current);
            review.setBaseVersionNo(paymentConfigMapper.findLatestControlPolicyVersionNo(sourceAppId));
        }
        review.setPendingSourceAppId(sourceAppId);
        review.setBaseFingerprint(current == null ? null : fingerprintControlPolicy(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertControlPolicyReview(review);
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    public List<PaymentControlPolicyReviewDTO> listControlPolicyReviews() {
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    @Transactional
    public List<PaymentControlPolicyReviewDTO> approveControlPolicyReview(
            String requestNo, PaymentControlPolicyReviewActionRequestDTO request) {
        PaymentControlPolicyReviewEntity review = requirePendingControlPolicyReview(requestNo);
        String reviewer = requireDistinctControlPolicyReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())
                && !"ROLLBACK".equals(review.getOperationType())) {
            throw new IllegalArgumentException("支付控制策略复核操作类型不支持");
        }
        if ("ROLLBACK".equals(review.getOperationType())
                && (review.getSourceVersionNo() == null || review.getSourceVersionNo().intValue() <= 0)) {
            throw new IllegalArgumentException("支付控制策略回滚候选缺少目标历史版本");
        }
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("支付控制策略候选状态不支持");
        }
        PaymentControlPolicyEntity current =
                paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate(review.getSourceAppId());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("支付控制策略已存在，复核申请基线已失效");
            }
        } else {
            Integer latestVersionNo = paymentConfigMapper.findLatestControlPolicyVersionNo(review.getSourceAppId());
            if (current == null || review.getBaseFingerprint() == null
                    || !review.getBaseFingerprint().equals(fingerprintControlPolicy(current))
                    || !sameInteger(review.getBaseVersionNo(), latestVersionNo)) {
                throw new IllegalArgumentException("支付控制策略配置已变化，请重新提交复核申请");
            }
        }
        PaymentControlPolicyEntity candidate = buildControlPolicyEntity(
                toControlPolicyUpsertRequest(review), null, review.getSourceAppId());
        int affectedRows;
        try {
            affectedRows = createOperation
                    ? paymentConfigMapper.insertControlPolicy(candidate)
                    : paymentConfigMapper.updateControlPolicy(candidate);
        } catch (DuplicateKeyException exception) {
            if (createOperation) {
                throw new IllegalArgumentException("支付控制策略已存在，复核申请基线已失效", exception);
            }
            throw exception;
        }
        if (affectedRows == 0) {
            throw new IllegalArgumentException(createOperation
                    ? "支付控制策略新增失败" : "支付控制策略配置不存在");
        }
        String versionChangeType = "REVIEW_RELEASE";
        Integer sourceVersionNo = null;
        if ("STATUS_CHANGE".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_STATUS_CHANGE";
        } else if ("ROLLBACK".equals(review.getOperationType())) {
            versionChangeType = "REVIEW_ROLLBACK";
            sourceVersionNo = review.getSourceVersionNo();
        }
        recordControlPolicyVersion(candidate, versionChangeType, sourceVersionNo, review.getRequestReason());
        completeControlPolicyReview(
                review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    @Transactional
    public List<PaymentControlPolicyReviewDTO> rejectControlPolicyReview(
            String requestNo, PaymentControlPolicyReviewActionRequestDTO request) {
        PaymentControlPolicyReviewEntity review = requirePendingControlPolicyReview(requestNo);
        String reviewer = requireDistinctControlPolicyReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeControlPolicyReview(
                review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    public List<PaymentControlPolicyVersionDTO> listControlPolicyVersions(String sourceAppId) {
        String normalizedSourceAppId = requireImportText(
                sourceAppId, "来源应用标识不能为空", 64).toLowerCase(Locale.ROOT);
        if (paymentConfigMapper.findControlPolicyBySourceAppId(normalizedSourceAppId) == null) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        return paymentConfigMapper.findControlPolicyVersions(normalizedSourceAppId);
    }

    @Override
    @Transactional
    public List<PaymentControlPolicyReviewDTO> submitControlPolicyRollbackReview(
            String sourceAppId, PaymentControlPolicyRollbackRequestDTO request) {
        String normalizedSourceAppId = requireImportText(
                sourceAppId, "来源应用标识不能为空", 64).toLowerCase(Locale.ROOT);
        if (request == null || request.getVersionNo() == null || request.getVersionNo().intValue() <= 0) {
            throw new IllegalArgumentException("回滚版本号必须大于0");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "回滚原因不能为空");
        if (paymentConfigMapper.findPendingControlPolicyReviewForUpdate(normalizedSourceAppId) != null) {
            throw new IllegalArgumentException("该支付控制策略已有待复核申请");
        }
        PaymentControlPolicyEntity current =
                paymentConfigMapper.findControlPolicyBySourceAppIdForUpdate(normalizedSourceAppId);
        if (current == null) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        ensureControlPolicyBaseline(current);
        PaymentControlPolicyVersionEntity target = paymentConfigMapper.findControlPolicyVersion(
                normalizedSourceAppId, request.getVersionNo());
        if (target == null) {
            throw new IllegalArgumentException("支付控制策略历史版本不存在");
        }
        PaymentControlPolicyEntity candidate = copyControlPolicyEntity(target);
        PaymentControlPolicyReviewEntity review = new PaymentControlPolicyReviewEntity();
        review.setRequestNo("CPR" + UUID.randomUUID().toString().replace("-", ""));
        copyControlPolicyToReview(candidate, review);
        review.setOperationType("ROLLBACK");
        review.setBaseVersionNo(paymentConfigMapper.findLatestControlPolicyVersionNo(normalizedSourceAppId));
        review.setSourceVersionNo(request.getVersionNo());
        review.setPendingSourceAppId(normalizedSourceAppId);
        review.setBaseFingerprint(fingerprintControlPolicy(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertControlPolicyReview(review);
        return paymentConfigMapper.findControlPolicyReviews();
    }

    @Override
    @Transactional
    public List<PaymentAlertProviderReviewDTO> createAlertProvider(
            PaymentAlertProviderUpsertRequestDTO request) {
        return submitAlertProviderReview(request, null);
    }

    @Override
    @Transactional
    public List<PaymentAlertProviderReviewDTO> updateAlertProvider(
            String providerCode, PaymentAlertProviderUpsertRequestDTO request) {
        return submitAlertProviderReview(request, providerCode);
    }

    @Override
    @Transactional
    public List<PaymentAlertProviderReviewDTO> toggleAlertProvider(
            PaymentAlertProviderStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("告警通知供应商启停状态不能为空");
        }
        String providerCode = requireImportText(request.getProviderCode(), "供应商配置编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingAlertProviderReviewForUpdate(providerCode) != null) {
            throw new IllegalArgumentException("该告警通知供应商已有待复核申请");
        }
        PaymentAlertProviderConfigEntity current =
                paymentConfigMapper.findAlertProviderByCodeForUpdate(providerCode);
        if (current == null) {
            throw new IllegalArgumentException("告警通知供应商配置不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "告警通知供应商已启用" : "告警通知供应商已停用");
        }
        PaymentAlertProviderReviewEntity review = new PaymentAlertProviderReviewEntity();
        copyAlertProviderToReview(current, review);
        review.setRequestNo("APR" + UUID.randomUUID().toString().replace("-", ""));
        review.setOperationType("STATUS_CHANGE");
        review.setPendingProviderCode(providerCode);
        review.setBaseFingerprint(fingerprintAlertProvider(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertAlertProviderReview(review);
        return paymentConfigMapper.findAlertProviderReviews();
    }

    @Override
    public List<PaymentAlertProviderReviewDTO> listAlertProviderReviews() {
        return paymentConfigMapper.findAlertProviderReviews();
    }

    @Override
    @Transactional
    public List<PaymentAlertProviderReviewDTO> approveAlertProviderReview(
            String requestNo, PaymentAlertProviderReviewActionRequestDTO request) {
        PaymentAlertProviderReviewEntity review = requirePendingAlertProviderReview(requestNo);
        String reviewer = requireDistinctAlertProviderReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "复核意见不能为空");
        boolean createOperation = "CREATE".equals(review.getOperationType());
        if (!createOperation
                && !"UPDATE".equals(review.getOperationType())
                && !"STATUS_CHANGE".equals(review.getOperationType())) {
            throw new IllegalArgumentException("告警通知供应商复核操作类型不支持");
        }
        if (!STATUS_ENABLED.equals(review.getCandidateStatus())
                && !STATUS_DISABLED.equals(review.getCandidateStatus())) {
            throw new IllegalArgumentException("告警通知供应商候选状态不支持");
        }
        PaymentAlertProviderConfigEntity current =
                paymentConfigMapper.findAlertProviderByCodeForUpdate(review.getProviderCode());
        if (createOperation) {
            if (current != null) {
                throw new IllegalArgumentException("告警通知供应商已存在，复核申请基线已失效");
            }
        } else if (current == null || review.getBaseFingerprint() == null
                || !review.getBaseFingerprint().equals(fingerprintAlertProvider(current))) {
            throw new IllegalArgumentException("告警通知供应商配置已变化，请重新提交复核申请");
        }
        PaymentAlertProviderConfigEntity candidate = buildAlertProviderEntity(
                toAlertProviderUpsertRequest(review), review.getProviderCode());
        int affectedRows;
        try {
            affectedRows = createOperation
                    ? paymentConfigMapper.insertAlertProvider(candidate)
                    : paymentConfigMapper.updateAlertProvider(candidate);
        } catch (DuplicateKeyException exception) {
            if (createOperation) {
                throw new IllegalArgumentException("告警通知供应商已存在，复核申请基线已失效", exception);
            }
            throw exception;
        }
        if (affectedRows == 0) {
            throw new IllegalArgumentException(createOperation
                    ? "告警通知供应商新增失败" : "告警通知供应商配置不存在");
        }
        completeAlertProviderReview(
                review.getRequestNo(), "APPROVED", "success", reviewer, reviewComment);
        return paymentConfigMapper.findAlertProviderReviews();
    }

    @Override
    @Transactional
    public List<PaymentAlertProviderReviewDTO> rejectAlertProviderReview(
            String requestNo, PaymentAlertProviderReviewActionRequestDTO request) {
        PaymentAlertProviderReviewEntity review = requirePendingAlertProviderReview(requestNo);
        String reviewer = requireDistinctAlertProviderReviewer(review, request);
        String reviewComment = requireReviewText(
                request == null ? null : request.getReviewComment(), "驳回原因不能为空");
        completeAlertProviderReview(
                review.getRequestNo(), "REJECTED", "danger", reviewer, reviewComment);
        return paymentConfigMapper.findAlertProviderReviews();
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
    public List<PaymentIssueDutyRosterReviewDTO> toggleIssueDutyRoster(
            PaymentIssueDutyRosterStatusReviewRequestDTO request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("异常告警值班路由启停状态不能为空");
        }
        String rosterCode = requireImportText(request.getRosterCode(), "值班路由编码不能为空", 64)
                .toUpperCase(Locale.ROOT);
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        if (paymentConfigMapper.findPendingIssueDutyRosterReviewForUpdate(rosterCode) != null) {
            throw new IllegalArgumentException("该异常告警值班路由已有待复核申请");
        }
        PaymentIssueDutyRosterEntity current =
                paymentConfigMapper.findIssueDutyRosterByCodeForUpdate(rosterCode);
        if (current == null) {
            throw new IllegalArgumentException("异常告警值班路由配置不存在");
        }
        String candidateStatus = resolveStatus(request.getEnabled());
        if (candidateStatus.equals(current.getStatus())) {
            throw new IllegalArgumentException(candidateStatus.equals(STATUS_ENABLED)
                    ? "异常告警值班路由已启用" : "异常告警值班路由已停用");
        }
        PaymentIssueDutyRosterReviewEntity review = new PaymentIssueDutyRosterReviewEntity();
        copyIssueDutyRosterToReview(current, review);
        review.setRequestNo("DRR" + UUID.randomUUID().toString().replace("-", ""));
        review.setOperationType("STATUS_CHANGE");
        review.setPendingRosterCode(rosterCode);
        review.setBaseFingerprint(fingerprintIssueDutyRoster(current));
        review.setCandidateStatus(candidateStatus);
        review.setCandidateStatusType(resolveStatusType(request.getEnabled()));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertIssueDutyRosterReview(review);
        return paymentConfigMapper.findIssueDutyRosterReviews();
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
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private PaymentProtocolReviewEntity buildProtocolReview(PaymentProtocolConfigEntity candidate,
                                                             String requester,
                                                             String reason,
                                                             String operationType,
                                                             Integer baseVersionNo,
                                                             Integer sourceVersionNo) {
        PaymentProtocolReviewEntity review = new PaymentProtocolReviewEntity();
        review.setRequestNo("PRV" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        review.setProtocolCode(candidate.getProtocolCode());
        review.setBaseVersionNo(baseVersionNo);
        review.setSourceVersionNo(sourceVersionNo);
        review.setOperationType(operationType);
        review.setProtocolName(candidate.getProtocolName());
        review.setProtocolType(candidate.getProtocolType());
        review.setProtocolTypeName(candidate.getProtocolTypeName());
        review.setTemplateCode(candidate.getTemplateCode());
        review.setTemplateName(candidate.getTemplateName());
        review.setTemplateVersion(candidate.getTemplateVersion());
        review.setSignMode(candidate.getSignMode());
        review.setSignElementSpec(candidate.getSignElementSpec());
        review.setESignatureProvider(candidate.getESignatureProvider());
        review.setSceneScope(candidate.getSceneScope());
        review.setChannelScope(candidate.getChannelScope());
        review.setMerchantAckRequired(candidate.getMerchantAckRequired());
        review.setRiskControlTag(candidate.getRiskControlTag());
        review.setProtocolBody(candidate.getProtocolBody());
        review.setStatus(candidate.getStatus());
        review.setStatusType(candidate.getStatusType());
        review.setPriority(candidate.getPriority());
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private List<PaymentProtocolReviewDTO> submitExistingProtocolReview(
            PaymentProtocolConfigEntity candidate,
            String requester,
            String reason,
            String operationType,
            Integer sourceVersionNo) {
        String protocolCode = candidate.getProtocolCode();
        if (paymentConfigMapper.countPendingProtocolReviews(protocolCode) > 0) {
            throw new IllegalArgumentException("该支付协议已有待复核申请");
        }
        Integer baseVersionNo = paymentConfigMapper.findLatestProtocolVersionNo(protocolCode);
        PaymentProtocolReviewEntity review = buildProtocolReview(
                candidate, requester, reason, operationType, baseVersionNo, sourceVersionNo);
        insertProtocolReview(review);
        return paymentConfigMapper.findProtocolReviews();
    }

    private void insertProtocolReview(PaymentProtocolReviewEntity review) {
        try {
            paymentConfigMapper.insertProtocolReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付协议已有待复核申请", exception);
        }
    }

    private PaymentProtocolReviewEntity requirePendingProtocolReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentProtocolReviewEntity review = paymentConfigMapper.findProtocolReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("支付协议复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("支付协议复核申请已完成");
        }
        return review;
    }

    private String requireDistinctProtocolReviewer(PaymentProtocolReviewEntity review,
                                                   PaymentProtocolReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeProtocolReview(String requestNo,
                                        String reviewStatus,
                                        String reviewStatusType,
                                        String reviewer,
                                        String reviewComment) {
        int affectedRows = paymentConfigMapper.completeProtocolReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付协议复核状态已变化，请刷新后重试");
        }
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

    private PaymentProtocolConfigEntity copyProtocolEntity(PaymentProtocolConfigEntity current) {
        PaymentProtocolConfigEntity protocol = new PaymentProtocolConfigEntity();
        protocol.setProtocolCode(current.getProtocolCode());
        protocol.setProtocolName(current.getProtocolName());
        protocol.setProtocolType(current.getProtocolType());
        protocol.setProtocolTypeName(current.getProtocolTypeName());
        protocol.setTemplateCode(current.getTemplateCode());
        protocol.setTemplateName(current.getTemplateName());
        protocol.setTemplateVersion(current.getTemplateVersion());
        protocol.setSignMode(current.getSignMode());
        protocol.setSignElementSpec(current.getSignElementSpec());
        protocol.setESignatureProvider(current.getESignatureProvider());
        protocol.setSceneScope(current.getSceneScope());
        protocol.setChannelScope(current.getChannelScope());
        protocol.setMerchantAckRequired(current.getMerchantAckRequired());
        protocol.setRiskControlTag(current.getRiskControlTag());
        protocol.setProtocolBody(current.getProtocolBody());
        protocol.setStatus(current.getStatus());
        protocol.setStatusType(current.getStatusType());
        protocol.setPriority(current.getPriority());
        return protocol;
    }

    private PaymentProtocolConfigEntity copyProtocolEntity(PaymentProtocolReviewEntity review) {
        PaymentProtocolConfigEntity protocol = new PaymentProtocolConfigEntity();
        protocol.setProtocolCode(review.getProtocolCode());
        protocol.setProtocolName(review.getProtocolName());
        protocol.setProtocolType(review.getProtocolType());
        protocol.setProtocolTypeName(review.getProtocolTypeName());
        protocol.setTemplateCode(review.getTemplateCode());
        protocol.setTemplateName(review.getTemplateName());
        protocol.setTemplateVersion(review.getTemplateVersion());
        protocol.setSignMode(review.getSignMode());
        protocol.setSignElementSpec(review.getSignElementSpec());
        protocol.setESignatureProvider(review.getESignatureProvider());
        protocol.setSceneScope(review.getSceneScope());
        protocol.setChannelScope(review.getChannelScope());
        protocol.setMerchantAckRequired(review.getMerchantAckRequired());
        protocol.setRiskControlTag(review.getRiskControlTag());
        protocol.setProtocolBody(review.getProtocolBody());
        protocol.setStatus(review.getStatus());
        protocol.setStatusType(review.getStatusType());
        protocol.setPriority(review.getPriority());
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
        if (containsIsoControl(identity)) {
            throw new IllegalArgumentException("人员声明不能包含控制字符");
        }
        String normalized = requireText(identity, message);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("人员声明不能超过64个字符");
        }
        return normalized;
    }

    private boolean containsIsoControl(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
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

    private List<PaymentAlertProviderReviewDTO> submitAlertProviderReview(
            PaymentAlertProviderUpsertRequestDTO request, String overrideProviderCode) {
        if (request == null) {
            throw new IllegalArgumentException("告警通知供应商请求不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        String providerCode = (StringUtils.hasText(overrideProviderCode)
                ? requireImportText(overrideProviderCode, "供应商配置编码不能为空", 64)
                : requireImportText(request.getProviderCode(), "供应商配置编码不能为空", 64))
                .toUpperCase(Locale.ROOT);
        if (paymentConfigMapper.findPendingAlertProviderReviewForUpdate(providerCode) != null) {
            throw new IllegalArgumentException("该告警通知供应商已有待复核申请");
        }
        PaymentAlertProviderConfigEntity current =
                paymentConfigMapper.findAlertProviderByCodeForUpdate(providerCode);
        boolean createOperation = !StringUtils.hasText(overrideProviderCode);
        if (createOperation && current != null) {
            throw new IllegalArgumentException("告警通知供应商编码已存在");
        }
        if (!createOperation && current == null) {
            throw new IllegalArgumentException("告警通知供应商配置不存在");
        }
        PaymentAlertProviderConfigEntity candidate = buildAlertProviderEntity(request, providerCode);
        PaymentAlertProviderReviewEntity review = new PaymentAlertProviderReviewEntity();
        copyAlertProviderToReview(candidate, review);
        review.setRequestNo("APR" + UUID.randomUUID().toString().replace("-", ""));
        review.setOperationType(createOperation ? "CREATE" : "UPDATE");
        review.setPendingProviderCode(providerCode);
        review.setBaseFingerprint(current == null ? null : fingerprintAlertProvider(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertAlertProviderReview(review);
        return paymentConfigMapper.findAlertProviderReviews();
    }

    private PaymentAlertProviderReviewEntity requirePendingAlertProviderReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentAlertProviderReviewEntity review =
                paymentConfigMapper.findAlertProviderReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("告警通知供应商复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("告警通知供应商复核申请已处理");
        }
        return review;
    }

    private String requireDistinctAlertProviderReviewer(
            PaymentAlertProviderReviewEntity review,
            PaymentAlertProviderReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeAlertProviderReview(String requestNo,
                                             String reviewStatus,
                                             String reviewStatusType,
                                             String reviewer,
                                             String reviewComment) {
        int affectedRows = paymentConfigMapper.completeAlertProviderReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("告警通知供应商复核状态已变化，请刷新后重试");
        }
    }

    private void insertAlertProviderReview(PaymentAlertProviderReviewEntity review) {
        try {
            paymentConfigMapper.insertAlertProviderReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该告警通知供应商已有待复核申请", exception);
        }
    }

    private void copyAlertProviderToReview(
            PaymentAlertProviderConfigEntity source, PaymentAlertProviderReviewEntity target) {
        target.setProviderCode(source.getProviderCode());
        target.setProviderName(source.getProviderName());
        target.setChannelCode(source.getChannelCode());
        target.setEndpointAlias(source.getEndpointAlias());
        target.setTemplateCode(source.getTemplateCode());
        target.setTemplateBody(source.getTemplateBody());
        target.setRouteRule(source.getRouteRule());
        target.setRoutePriority(source.getRoutePriority());
        target.setRetryPolicy(source.getRetryPolicy());
        target.setRateLimitPolicy(source.getRateLimitPolicy());
        target.setCandidateStatus(source.getStatus());
        target.setCandidateStatusType(source.getStatusType());
    }

    private PaymentAlertProviderUpsertRequestDTO toAlertProviderUpsertRequest(
            PaymentAlertProviderReviewEntity review) {
        PaymentAlertProviderUpsertRequestDTO request = new PaymentAlertProviderUpsertRequestDTO();
        request.setProviderCode(review.getProviderCode());
        request.setProviderName(review.getProviderName());
        request.setChannelCode(review.getChannelCode());
        request.setEndpointAlias(review.getEndpointAlias());
        request.setTemplateCode(review.getTemplateCode());
        request.setTemplateBody(review.getTemplateBody());
        request.setRouteRule(review.getRouteRule());
        request.setRoutePriority(review.getRoutePriority());
        request.setRetryPolicy(review.getRetryPolicy());
        request.setRateLimitPolicy(review.getRateLimitPolicy());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getCandidateStatus())));
        return request;
    }

    private PaymentAlertProviderConfigEntity buildAlertProviderEntity(
            PaymentAlertProviderUpsertRequestDTO request, String overrideProviderCode) {
        if (request == null) {
            throw new IllegalArgumentException("告警通知供应商请求不能为空");
        }
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("告警通知供应商启停状态不能为空");
        }
        PaymentAlertProviderConfigEntity entity = new PaymentAlertProviderConfigEntity();
        entity.setProviderCode((StringUtils.hasText(overrideProviderCode)
                ? requireImportText(overrideProviderCode, "供应商配置编码不能为空", 64)
                : requireImportText(request.getProviderCode(), "供应商配置编码不能为空", 64))
                .toUpperCase(Locale.ROOT));
        entity.setProviderName(requireImportText(request.getProviderName(), "供应商名称不能为空", 64));
        entity.setChannelCode(resolveAlertProviderChannel(request.getChannelCode()));
        entity.setEndpointAlias(requireImportText(request.getEndpointAlias(), "接入端点标识不能为空", 64));
        entity.setTemplateCode(requireImportText(request.getTemplateCode(), "模板编码不能为空", 64));
        entity.setTemplateBody(requireImportText(request.getTemplateBody(), "模板正文不能为空", 1024));
        entity.setRouteRule(requireImportText(request.getRouteRule(), "路由规则不能为空", 255));
        entity.setRoutePriority(resolveAlertProviderPriority(request.getRoutePriority()));
        entity.setRetryPolicy(requireImportText(request.getRetryPolicy(), "重试策略不能为空", 128));
        entity.setRateLimitPolicy(requireImportText(request.getRateLimitPolicy(), "限流策略不能为空", 128));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private String resolveAlertProviderChannel(String channelCode) {
        String normalized = requireImportText(channelCode, "通知通道不能为空", 32).toUpperCase(Locale.ROOT);
        if (!"IN_APP".equals(normalized)
                && !"IM".equals(normalized)
                && !"SMS".equals(normalized)
                && !"EMAIL".equals(normalized)) {
            throw new IllegalArgumentException("通知通道仅支持 IN_APP/IM/SMS/EMAIL");
        }
        return normalized;
    }

    private Integer resolveAlertProviderPriority(Integer priority) {
        if (priority == null) {
            return Integer.valueOf(100);
        }
        if (priority.intValue() < 0) {
            throw new IllegalArgumentException("供应商路由优先级不能小于0");
        }
        return priority;
    }

    private List<PaymentIssueDutyRosterReviewDTO> submitIssueDutyRosterReview(
            PaymentIssueDutyRosterUpsertRequestDTO request, String overrideRosterCode) {
        if (request == null) {
            throw new IllegalArgumentException("异常告警值班路由请求不能为空");
        }
        String requester = requireReviewIdentity(request.getRequester(), "申请人声明不能为空");
        String reason = requireReviewText(request.getChangeReason(), "申请原因不能为空");
        String rosterCode = (StringUtils.hasText(overrideRosterCode)
                ? requireImportText(overrideRosterCode, "值班路由编码不能为空", 64)
                : requireImportText(request.getRosterCode(), "值班路由编码不能为空", 64))
                .toUpperCase(Locale.ROOT);
        if (paymentConfigMapper.findPendingIssueDutyRosterReviewForUpdate(rosterCode) != null) {
            throw new IllegalArgumentException("该异常告警值班路由已有待复核申请");
        }
        PaymentIssueDutyRosterEntity current =
                paymentConfigMapper.findIssueDutyRosterByCodeForUpdate(rosterCode);
        boolean createOperation = !StringUtils.hasText(overrideRosterCode);
        if (createOperation && current != null) {
            throw new IllegalArgumentException("异常告警值班路由编码已存在");
        }
        if (!createOperation && current == null) {
            throw new IllegalArgumentException("异常告警值班路由配置不存在");
        }
        PaymentIssueDutyRosterEntity candidate = buildIssueDutyRosterEntity(request, rosterCode);
        PaymentIssueDutyRosterReviewEntity review = new PaymentIssueDutyRosterReviewEntity();
        copyIssueDutyRosterToReview(candidate, review);
        review.setRequestNo("DRR" + UUID.randomUUID().toString().replace("-", ""));
        review.setOperationType(createOperation ? "CREATE" : "UPDATE");
        review.setPendingRosterCode(rosterCode);
        review.setBaseFingerprint(current == null ? null : fingerprintIssueDutyRoster(current));
        review.setRequester(requester);
        review.setRequestReason(reason);
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        insertIssueDutyRosterReview(review);
        return paymentConfigMapper.findIssueDutyRosterReviews();
    }

    private PaymentIssueDutyRosterReviewEntity requirePendingIssueDutyRosterReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentIssueDutyRosterReviewEntity review =
                paymentConfigMapper.findIssueDutyRosterReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("异常告警值班路由复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("异常告警值班路由复核申请已处理");
        }
        return review;
    }

    private String requireDistinctIssueDutyRosterReviewer(
            PaymentIssueDutyRosterReviewEntity review,
            PaymentIssueDutyRosterReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeIssueDutyRosterReview(String requestNo,
                                               String reviewStatus,
                                               String reviewStatusType,
                                               String reviewer,
                                               String reviewComment) {
        int affectedRows = paymentConfigMapper.completeIssueDutyRosterReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("异常告警值班路由复核状态已变化，请刷新后重试");
        }
    }

    private void insertIssueDutyRosterReview(PaymentIssueDutyRosterReviewEntity review) {
        try {
            paymentConfigMapper.insertIssueDutyRosterReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该异常告警值班路由已有待复核申请", exception);
        }
    }

    private void copyIssueDutyRosterToReview(
            PaymentIssueDutyRosterEntity source, PaymentIssueDutyRosterReviewEntity target) {
        target.setRosterCode(source.getRosterCode());
        target.setIssueType(source.getIssueType());
        target.setSeverity(source.getSeverity());
        target.setResponsibilityGroup(source.getResponsibilityGroup());
        target.setReceiver(source.getReceiver());
        target.setNotifyChannels(source.getNotifyChannels());
        target.setEscalationLevel(source.getEscalationLevel());
        target.setEscalationReceiver(source.getEscalationReceiver());
        target.setEscalationPolicy(source.getEscalationPolicy());
        target.setEscalationTimeoutMinutes(source.getEscalationTimeoutMinutes());
        target.setScheduleTag(source.getScheduleTag());
        target.setEffectiveStartHour(source.getEffectiveStartHour());
        target.setEffectiveEndHour(source.getEffectiveEndHour());
        target.setWeekdayScope(source.getWeekdayScope());
        target.setHolidayStrategy(source.getHolidayStrategy());
        target.setCandidateStatus(source.getStatus());
        target.setCandidateStatusType(source.getStatusType());
    }

    private PaymentIssueDutyRosterUpsertRequestDTO toIssueDutyRosterUpsertRequest(
            PaymentIssueDutyRosterReviewEntity review) {
        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode(review.getRosterCode());
        request.setIssueType(review.getIssueType());
        request.setSeverity(review.getSeverity());
        request.setResponsibilityGroup(review.getResponsibilityGroup());
        request.setReceiver(review.getReceiver());
        request.setNotifyChannels(review.getNotifyChannels());
        request.setEscalationLevel(review.getEscalationLevel());
        request.setEscalationReceiver(review.getEscalationReceiver());
        request.setEscalationPolicy(review.getEscalationPolicy());
        request.setEscalationTimeoutMinutes(review.getEscalationTimeoutMinutes());
        request.setScheduleTag(review.getScheduleTag());
        request.setEffectiveStartHour(review.getEffectiveStartHour());
        request.setEffectiveEndHour(review.getEffectiveEndHour());
        request.setWeekdayScope(review.getWeekdayScope());
        request.setHolidayStrategy(review.getHolidayStrategy());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getCandidateStatus())));
        return request;
    }

    private PaymentIssueDutyRosterEntity buildIssueDutyRosterEntity(PaymentIssueDutyRosterUpsertRequestDTO request,
                                                                    String overrideRosterCode) {
        if (request == null) {
            throw new IllegalArgumentException("异常告警值班路由请求不能为空");
        }
        PaymentIssueDutyRosterEntity entity = new PaymentIssueDutyRosterEntity();
        entity.setRosterCode((StringUtils.hasText(overrideRosterCode)
                ? overrideRosterCode.trim()
                : requireText(request.getRosterCode(), "值班路由编码不能为空"))
                .toUpperCase(Locale.ROOT));
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

    private PaymentChannelReviewEntity requirePendingChannelReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentChannelReviewEntity review = paymentConfigMapper.findChannelReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("支付渠道复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("支付渠道复核申请已完成");
        }
        return review;
    }

    private PaymentGatewayReviewEntity requirePendingGatewayReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentGatewayReviewEntity review = paymentConfigMapper.findGatewayReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("支付网关复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("支付网关复核申请已完成");
        }
        return review;
    }

    private String requireDistinctGatewayReviewer(
            PaymentGatewayReviewEntity review,
            PaymentGatewayReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeGatewayReview(String requestNo,
                                       String reviewStatus,
                                       String reviewStatusType,
                                       String reviewer,
                                       String reviewComment) {
        int affectedRows = paymentConfigMapper.completeGatewayReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付网关复核状态已变化，请刷新后重试");
        }
    }

    private void insertGatewayReview(PaymentGatewayReviewEntity review) {
        try {
            paymentConfigMapper.insertGatewayReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付网关已有待复核申请", exception);
        }
    }

    private PaymentGatewayConfigEntity buildGatewayEntity(
            PaymentGatewayUpsertRequestDTO request, String gatewayCode) {
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("支付网关启停状态不能为空");
        }
        PaymentGatewayConfigEntity entity = new PaymentGatewayConfigEntity();
        entity.setGatewayCode(gatewayCode);
        entity.setGatewayName(requireImportText(request.getGatewayName(), "支付网关名称不能为空", 128));
        entity.setAccessMode(requireImportText(request.getAccessMode(), "接入模式不能为空", 64));
        entity.setChannelScope(requireImportText(request.getChannelScope(), "适用渠道范围不能为空", 256));
        entity.setEnvironmentScope(requireImportText(
                request.getEnvironmentScope(), "环境范围不能为空", 128));
        entity.setApiBaseUrl(requireGatewayHttpsUrl(request.getApiBaseUrl()));
        entity.setProtocolType(requireImportText(request.getProtocolType(), "报文协议不能为空", 64));
        entity.setSignAlgorithm(requireImportText(request.getSignAlgorithm(), "签名算法不能为空", 64));
        entity.setCertificateAlias(requireImportText(
                request.getCertificateAlias(), "证书别名不能为空", 128));
        String certificateStatus = requireImportText(
                request.getCertificateStatus(), "证书状态不能为空", 32).toUpperCase(Locale.ROOT);
        entity.setCertificateStatus(certificateStatus);
        entity.setCertificateStatusType(resolveCertificateStatusType(certificateStatus));
        entity.setReleaseStage(requireImportText(request.getReleaseStage(), "发布阶段不能为空", 64));
        entity.setGrayStrategy(requireImportText(request.getGrayStrategy(), "灰度策略不能为空", 256));
        entity.setCallbackWhitelist(requireImportText(
                request.getCallbackWhitelist(), "回调白名单不能为空", 256));
        entity.setAdapterRegistry(requireImportText(
                request.getAdapterRegistry(), "适配器编排不能为空", 256));
        entity.setTimeoutMs(requirePositiveInteger(request.getTimeoutMs(), "网关超时时间必须大于0"));
        entity.setRetryPolicy(requireImportText(request.getRetryPolicy(), "重试策略不能为空", 128));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        entity.setPriority(requireNonNegativeInteger(request.getPriority(), "网关优先级不能小于0"));
        return entity;
    }

    private String resolveCertificateStatusType(String certificateStatus) {
        if ("VALID".equals(certificateStatus)) {
            return "success";
        }
        if ("EXPIRING_SOON".equals(certificateStatus)) {
            return "warn";
        }
        if ("EXPIRED".equals(certificateStatus) || "ARCHIVED".equals(certificateStatus)) {
            return "danger";
        }
        throw new IllegalArgumentException("证书状态仅支持 VALID/EXPIRING_SOON/EXPIRED/ARCHIVED");
    }

    private void copyGatewayToReview(PaymentGatewayConfigEntity source, PaymentGatewayReviewEntity target) {
        target.setGatewayCode(source.getGatewayCode());
        target.setGatewayName(source.getGatewayName());
        target.setAccessMode(source.getAccessMode());
        target.setChannelScope(source.getChannelScope());
        target.setEnvironmentScope(source.getEnvironmentScope());
        target.setApiBaseUrl(source.getApiBaseUrl());
        target.setProtocolType(source.getProtocolType());
        target.setSignAlgorithm(source.getSignAlgorithm());
        target.setCertificateAlias(source.getCertificateAlias());
        target.setCertificateStatus(source.getCertificateStatus());
        target.setCertificateStatusType(source.getCertificateStatusType());
        target.setReleaseStage(source.getReleaseStage());
        target.setGrayStrategy(source.getGrayStrategy());
        target.setCallbackWhitelist(source.getCallbackWhitelist());
        target.setAdapterRegistry(source.getAdapterRegistry());
        target.setTimeoutMs(source.getTimeoutMs());
        target.setRetryPolicy(source.getRetryPolicy());
        target.setCandidateStatus(source.getStatus());
        target.setCandidateStatusType(source.getStatusType());
        target.setPriority(source.getPriority());
    }

    private void ensureGatewayBaseline(PaymentGatewayConfigEntity current) {
        Integer latestVersionNo = paymentConfigMapper.findLatestGatewayVersionNo(current.getGatewayCode());
        if (latestVersionNo == null || latestVersionNo.intValue() == 0) {
            recordGatewayVersion(current, "BASELINE", null, "存量支付网关变更前基线");
        }
    }

    private void recordGatewayVersion(PaymentGatewayConfigEntity gateway,
                                      String changeType,
                                      Integer sourceVersionNo,
                                      String changeReason) {
        Integer latestVersionNo = paymentConfigMapper.findLatestGatewayVersionNo(gateway.getGatewayCode());
        int nextVersionNo = latestVersionNo == null ? 1 : latestVersionNo.intValue() + 1;
        PaymentGatewayVersionEntity version = new PaymentGatewayVersionEntity();
        version.setGatewayCode(gateway.getGatewayCode());
        version.setVersionNo(Integer.valueOf(nextVersionNo));
        version.setGatewayName(gateway.getGatewayName());
        version.setAccessMode(gateway.getAccessMode());
        version.setChannelScope(gateway.getChannelScope());
        version.setEnvironmentScope(gateway.getEnvironmentScope());
        version.setApiBaseUrl(gateway.getApiBaseUrl());
        version.setProtocolType(gateway.getProtocolType());
        version.setSignAlgorithm(gateway.getSignAlgorithm());
        version.setCertificateAlias(gateway.getCertificateAlias());
        version.setCertificateStatus(gateway.getCertificateStatus());
        version.setCertificateStatusType(gateway.getCertificateStatusType());
        version.setReleaseStage(gateway.getReleaseStage());
        version.setGrayStrategy(gateway.getGrayStrategy());
        version.setCallbackWhitelist(gateway.getCallbackWhitelist());
        version.setAdapterRegistry(gateway.getAdapterRegistry());
        version.setTimeoutMs(gateway.getTimeoutMs());
        version.setRetryPolicy(gateway.getRetryPolicy());
        version.setStatus(gateway.getStatus());
        version.setStatusType(gateway.getStatusType());
        version.setPriority(gateway.getPriority());
        version.setChangeType(changeType);
        version.setSourceVersionNo(sourceVersionNo);
        version.setChangeReason(changeReason);
        paymentConfigMapper.insertGatewayVersion(version);
    }

    private PaymentGatewayConfigEntity copyGatewayEntity(PaymentGatewayVersionEntity version) {
        PaymentGatewayConfigEntity gateway = new PaymentGatewayConfigEntity();
        gateway.setGatewayCode(version.getGatewayCode());
        gateway.setGatewayName(version.getGatewayName());
        gateway.setAccessMode(version.getAccessMode());
        gateway.setChannelScope(version.getChannelScope());
        gateway.setEnvironmentScope(version.getEnvironmentScope());
        gateway.setApiBaseUrl(version.getApiBaseUrl());
        gateway.setProtocolType(version.getProtocolType());
        gateway.setSignAlgorithm(version.getSignAlgorithm());
        gateway.setCertificateAlias(version.getCertificateAlias());
        gateway.setCertificateStatus(version.getCertificateStatus());
        gateway.setCertificateStatusType(version.getCertificateStatusType());
        gateway.setReleaseStage(version.getReleaseStage());
        gateway.setGrayStrategy(version.getGrayStrategy());
        gateway.setCallbackWhitelist(version.getCallbackWhitelist());
        gateway.setAdapterRegistry(version.getAdapterRegistry());
        gateway.setTimeoutMs(version.getTimeoutMs());
        gateway.setRetryPolicy(version.getRetryPolicy());
        gateway.setStatus(version.getStatus());
        gateway.setStatusType(version.getStatusType());
        gateway.setPriority(version.getPriority());
        return gateway;
    }

    private PaymentGatewayUpsertRequestDTO toGatewayUpsertRequest(PaymentGatewayReviewEntity review) {
        PaymentGatewayUpsertRequestDTO request = new PaymentGatewayUpsertRequestDTO();
        request.setGatewayCode(review.getGatewayCode());
        request.setGatewayName(review.getGatewayName());
        request.setAccessMode(review.getAccessMode());
        request.setChannelScope(review.getChannelScope());
        request.setEnvironmentScope(review.getEnvironmentScope());
        request.setApiBaseUrl(review.getApiBaseUrl());
        request.setProtocolType(review.getProtocolType());
        request.setSignAlgorithm(review.getSignAlgorithm());
        request.setCertificateAlias(review.getCertificateAlias());
        request.setCertificateStatus(review.getCertificateStatus());
        request.setReleaseStage(review.getReleaseStage());
        request.setGrayStrategy(review.getGrayStrategy());
        request.setCallbackWhitelist(review.getCallbackWhitelist());
        request.setAdapterRegistry(review.getAdapterRegistry());
        request.setTimeoutMs(review.getTimeoutMs());
        request.setRetryPolicy(review.getRetryPolicy());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getCandidateStatus())));
        request.setPriority(review.getPriority());
        return request;
    }

    private PaymentControlPolicyReviewEntity requirePendingControlPolicyReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentControlPolicyReviewEntity review =
                paymentConfigMapper.findControlPolicyReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("支付控制策略复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("支付控制策略复核申请已处理");
        }
        return review;
    }

    private String requireDistinctControlPolicyReviewer(
            PaymentControlPolicyReviewEntity review,
            PaymentControlPolicyReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeControlPolicyReview(String requestNo,
                                             String reviewStatus,
                                             String reviewStatusType,
                                             String reviewer,
                                             String reviewComment) {
        int affectedRows = paymentConfigMapper.completeControlPolicyReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付控制策略复核状态已变化，请刷新后重试");
        }
    }

    private void ensureControlPolicyBaseline(PaymentControlPolicyEntity current) {
        Integer latestVersionNo = paymentConfigMapper.findLatestControlPolicyVersionNo(current.getSourceAppId());
        if (latestVersionNo == null || latestVersionNo.intValue() == 0) {
            recordControlPolicyVersion(current, "BASELINE", null, "存量支付控制策略变更前基线");
        }
    }

    private void recordControlPolicyVersion(PaymentControlPolicyEntity policy,
                                            String changeType,
                                            Integer sourceVersionNo,
                                            String changeReason) {
        Integer latestVersionNo = paymentConfigMapper.findLatestControlPolicyVersionNo(policy.getSourceAppId());
        int nextVersionNo = latestVersionNo == null ? 1 : latestVersionNo.intValue() + 1;
        PaymentControlPolicyVersionEntity version = new PaymentControlPolicyVersionEntity();
        version.setSourceAppId(policy.getSourceAppId());
        version.setVersionNo(Integer.valueOf(nextVersionNo));
        version.setSourceAppName(policy.getSourceAppName());
        version.setAllowedPaymentMethods(policy.getAllowedPaymentMethods());
        version.setAllowedChannelCodes(policy.getAllowedChannelCodes());
        version.setAllowedMerchantNos(policy.getAllowedMerchantNos());
        version.setMinuteSubmitLimit(policy.getMinuteSubmitLimit());
        version.setInterfaceMinuteSubmitLimit(policy.getInterfaceMinuteSubmitLimit());
        version.setTokenAuthRequired(policy.getTokenAuthRequired());
        version.setAccessTokenValue(policy.getAccessTokenValue());
        version.setStrictMode(policy.getStrictMode());
        version.setStatus(policy.getStatus());
        version.setStatusType(policy.getStatusType());
        version.setChangeType(changeType);
        version.setSourceVersionNo(sourceVersionNo);
        version.setChangeReason(changeReason);
        paymentConfigMapper.insertControlPolicyVersion(version);
    }

    private PaymentControlPolicyEntity copyControlPolicyEntity(PaymentControlPolicyVersionEntity version) {
        PaymentControlPolicyEntity policy = new PaymentControlPolicyEntity();
        policy.setSourceAppId(version.getSourceAppId());
        policy.setSourceAppName(version.getSourceAppName());
        policy.setAllowedPaymentMethods(version.getAllowedPaymentMethods());
        policy.setAllowedChannelCodes(version.getAllowedChannelCodes());
        policy.setAllowedMerchantNos(version.getAllowedMerchantNos());
        policy.setMinuteSubmitLimit(version.getMinuteSubmitLimit());
        policy.setInterfaceMinuteSubmitLimit(version.getInterfaceMinuteSubmitLimit());
        policy.setTokenAuthRequired(version.getTokenAuthRequired());
        policy.setAccessTokenValue(version.getAccessTokenValue());
        policy.setStrictMode(version.getStrictMode());
        policy.setSelfCheckStatus("WARN");
        policy.setSelfCheckStatusType("warn");
        policy.setSelfCheckMessage("配置已回滚，待重新执行支付控制策略自检");
        policy.setStatus(version.getStatus());
        policy.setStatusType(version.getStatusType());
        return policy;
    }

    private void insertControlPolicyReview(PaymentControlPolicyReviewEntity review) {
        try {
            paymentConfigMapper.insertControlPolicyReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付控制策略已有待复核申请", exception);
        }
    }

    private PaymentControlPolicyEntity buildControlPolicyEntity(
            PaymentControlPolicyUpsertRequestDTO request,
            PaymentControlPolicyEntity current,
            String sourceAppId) {
        if (request.getTokenAuthRequired() == null) {
            throw new IllegalArgumentException("令牌鉴权开关不能为空");
        }
        if (request.getStrictMode() == null) {
            throw new IllegalArgumentException("严格模式开关不能为空");
        }
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("支付控制策略启停状态不能为空");
        }
        String accessToken = StringUtils.hasText(request.getAccessTokenValue())
                ? request.getAccessTokenValue().trim()
                : current == null ? "" : current.getAccessTokenValue();
        if (accessToken.length() > 128) {
            throw new IllegalArgumentException("接口访问令牌不能超过128个字符");
        }
        if (request.getTokenAuthRequired().booleanValue() && !StringUtils.hasText(accessToken)) {
            throw new IllegalArgumentException("开启令牌鉴权时接口访问令牌不能为空");
        }
        PaymentControlPolicyEntity entity = new PaymentControlPolicyEntity();
        entity.setSourceAppId(sourceAppId);
        entity.setSourceAppName(requireImportText(request.getSourceAppName(), "来源应用名称不能为空", 128));
        entity.setAllowedPaymentMethods(requireImportText(
                request.getAllowedPaymentMethods(), "允许支付方式不能为空", 255));
        entity.setAllowedChannelCodes(requireImportText(
                request.getAllowedChannelCodes(), "允许渠道不能为空", 255));
        entity.setAllowedMerchantNos(requireImportText(
                request.getAllowedMerchantNos(), "允许商户号不能为空", 255));
        entity.setMinuteSubmitLimit(requirePositiveInteger(
                request.getMinuteSubmitLimit(), "分钟级提交限流阈值必须大于0"));
        entity.setInterfaceMinuteSubmitLimit(requirePositiveInteger(
                request.getInterfaceMinuteSubmitLimit(), "接口级分钟限流阈值必须大于0"));
        entity.setTokenAuthRequired(request.getTokenAuthRequired().booleanValue() ? "开启" : "关闭");
        entity.setAccessTokenValue(accessToken);
        entity.setStrictMode(request.getStrictMode().booleanValue() ? "开启" : "关闭");
        entity.setSelfCheckStatus("WARN");
        entity.setSelfCheckStatusType("warn");
        entity.setSelfCheckMessage("配置已变更，待重新执行支付控制策略自检");
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private void copyControlPolicyToReview(
            PaymentControlPolicyEntity source, PaymentControlPolicyReviewEntity target) {
        target.setSourceAppId(source.getSourceAppId());
        target.setSourceAppName(source.getSourceAppName());
        target.setAllowedPaymentMethods(source.getAllowedPaymentMethods());
        target.setAllowedChannelCodes(source.getAllowedChannelCodes());
        target.setAllowedMerchantNos(source.getAllowedMerchantNos());
        target.setMinuteSubmitLimit(source.getMinuteSubmitLimit());
        target.setInterfaceMinuteSubmitLimit(source.getInterfaceMinuteSubmitLimit());
        target.setTokenAuthRequired(source.getTokenAuthRequired());
        target.setAccessTokenValue(source.getAccessTokenValue());
        target.setStrictMode(source.getStrictMode());
        target.setCandidateStatus(source.getStatus());
        target.setCandidateStatusType(source.getStatusType());
    }

    private PaymentControlPolicyUpsertRequestDTO toControlPolicyUpsertRequest(
            PaymentControlPolicyReviewEntity review) {
        PaymentControlPolicyUpsertRequestDTO request = new PaymentControlPolicyUpsertRequestDTO();
        request.setSourceAppId(review.getSourceAppId());
        request.setSourceAppName(review.getSourceAppName());
        request.setAllowedPaymentMethods(review.getAllowedPaymentMethods());
        request.setAllowedChannelCodes(review.getAllowedChannelCodes());
        request.setAllowedMerchantNos(review.getAllowedMerchantNos());
        request.setMinuteSubmitLimit(review.getMinuteSubmitLimit());
        request.setInterfaceMinuteSubmitLimit(review.getInterfaceMinuteSubmitLimit());
        request.setTokenAuthRequired(Boolean.valueOf("开启".equals(review.getTokenAuthRequired())));
        request.setAccessTokenValue(review.getAccessTokenValue());
        request.setStrictMode(Boolean.valueOf("开启".equals(review.getStrictMode())));
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getCandidateStatus())));
        return request;
    }

    private void ensureChannelBaseline(PaymentChannelConfigEntity current) {
        Integer latestVersionNo = paymentConfigMapper.findLatestChannelVersionNo(current.getChannelCode());
        if (latestVersionNo == null || latestVersionNo.intValue() == 0) {
            recordChannelVersion(current, "BASELINE", null, "存量支付渠道变更前基线");
        }
    }

    private void recordChannelVersion(PaymentChannelConfigEntity channel,
                                      String changeType,
                                      Integer sourceVersionNo,
                                      String changeReason) {
        Integer latestVersionNo = paymentConfigMapper.findLatestChannelVersionNo(channel.getChannelCode());
        int nextVersionNo = latestVersionNo == null ? 1 : latestVersionNo.intValue() + 1;
        PaymentChannelVersionEntity version = new PaymentChannelVersionEntity();
        version.setChannelCode(channel.getChannelCode());
        version.setVersionNo(Integer.valueOf(nextVersionNo));
        version.setChannelName(channel.getChannelName());
        version.setPaymentMethod(channel.getPaymentMethod());
        version.setMerchantNo(channel.getMerchantNo());
        version.setMerchantAppId(channel.getMerchantAppId());
        version.setCallbackSecret(channel.getCallbackSecret());
        version.setCallbackSignAlgorithm(channel.getCallbackSignAlgorithm());
        version.setCallbackPublicKey(channel.getCallbackPublicKey());
        version.setCallbackNotifyUrl(channel.getCallbackNotifyUrl());
        version.setCertificateProfile(channel.getCertificateProfile());
        version.setNotifySignWindowSec(channel.getNotifySignWindowSec());
        version.setRefundWindowDays(channel.getRefundWindowDays());
        version.setRiskControlTag(channel.getRiskControlTag());
        version.setSceneScope(channel.getSceneScope());
        version.setStatus(channel.getStatus());
        version.setStatusType(channel.getStatusType());
        version.setDailyLimit(channel.getDailyLimit());
        version.setPriority(channel.getPriority());
        version.setRouteWeight(channel.getRouteWeight());
        version.setChangeType(changeType);
        version.setSourceVersionNo(sourceVersionNo);
        version.setChangeReason(changeReason);
        paymentConfigMapper.insertChannelVersion(version);
    }

    private PaymentChannelConfigEntity copyChannelEntity(PaymentChannelVersionEntity version) {
        PaymentChannelConfigEntity channel = new PaymentChannelConfigEntity();
        channel.setChannelCode(version.getChannelCode());
        channel.setChannelName(version.getChannelName());
        channel.setPaymentMethod(version.getPaymentMethod());
        channel.setMerchantNo(version.getMerchantNo());
        channel.setMerchantAppId(version.getMerchantAppId());
        channel.setCallbackSecret(version.getCallbackSecret());
        channel.setCallbackSignAlgorithm(version.getCallbackSignAlgorithm());
        channel.setCallbackPublicKey(version.getCallbackPublicKey());
        channel.setCallbackNotifyUrl(version.getCallbackNotifyUrl());
        channel.setCertificateProfile(version.getCertificateProfile());
        channel.setNotifySignWindowSec(version.getNotifySignWindowSec());
        channel.setRefundWindowDays(version.getRefundWindowDays());
        channel.setRiskControlTag(version.getRiskControlTag());
        channel.setSceneScope(version.getSceneScope());
        channel.setStatus(version.getStatus());
        channel.setStatusType(version.getStatusType());
        channel.setDailyLimit(version.getDailyLimit());
        channel.setPriority(version.getPriority());
        channel.setRouteWeight(version.getRouteWeight());
        return channel;
    }

    private String requireDistinctChannelReviewer(
            PaymentChannelReviewEntity review,
            PaymentChannelReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeChannelReview(String requestNo,
                                       String reviewStatus,
                                       String reviewStatusType,
                                       String reviewer,
                                       String reviewComment) {
        int affectedRows = paymentConfigMapper.completeChannelReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付渠道复核状态已变化，请刷新后重试");
        }
    }

    private void insertChannelReview(PaymentChannelReviewEntity review) {
        try {
            paymentConfigMapper.insertChannelReview(review);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该支付渠道已有待复核申请", exception);
        }
    }

    private PaymentChannelConfigEntity buildChannelEntity(PaymentChannelUpsertRequestDTO request,
                                                            PaymentChannelConfigEntity current,
                                                            String channelCode) {
        if (request.getEnabled() == null) {
            throw new IllegalArgumentException("支付渠道启停状态不能为空");
        }
        PaymentChannelConfigEntity entity = new PaymentChannelConfigEntity();
        entity.setChannelCode(channelCode);
        entity.setChannelName(requireImportText(request.getChannelName(), "渠道名称不能为空", 128));
        entity.setPaymentMethod(requireImportText(request.getPaymentMethod(), "支付方式不能为空", 64));
        entity.setMerchantNo(requireImportText(request.getMerchantNo(), "渠道商户号不能为空", 128));
        entity.setMerchantAppId(requireImportText(request.getMerchantAppId(), "商户应用标识不能为空", 128));
        String callbackSecret = optionalChannelText(request.getCallbackSecret(), 256, "回调验签密钥");
        if (callbackSecret == null && current != null) {
            callbackSecret = current.getCallbackSecret();
        }
        if (!StringUtils.hasText(callbackSecret)) {
            throw new IllegalArgumentException("回调验签密钥不能为空");
        }
        entity.setCallbackSecret(callbackSecret);
        String algorithm = requireImportText(
                request.getCallbackSignAlgorithm(), "回调签名算法不能为空", 32).toUpperCase(Locale.ROOT);
        if (!"HMAC-SHA256".equals(algorithm) && !"RSA2".equals(algorithm)) {
            throw new IllegalArgumentException("回调签名算法仅支持 HMAC-SHA256/RSA2");
        }
        entity.setCallbackSignAlgorithm(algorithm);
        String publicKey = optionalChannelText(request.getCallbackPublicKey(), 8192, "回调验签公钥");
        if (publicKey == null && current != null) {
            publicKey = current.getCallbackPublicKey();
        }
        if ("RSA2".equals(algorithm) && !StringUtils.hasText(publicKey)) {
            throw new IllegalArgumentException("RSA2 回调验签公钥不能为空");
        }
        entity.setCallbackPublicKey(publicKey);
        entity.setCallbackNotifyUrl(requireHttpsUrl(request.getCallbackNotifyUrl()));
        entity.setCertificateProfile(requireImportText(
                request.getCertificateProfile(), "证书档案不能为空", 128));
        entity.setNotifySignWindowSec(requirePositiveInteger(
                request.getNotifySignWindowSec(), "回调验签时间窗必须大于0"));
        entity.setRefundWindowDays(requirePositiveInteger(
                request.getRefundWindowDays(), "原路退款时效必须大于0"));
        entity.setRiskControlTag(requireImportText(request.getRiskControlTag(), "风控标签不能为空", 128));
        entity.setSceneScope(requireImportText(request.getSceneScope(), "适用场景不能为空", 128));
        if (request.getDailyLimit() == null || request.getDailyLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("单日限额不能小于0");
        }
        entity.setDailyLimit(request.getDailyLimit());
        entity.setPriority(requireNonNegativeInteger(request.getPriority(), "渠道优先级不能小于0"));
        Integer routeWeight = requireNonNegativeInteger(request.getRouteWeight(), "渠道路由权重不能小于0");
        if (routeWeight.intValue() > 100) {
            throw new IllegalArgumentException("渠道路由权重不能大于100");
        }
        entity.setRouteWeight(routeWeight);
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private void copyChannelToReview(PaymentChannelConfigEntity source, PaymentChannelReviewEntity target) {
        target.setChannelCode(source.getChannelCode());
        target.setChannelName(source.getChannelName());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setMerchantNo(source.getMerchantNo());
        target.setMerchantAppId(source.getMerchantAppId());
        target.setCallbackSecret(source.getCallbackSecret());
        target.setCallbackSignAlgorithm(source.getCallbackSignAlgorithm());
        target.setCallbackPublicKey(source.getCallbackPublicKey());
        target.setCallbackNotifyUrl(source.getCallbackNotifyUrl());
        target.setCertificateProfile(source.getCertificateProfile());
        target.setNotifySignWindowSec(source.getNotifySignWindowSec());
        target.setRefundWindowDays(source.getRefundWindowDays());
        target.setRiskControlTag(source.getRiskControlTag());
        target.setSceneScope(source.getSceneScope());
        target.setCandidateStatus(source.getStatus());
        target.setCandidateStatusType(source.getStatusType());
        target.setDailyLimit(source.getDailyLimit());
        target.setPriority(source.getPriority());
        target.setRouteWeight(source.getRouteWeight());
    }

    private PaymentChannelUpsertRequestDTO toChannelUpsertRequest(PaymentChannelReviewEntity review) {
        PaymentChannelUpsertRequestDTO request = new PaymentChannelUpsertRequestDTO();
        request.setChannelCode(review.getChannelCode());
        request.setChannelName(review.getChannelName());
        request.setPaymentMethod(review.getPaymentMethod());
        request.setMerchantNo(review.getMerchantNo());
        request.setMerchantAppId(review.getMerchantAppId());
        request.setCallbackSecret(review.getCallbackSecret());
        request.setCallbackSignAlgorithm(review.getCallbackSignAlgorithm());
        request.setCallbackPublicKey(review.getCallbackPublicKey());
        request.setCallbackNotifyUrl(review.getCallbackNotifyUrl());
        request.setCertificateProfile(review.getCertificateProfile());
        request.setNotifySignWindowSec(review.getNotifySignWindowSec());
        request.setRefundWindowDays(review.getRefundWindowDays());
        request.setRiskControlTag(review.getRiskControlTag());
        request.setSceneScope(review.getSceneScope());
        request.setEnabled(Boolean.valueOf(STATUS_ENABLED.equals(review.getCandidateStatus())));
        request.setDailyLimit(review.getDailyLimit());
        request.setPriority(review.getPriority());
        request.setRouteWeight(review.getRouteWeight());
        return request;
    }

    private String optionalChannelText(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private Integer requirePositiveInteger(Integer value, String message) {
        if (value == null || value.intValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Integer requireNonNegativeInteger(Integer value, String message) {
        if (value == null || value.intValue() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String requireHttpsUrl(String value) {
        String normalized = requireImportText(value, "回调通知地址不能为空", 256);
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("回调通知地址必须是有效的 HTTPS 地址");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("回调通知地址必须是有效的 HTTPS 地址", exception);
        }
        return normalized;
    }

    private String requireGatewayHttpsUrl(String value) {
        String normalized = requireImportText(value, "网关基础地址不能为空", 256);
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("网关基础地址必须是有效的 HTTPS 地址");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("网关基础地址必须是有效的 HTTPS 地址", exception);
        }
        return normalized;
    }

    private String fingerprintChannel(PaymentChannelConfigEntity channel) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, channel.getChannelCode());
        appendFingerprintValue(canonical, channel.getChannelName());
        appendFingerprintValue(canonical, channel.getPaymentMethod());
        appendFingerprintValue(canonical, channel.getMerchantNo());
        appendFingerprintValue(canonical, channel.getMerchantAppId());
        appendFingerprintValue(canonical, channel.getCallbackSecret());
        appendFingerprintValue(canonical, channel.getCallbackSignAlgorithm());
        appendFingerprintValue(canonical, channel.getCallbackPublicKey());
        appendFingerprintValue(canonical, channel.getCallbackNotifyUrl());
        appendFingerprintValue(canonical, channel.getCertificateProfile());
        appendFingerprintValue(canonical, channel.getNotifySignWindowSec());
        appendFingerprintValue(canonical, channel.getRefundWindowDays());
        appendFingerprintValue(canonical, channel.getRiskControlTag());
        appendFingerprintValue(canonical, channel.getSceneScope());
        appendFingerprintValue(canonical, channel.getStatus());
        appendFingerprintValue(canonical, channel.getStatusType());
        appendFingerprintValue(canonical, channel.getDailyLimit());
        appendFingerprintValue(canonical, channel.getPriority());
        appendFingerprintValue(canonical, channel.getRouteWeight());
        return sha256(canonical.toString());
    }

    private String fingerprintGateway(PaymentGatewayConfigEntity gateway) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, gateway.getGatewayCode());
        appendFingerprintValue(canonical, gateway.getGatewayName());
        appendFingerprintValue(canonical, gateway.getAccessMode());
        appendFingerprintValue(canonical, gateway.getChannelScope());
        appendFingerprintValue(canonical, gateway.getEnvironmentScope());
        appendFingerprintValue(canonical, gateway.getApiBaseUrl());
        appendFingerprintValue(canonical, gateway.getProtocolType());
        appendFingerprintValue(canonical, gateway.getSignAlgorithm());
        appendFingerprintValue(canonical, gateway.getCertificateAlias());
        appendFingerprintValue(canonical, gateway.getCertificateStatus());
        appendFingerprintValue(canonical, gateway.getCertificateStatusType());
        appendFingerprintValue(canonical, gateway.getReleaseStage());
        appendFingerprintValue(canonical, gateway.getGrayStrategy());
        appendFingerprintValue(canonical, gateway.getCallbackWhitelist());
        appendFingerprintValue(canonical, gateway.getAdapterRegistry());
        appendFingerprintValue(canonical, gateway.getTimeoutMs());
        appendFingerprintValue(canonical, gateway.getRetryPolicy());
        appendFingerprintValue(canonical, gateway.getStatus());
        appendFingerprintValue(canonical, gateway.getStatusType());
        appendFingerprintValue(canonical, gateway.getPriority());
        return sha256(canonical.toString());
    }

    private String fingerprintControlPolicy(PaymentControlPolicyEntity policy) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, policy.getSourceAppId());
        appendFingerprintValue(canonical, policy.getSourceAppName());
        appendFingerprintValue(canonical, policy.getAllowedPaymentMethods());
        appendFingerprintValue(canonical, policy.getAllowedChannelCodes());
        appendFingerprintValue(canonical, policy.getAllowedMerchantNos());
        appendFingerprintValue(canonical, policy.getMinuteSubmitLimit());
        appendFingerprintValue(canonical, policy.getInterfaceMinuteSubmitLimit());
        appendFingerprintValue(canonical, policy.getTokenAuthRequired());
        appendFingerprintValue(canonical, policy.getAccessTokenValue());
        appendFingerprintValue(canonical, policy.getStrictMode());
        appendFingerprintValue(canonical, policy.getStatus());
        appendFingerprintValue(canonical, policy.getStatusType());
        return sha256(canonical.toString());
    }

    private String fingerprintAlertProvider(PaymentAlertProviderConfigEntity provider) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, provider.getProviderCode());
        appendFingerprintValue(canonical, provider.getProviderName());
        appendFingerprintValue(canonical, provider.getChannelCode());
        appendFingerprintValue(canonical, provider.getEndpointAlias());
        appendFingerprintValue(canonical, provider.getTemplateCode());
        appendFingerprintValue(canonical, provider.getTemplateBody());
        appendFingerprintValue(canonical, provider.getRouteRule());
        appendFingerprintValue(canonical, provider.getRoutePriority());
        appendFingerprintValue(canonical, provider.getRetryPolicy());
        appendFingerprintValue(canonical, provider.getRateLimitPolicy());
        appendFingerprintValue(canonical, provider.getStatus());
        appendFingerprintValue(canonical, provider.getStatusType());
        return sha256(canonical.toString());
    }

    private String fingerprintIssueDutyRoster(PaymentIssueDutyRosterEntity roster) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, roster.getRosterCode());
        appendFingerprintValue(canonical, roster.getIssueType());
        appendFingerprintValue(canonical, roster.getSeverity());
        appendFingerprintValue(canonical, roster.getResponsibilityGroup());
        appendFingerprintValue(canonical, roster.getReceiver());
        appendFingerprintValue(canonical, roster.getNotifyChannels());
        appendFingerprintValue(canonical, roster.getEscalationLevel());
        appendFingerprintValue(canonical, roster.getEscalationReceiver());
        appendFingerprintValue(canonical, roster.getEscalationPolicy());
        appendFingerprintValue(canonical, roster.getEscalationTimeoutMinutes());
        appendFingerprintValue(canonical, roster.getScheduleTag());
        appendFingerprintValue(canonical, roster.getEffectiveStartHour());
        appendFingerprintValue(canonical, roster.getEffectiveEndHour());
        appendFingerprintValue(canonical, roster.getWeekdayScope());
        appendFingerprintValue(canonical, roster.getHolidayStrategy());
        appendFingerprintValue(canonical, roster.getStatus());
        appendFingerprintValue(canonical, roster.getStatusType());
        return sha256(canonical.toString());
    }

    private PaymentReturnCodeImportReviewEntity requirePendingReturnCodeImportReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentReturnCodeImportReviewEntity review =
                paymentConfigMapper.findReturnCodeImportReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("返回码导入复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("返回码导入复核申请已完成");
        }
        return review;
    }

    private PaymentReturnCodeReviewEntity requirePendingReturnCodeReview(String requestNo) {
        String normalizedRequestNo = requireText(requestNo, "复核申请编号不能为空");
        PaymentReturnCodeReviewEntity review =
                paymentConfigMapper.findReturnCodeReviewForUpdate(normalizedRequestNo);
        if (review == null) {
            throw new IllegalArgumentException("渠道返回码复核申请不存在");
        }
        if (!"PENDING_REVIEW".equals(review.getReviewStatus())) {
            throw new IllegalArgumentException("渠道返回码复核申请已完成");
        }
        return review;
    }

    private String requireDistinctReturnCodeReviewer(
            PaymentReturnCodeReviewEntity review,
            PaymentReturnCodeReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeReturnCodeReview(String requestNo,
                                          String reviewStatus,
                                          String reviewStatusType,
                                          String reviewer,
                                          String reviewComment) {
        int affectedRows = paymentConfigMapper.completeReturnCodeReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("渠道返回码复核状态已变化，请刷新后重试");
        }
    }

    private String requireDistinctReturnCodeImportReviewer(
            PaymentReturnCodeImportReviewEntity review,
            PaymentReturnCodeImportReviewActionRequestDTO request) {
        String reviewer = requireReviewIdentity(
                request == null ? null : request.getReviewer(), "复核人声明不能为空");
        if (review.getRequester().equalsIgnoreCase(reviewer)) {
            throw new IllegalArgumentException("申请人与复核人声明不能相同");
        }
        return reviewer;
    }

    private void completeReturnCodeImportReview(String requestNo,
                                                String reviewStatus,
                                                String reviewStatusType,
                                                String reviewer,
                                                String reviewComment,
                                                String appliedBatchNo) {
        int affectedRows = paymentConfigMapper.completeReturnCodeImportReview(
                requestNo, reviewStatus, reviewStatusType, reviewer, reviewComment, appliedBatchNo);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("返回码导入复核状态已变化，请刷新后重试");
        }
    }

    private PaymentReturnCodeImportRequestDTO returnCodeImportRequest(String csvContent) {
        PaymentReturnCodeImportRequestDTO request = new PaymentReturnCodeImportRequestDTO();
        request.setCsvContent(csvContent);
        return request;
    }

    private PaymentReturnCodeImportResultDTO buildReturnCodeImportResult(
            PaymentReturnCodeImportRequestDTO request,
            List<PaymentChannelReturnCodeConfigDTO> currentMappings,
            Set<String> availableChannels) {
        String csvContent = normalizeCsvContent(request == null ? null : request.getCsvContent());
        List<PaymentChannelReturnCodeConfigDTO> safeCurrentMappings = currentMappings == null
                ? Collections.emptyList() : currentMappings;
        Map<String, PaymentChannelReturnCodeConfigDTO> currentByKey = new LinkedHashMap<>();
        for (PaymentChannelReturnCodeConfigDTO mapping : safeCurrentMappings) {
            currentByKey.put(returnCodeMappingKey(mapping.getChannelCode(), mapping.getChannelReturnCode()), mapping);
        }
        List<PaymentReturnCodeImportItemDTO> items = new ArrayList<>();
        Set<String> importedKeys = new HashSet<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(csvContent))) {
            if (!RETURN_CODE_IMPORT_HEADERS.equals(parser.getHeaderNames())) {
                throw new IllegalArgumentException("CSV 表头必须严格为 "
                        + String.join(",", RETURN_CODE_IMPORT_HEADERS));
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV 至少需要一行返回码映射");
            }
            if (records.size() > RETURN_CODE_IMPORT_MAX_ROWS) {
                throw new IllegalArgumentException("CSV 单次最多导入 " + RETURN_CODE_IMPORT_MAX_ROWS + " 行");
            }
            for (CSVRecord record : records) {
                items.add(buildReturnCodeImportItem(
                        record, currentByKey, importedKeys, availableChannels));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("CSV 解析失败，请检查引号、换行和 UTF-8 编码", exception);
        }
        PaymentReturnCodeImportResultDTO result = new PaymentReturnCodeImportResultDTO();
        result.setBaselineFingerprint(fingerprintReturnCodeMappings(safeCurrentMappings));
        result.setValid(Boolean.valueOf(items.stream().allMatch(item -> Boolean.TRUE.equals(item.getValid()))));
        result.setApplied(Boolean.FALSE);
        result.setTotalCount(Integer.valueOf(items.size()));
        result.setCreateCount(Integer.valueOf(countImportAction(items, "CREATE")));
        result.setUpdateCount(Integer.valueOf(countImportAction(items, "UPDATE")));
        result.setUnchangedCount(Integer.valueOf(countImportAction(items, "UNCHANGED")));
        result.setInvalidCount(Integer.valueOf(countImportAction(items, "INVALID")));
        result.setAppliedCount(Integer.valueOf(0));
        result.setItems(items);
        return result;
    }

    private PaymentReturnCodeImportItemDTO buildReturnCodeImportItem(
            CSVRecord record,
            Map<String, PaymentChannelReturnCodeConfigDTO> currentByKey,
            Set<String> importedKeys,
            Set<String> availableChannels) {
        List<String> errors = new ArrayList<>();
        PaymentChannelReturnCodeConfigDTO candidate = new PaymentChannelReturnCodeConfigDTO();
        candidate.setChannelCode(normalizeImportValue(record.get("channelCode")).toLowerCase(Locale.ROOT));
        candidate.setChannelReturnCode(normalizeImportValue(record.get("channelReturnCode")));
        candidate.setStandardizedCode(normalizeImportValue(record.get("standardizedCode")));
        candidate.setStandardizedMessage(normalizeImportValue(record.get("standardizedMessage")));
        candidate.setHandlingSuggestion(normalizeImportValue(record.get("handlingSuggestion")));
        candidate.setRetryable(normalizeImportValue(record.get("retryable")));
        candidate.setManualInterventionRequired(normalizeImportValue(record.get("manualInterventionRequired")));
        candidate.setMappingVersion(normalizeImportValue(record.get("mappingVersion")));
        candidate.setStatus(normalizeImportValue(record.get("status")).toUpperCase(Locale.ROOT));
        candidate.setStatusType(STATUS_ENABLED.equals(candidate.getStatus()) ? "success" : "danger");
        candidate.setArchiveStatus("ACTIVE");
        candidate.setArchiveStatusType("success");
        validateImportText(candidate.getChannelCode(), "channelCode", 64, errors);
        validateImportText(candidate.getChannelReturnCode(), "channelReturnCode", 64, errors);
        validateImportText(candidate.getStandardizedCode(), "standardizedCode", 64, errors);
        validateImportText(candidate.getStandardizedMessage(), "standardizedMessage", 256, errors);
        validateImportText(candidate.getHandlingSuggestion(), "handlingSuggestion", 256, errors);
        validateImportText(candidate.getMappingVersion(), "mappingVersion", 32, errors);
        if (StringUtils.hasText(candidate.getChannelCode())
                && !candidate.getChannelCode().matches("[a-z0-9][a-z0-9_-]*")) {
            errors.add("channelCode 仅支持小写字母、数字、下划线和连字符");
        } else if (StringUtils.hasText(candidate.getChannelCode())
                && !availableChannels.contains(candidate.getChannelCode())) {
            errors.add("channelCode 对应的支付渠道不存在");
        }
        if (StringUtils.hasText(candidate.getStandardizedCode())
                && !candidate.getStandardizedCode().matches("[A-Z0-9][A-Z0-9._-]*")) {
            errors.add("standardizedCode 仅支持大写字母、数字、点、下划线和连字符");
        }
        if (!Arrays.asList("是", "否", "未知").contains(candidate.getRetryable())) {
            errors.add("retryable 仅支持 是/否/未知");
        }
        if (!Arrays.asList("是", "否").contains(candidate.getManualInterventionRequired())) {
            errors.add("manualInterventionRequired 仅支持 是/否");
        }
        if (!STATUS_ENABLED.equals(candidate.getStatus()) && !STATUS_DISABLED.equals(candidate.getStatus())) {
            errors.add("status 仅支持 ENABLED/DISABLED");
        }
        try {
            candidate.setPriority(Integer.valueOf(normalizeImportValue(record.get("priority"))));
            if (candidate.getPriority().intValue() < 0) {
                errors.add("priority 不能小于 0");
            }
        } catch (NumberFormatException exception) {
            errors.add("priority 必须为整数");
        }
        String key = returnCodeMappingKey(candidate.getChannelCode(), candidate.getChannelReturnCode());
        if (!importedKeys.add(key)) {
            errors.add("CSV 内渠道编码与返回码重复");
        }
        PaymentChannelReturnCodeConfigDTO current = currentByKey.get(key);
        if (current != null && "ARCHIVED".equals(current.getArchiveStatus())) {
            errors.add("已归档映射禁止通过批量导入修改");
        }
        PaymentReturnCodeImportItemDTO item = new PaymentReturnCodeImportItemDTO();
        item.setRowNumber(Long.valueOf(record.getRecordNumber() + 1));
        item.setCandidate(candidate);
        if (!errors.isEmpty()) {
            item.setAction("INVALID");
            item.setValid(Boolean.FALSE);
            item.setMessage(String.join("；", errors));
            item.setChangedFields(Collections.emptyList());
            return item;
        }
        if (current == null) {
            item.setAction("CREATE");
            item.setValid(Boolean.TRUE);
            item.setMessage("新增映射");
            item.setChangedFields(Collections.emptyList());
            return item;
        }
        List<String> changedFields = findReturnCodeChangedFields(current, candidate);
        if (!changedFields.isEmpty()
                && Objects.equals(current.getMappingVersion(), candidate.getMappingVersion())) {
            item.setAction("INVALID");
            item.setValid(Boolean.FALSE);
            item.setMessage("更新映射字段时必须同时变更 mappingVersion");
            item.setChangedFields(changedFields);
            return item;
        }
        item.setAction(changedFields.isEmpty() ? "UNCHANGED" : "UPDATE");
        item.setValid(Boolean.TRUE);
        item.setMessage(changedFields.isEmpty() ? "与当前配置一致" : "更新 " + String.join("、", changedFields));
        item.setChangedFields(changedFields);
        return item;
    }

    private List<String> findReturnCodeChangedFields(
            PaymentChannelReturnCodeConfigDTO current,
            PaymentChannelReturnCodeConfigDTO candidate) {
        List<String> fields = new ArrayList<>();
        addChangedField(fields, "standardizedCode", current.getStandardizedCode(), candidate.getStandardizedCode());
        addChangedField(fields, "standardizedMessage", current.getStandardizedMessage(), candidate.getStandardizedMessage());
        addChangedField(fields, "handlingSuggestion", current.getHandlingSuggestion(), candidate.getHandlingSuggestion());
        addChangedField(fields, "retryable", current.getRetryable(), candidate.getRetryable());
        addChangedField(fields, "manualInterventionRequired",
                current.getManualInterventionRequired(), candidate.getManualInterventionRequired());
        addChangedField(fields, "mappingVersion", current.getMappingVersion(), candidate.getMappingVersion());
        addChangedField(fields, "status", current.getStatus(), candidate.getStatus());
        addChangedField(fields, "priority", current.getPriority(), candidate.getPriority());
        return fields;
    }

    private void addChangedField(List<String> fields, String fieldName, Object current, Object candidate) {
        if (!Objects.equals(current, candidate)) {
            fields.add(fieldName);
        }
    }

    private int countImportAction(List<PaymentReturnCodeImportItemDTO> items, String action) {
        return (int) items.stream().filter(item -> action.equals(item.getAction())).count();
    }

    private String normalizeCsvContent(String csvContent) {
        if (!StringUtils.hasText(csvContent)) {
            throw new IllegalArgumentException("CSV 内容不能为空");
        }
        String normalized = csvContent.charAt(0) == '\uFEFF' ? csvContent.substring(1) : csvContent;
        if (normalized.getBytes(StandardCharsets.UTF_8).length > RETURN_CODE_IMPORT_MAX_BYTES) {
            throw new IllegalArgumentException("CSV 大小不能超过 512 KB");
        }
        return normalized;
    }

    private Set<String> resolveReturnCodeImportChannels() {
        List<PaymentChannelConfigDTO> channels = paymentConfigMapper.findChannels();
        Set<String> channelCodes = new HashSet<>();
        if (channels != null) {
            for (PaymentChannelConfigDTO channel : channels) {
                if (channel != null && StringUtils.hasText(channel.getChannelCode())) {
                    channelCodes.add(channel.getChannelCode().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return channelCodes;
    }

    private String normalizeImportValue(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateImportText(String value, String fieldName, int maxLength, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(fieldName + " 不能为空");
        } else if (value.length() > maxLength) {
            errors.add(fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private String requireImportText(String value, String message, int maxLength) {
        String normalized = requireText(value, message);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message.replace("不能为空", "长度不能超过 " + maxLength));
        }
        return normalized;
    }

    private String returnCodeMappingKey(String channelCode, String channelReturnCode) {
        return normalizeImportValue(channelCode).toLowerCase(Locale.ROOT)
                + "\u0000" + normalizeImportValue(channelReturnCode).toUpperCase(Locale.ROOT);
    }

    private String fingerprintReturnCodeMapping(PaymentChannelReturnCodeConfigDTO mapping) {
        return fingerprintReturnCodeMappings(Collections.singletonList(mapping));
    }

    private String fingerprintReturnCodeMappings(List<PaymentChannelReturnCodeConfigDTO> mappings) {
        List<PaymentChannelReturnCodeConfigDTO> sortedMappings = new ArrayList<>(
                mappings == null ? Collections.emptyList() : mappings);
        sortedMappings.sort(Comparator
                .comparing((PaymentChannelReturnCodeConfigDTO mapping) ->
                        normalizeImportValue(mapping.getChannelCode()).toLowerCase(Locale.ROOT))
                .thenComparing(mapping -> normalizeImportValue(mapping.getChannelReturnCode())
                        .toUpperCase(Locale.ROOT)));
        StringBuilder canonical = new StringBuilder();
        for (PaymentChannelReturnCodeConfigDTO mapping : sortedMappings) {
            appendFingerprintValue(canonical, mapping.getChannelCode());
            appendFingerprintValue(canonical, mapping.getChannelReturnCode());
            appendFingerprintValue(canonical, mapping.getStandardizedCode());
            appendFingerprintValue(canonical, mapping.getStandardizedMessage());
            appendFingerprintValue(canonical, mapping.getHandlingSuggestion());
            appendFingerprintValue(canonical, mapping.getRetryable());
            appendFingerprintValue(canonical, mapping.getManualInterventionRequired());
            appendFingerprintValue(canonical, mapping.getMappingVersion());
            appendFingerprintValue(canonical, mapping.getArchiveStatus());
            appendFingerprintValue(canonical, mapping.getStatus());
            appendFingerprintValue(canonical, mapping.getPriority());
        }
        return sha256(canonical.toString());
    }

    private void appendFingerprintValue(StringBuilder builder, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        builder.append(text.length()).append(':').append(text).append('|');
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 支持", exception);
        }
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
