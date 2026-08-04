package com.abc123.riskcontrol.dao.impl;

import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.mapper.RiskControlMapper;
import java.util.List;
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
        return mapper.findPolicies();
    }

    @Override
    public List<LimitRuleDTO> findLimitRules() {
        return mapper.findLimitRules();
    }

    @Override
    public List<BlocklistDTO> findBlocklists() {
        return mapper.findBlocklists();
    }

    @Override
    public List<InterceptEventDTO> findInterceptEvents() {
        return mapper.findInterceptEvents();
    }

    @Override
    public List<ReviewOrderDTO> findReviewOrders() {
        return mapper.findReviewOrders();
    }

    @Override
    public List<MonitorRuleDTO> findMonitorRules() {
        return mapper.findMonitorRules();
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
    public int updateReviewOrder(String reviewNo, String status, String statusType, String reviewer) {
        return mapper.updateReviewOrder(reviewNo, status, statusType, reviewer);
    }
}

