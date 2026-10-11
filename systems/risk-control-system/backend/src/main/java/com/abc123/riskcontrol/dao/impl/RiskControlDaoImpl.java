package com.abc123.riskcontrol.dao.impl;

import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.entity.BlocklistEntity;
import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.LimitRuleEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import com.abc123.riskcontrol.entity.RiskPolicyEntity;
import com.abc123.riskcontrol.mapper.RiskControlMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 风控 DAO 实现。
 */
@Repository
public class RiskControlDaoImpl implements RiskControlDao {

    private final RiskControlMapper mapper;

    public RiskControlDaoImpl(RiskControlMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RiskPolicyDTO> findPolicies() {
        return safeList(mapper.findPolicies());
    }

    @Override
    public List<LimitRuleDTO> findLimitRules() {
        return safeList(mapper.findLimitRules());
    }

    @Override
    public List<BlocklistDTO> findBlocklists() {
        return safeList(mapper.findBlocklists());
    }

    @Override
    public List<InterceptEventDTO> findInterceptEvents() {
        return safeList(mapper.findInterceptEvents());
    }

    @Override
    public List<ReviewOrderDTO> findReviewOrders() {
        return safeList(mapper.findReviewOrders());
    }

    @Override
    public List<MonitorRuleDTO> findMonitorRules() {
        return safeList(mapper.findMonitorRules());
    }

    @Override
    public long countEnabledPolicies() {
        return mapper.countEnabledPolicies();
    }

    @Override
    public long countEnabledLimits() {
        return mapper.countEnabledLimits();
    }

    @Override
    public long countPendingReviews() {
        return mapper.countPendingReviews();
    }

    @Override
    public long countInterceptedEvents() {
        return mapper.countInterceptedEvents();
    }

    @Override
    public List<BlocklistEntity> findEnabledBlocklists() {
        return safeList(mapper.findEnabledBlocklists());
    }

    @Override
    public List<LimitRuleEntity> findEnabledLimitRulesByScene(String sceneCode) {
        return safeList(mapper.findEnabledLimitRulesByScene(sceneCode));
    }

    @Override
    public List<RiskPolicyEntity> findEnabledPoliciesForDecision() {
        return safeList(mapper.findEnabledPoliciesForDecision());
    }

    @Override
    public ReviewOrderEntity findReviewOrderByReviewNo(String reviewNo) {
        return mapper.findReviewOrderByReviewNo(reviewNo);
    }

    @Override
    public ReviewOrderEntity findLatestReviewOrderByBusinessNo(String businessNo) {
        return mapper.findLatestReviewOrderByBusinessNo(businessNo);
    }

    @Override
    public ReviewOrderEntity findReviewOrderByBusinessKeyForUpdate(byte[] businessKey) {
        return mapper.findReviewOrderByBusinessKeyForUpdate(businessKey);
    }

    @Override
    public InterceptEventEntity findLatestInterceptEvent(String paymentOrderId, String hitPolicy,
                                                        String decisionResult, String sourceSystem) {
        return mapper.findLatestInterceptEvent(paymentOrderId, hitPolicy, decisionResult, sourceSystem);
    }

    @Override
    public InterceptEventEntity findInterceptEventByKeyForUpdate(byte[] eventKey) {
        return mapper.findInterceptEventByKeyForUpdate(eventKey);
    }

    @Override
    public int insertInterceptEvent(InterceptEventEntity entity) {
        return mapper.insertInterceptEvent(entity);
    }

    @Override
    public int insertReviewOrder(ReviewOrderEntity entity) {
        return mapper.insertReviewOrder(entity);
    }

    @Override
    public int updatePolicyStatus(String policyCode, String status, String statusType) {
        return mapper.updatePolicyStatus(policyCode, status, statusType);
    }

    @Override
    public int updateLimitRuleStatus(String ruleCode, String status, String statusType) {
        return mapper.updateLimitRuleStatus(ruleCode, status, statusType);
    }

    @Override
    public int updateBlocklistStatus(String blockCode, String status, String statusType) {
        return mapper.updateBlocklistStatus(blockCode, status, statusType);
    }

    @Override
    public int updateMonitorRuleStatus(String monitorCode, String status, String statusType) {
        return mapper.updateMonitorRuleStatus(monitorCode, status, statusType);
    }

    @Override
    public int updateReviewOrderWhenPending(String reviewNo, String status, String statusType, String reviewer) {
        return mapper.updateReviewOrderWhenPending(reviewNo, status, statusType, reviewer);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : values.stream().filter(value -> value != null).collect(Collectors.toList());
    }
}
