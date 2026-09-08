package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import java.util.List;

/**
 * 支付配置中心服务。
 */
public interface PaymentConfigService {

    /**
     * 查询支付配置中心总览。
     */
    PaymentConfigOverviewDTO overview();

    /**
     * 导出支付配置治理快照，供运营、财务、测试和发布审计留痕。
     */
    String exportGovernanceSnapshotCsv(String section);

    /**
     * 启停支付渠道。
     */
    PaymentConfigOverviewDTO toggleChannel(PaymentConfigToggleRequestDTO request);

    /**
     * 提交路由规则启停候选进入复核，不直接改变运行态。
     */
    List<PaymentRouteRuleReviewDTO> submitRouteRuleStatusReview(
            PaymentRouteRuleStatusReviewRequestDTO request);

    /**
     * 新增支付路由规则。
     */
    PaymentConfigOverviewDTO createRouteRule(PaymentRouteRuleUpsertRequestDTO request);

    /**
     * 编辑支付路由规则。
     */
    PaymentConfigOverviewDTO updateRouteRule(String ruleCode, PaymentRouteRuleUpsertRequestDTO request);

    /**
     * 校验路由表达式并使用样例上下文模拟命中。
     */
    PaymentRouteRuleValidationDTO validateRouteRule(PaymentRouteRuleValidationRequestDTO request);

    /**
     * 检测当前启用路由规则的确定冲突、遮蔽和潜在优先级冲突。
     */
    List<PaymentRouteRuleConflictDTO> listRouteRuleConflicts();

    /**
     * 查询路由规则复核申请。
     */
    List<PaymentRouteRuleReviewDTO> listRouteRuleReviews();

    /**
     * 提交路由规则候选快照进入复核，不改变运行态规则。
     */
    List<PaymentRouteRuleReviewDTO> submitRouteRuleReview(PaymentRouteRuleReviewSubmitRequestDTO request);

    /**
     * 通过复核并发布候选快照。
     */
    List<PaymentRouteRuleReviewDTO> approveRouteRuleReview(
            String requestNo, PaymentRouteRuleReviewActionRequestDTO request);

    /**
     * 驳回复核申请，不改变运行态规则。
     */
    List<PaymentRouteRuleReviewDTO> rejectRouteRuleReview(
            String requestNo, PaymentRouteRuleReviewActionRequestDTO request);

    /**
     * 查询路由规则历史版本。
     */
    List<PaymentRouteRuleVersionDTO> listRouteRuleVersions(String ruleCode);

    /**
     * 提交指定历史版本的回滚候选进入复核，不直接改变运行态。
     */
    List<PaymentRouteRuleReviewDTO> submitRouteRuleRollbackReview(
            String ruleCode, PaymentRouteRuleRollbackRequestDTO request);

    /**
     * 提交支付协议启停候选进入复核，不直接改变运行态。
     */
    List<PaymentProtocolReviewDTO> submitProtocolStatusReview(PaymentProtocolStatusReviewRequestDTO request);

    /**
     * 查询支付协议复核申请。
     */
    List<PaymentProtocolReviewDTO> listProtocolReviews();

    /**
     * 提交支付协议候选快照进入复核，不改变运行态协议。
     */
    List<PaymentProtocolReviewDTO> submitProtocolReview(PaymentProtocolReviewSubmitRequestDTO request);

    /**
     * 通过复核并发布支付协议候选快照。
     */
    List<PaymentProtocolReviewDTO> approveProtocolReview(
            String requestNo, PaymentProtocolReviewActionRequestDTO request);

    /**
     * 驳回支付协议复核申请，不改变运行态协议。
     */
    List<PaymentProtocolReviewDTO> rejectProtocolReview(
            String requestNo, PaymentProtocolReviewActionRequestDTO request);

    /**
     * 查询支付协议不可变版本历史。
     */
    List<PaymentProtocolVersionDTO> listProtocolVersions(String protocolCode);

    /**
     * 提交指定支付协议历史版本的回滚候选进入复核，不直接改变运行态。
     */
    List<PaymentProtocolReviewDTO> submitProtocolRollbackReview(
            String protocolCode, PaymentProtocolRollbackRequestDTO request);

    /**
     * 新增异常告警值班路由配置。
     */
    PaymentConfigOverviewDTO createIssueDutyRoster(PaymentIssueDutyRosterUpsertRequestDTO request);

    /**
     * 编辑异常告警值班路由配置。
     */
    PaymentConfigOverviewDTO updateIssueDutyRoster(String rosterCode, PaymentIssueDutyRosterUpsertRequestDTO request);

    /**
     * 启停渠道返回码映射配置。
     */
    PaymentConfigOverviewDTO toggleReturnCodeMapping(PaymentConfigToggleRequestDTO request);

    /**
     * 启停支付网关接入配置。
     */
    PaymentConfigOverviewDTO toggleGateway(PaymentConfigToggleRequestDTO request);

    /**
     * 启停支付控制策略配置。
     */
    PaymentConfigOverviewDTO toggleControlPolicy(PaymentConfigToggleRequestDTO request);

    /**
     * 启停告警通知供应商配置。
     */
    PaymentConfigOverviewDTO toggleAlertProvider(PaymentConfigToggleRequestDTO request);

    /**
     * 执行支付控制策略自检并回写准入结果。
     */
    PaymentConfigOverviewDTO runControlPolicySelfCheck(PaymentConfigToggleRequestDTO request);

    /**
     * 启停异常告警值班路由配置。
     */
    PaymentConfigOverviewDTO toggleIssueDutyRoster(PaymentConfigToggleRequestDTO request);

    /**
     * 批量执行启用中支付控制策略自检并回写准入结果。
     */
    PaymentControlPolicySelfCheckSummaryDTO runAllEnabledControlPolicySelfChecks();
}
