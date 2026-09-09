package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyVersionDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
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
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeStatusReviewRequestDTO;
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

    /** 提交支付渠道启停候选，不改变运行态。 */
    List<PaymentChannelReviewDTO> submitChannelStatusReview(PaymentChannelStatusReviewRequestDTO request);

    /** 提交支付渠道新增或编辑完整候选，不改变运行态。 */
    List<PaymentChannelReviewDTO> submitChannelReview(PaymentChannelReviewSubmitRequestDTO request);

    /** 查询支付渠道状态变更复核申请。 */
    List<PaymentChannelReviewDTO> listChannelReviews();

    /** 通过复核并应用支付渠道状态候选。 */
    List<PaymentChannelReviewDTO> approveChannelReview(
            String requestNo, PaymentChannelReviewActionRequestDTO request);

    /** 驳回支付渠道状态候选，不改变运行态。 */
    List<PaymentChannelReviewDTO> rejectChannelReview(
            String requestNo, PaymentChannelReviewActionRequestDTO request);

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

    /** 提交异常告警值班路由新增候选，不改变运行态。 */
    List<PaymentIssueDutyRosterReviewDTO> createIssueDutyRoster(
            PaymentIssueDutyRosterUpsertRequestDTO request);

    /** 提交异常告警值班路由编辑候选，不改变运行态。 */
    List<PaymentIssueDutyRosterReviewDTO> updateIssueDutyRoster(
            String rosterCode, PaymentIssueDutyRosterUpsertRequestDTO request);

    /** 查询异常告警值班路由配置变更复核申请。 */
    List<PaymentIssueDutyRosterReviewDTO> listIssueDutyRosterReviews();

    /** 通过复核并应用异常告警值班路由完整候选。 */
    List<PaymentIssueDutyRosterReviewDTO> approveIssueDutyRosterReview(
            String requestNo, PaymentIssueDutyRosterReviewActionRequestDTO request);

    /** 驳回异常告警值班路由完整候选，不改变运行态。 */
    List<PaymentIssueDutyRosterReviewDTO> rejectIssueDutyRosterReview(
            String requestNo, PaymentIssueDutyRosterReviewActionRequestDTO request);

    /** 提交渠道返回码启停候选，不改变运行态配置。 */
    List<PaymentReturnCodeReviewDTO> submitReturnCodeStatusReview(
            PaymentReturnCodeStatusReviewRequestDTO request);

    /** 查询渠道返回码单条变更复核申请。 */
    List<PaymentReturnCodeReviewDTO> listReturnCodeReviews();

    /** 通过复核并应用渠道返回码单条状态候选。 */
    List<PaymentReturnCodeReviewDTO> approveReturnCodeReview(
            String requestNo, PaymentReturnCodeReviewActionRequestDTO request);

    /** 驳回渠道返回码单条状态候选，不改变运行态配置。 */
    List<PaymentReturnCodeReviewDTO> rejectReturnCodeReview(
            String requestNo, PaymentReturnCodeReviewActionRequestDTO request);

    /**
     * 预检渠道返回码 CSV，不修改运行态配置。
     */
    PaymentReturnCodeImportResultDTO previewReturnCodeImport(PaymentReturnCodeImportRequestDTO request);

    /** 查询渠道返回码导入复核申请。 */
    List<PaymentReturnCodeImportReviewDTO> listReturnCodeImportReviews();

    /** 提交渠道返回码导入候选，不改变运行态配置。 */
    List<PaymentReturnCodeImportReviewDTO> submitReturnCodeImportReview(
            PaymentReturnCodeImportReviewSubmitRequestDTO request);

    /** 通过复核并原子应用渠道返回码导入候选。 */
    List<PaymentReturnCodeImportReviewDTO> approveReturnCodeImportReview(
            String requestNo, PaymentReturnCodeImportReviewActionRequestDTO request);

    /** 驳回渠道返回码导入候选，不改变运行态配置。 */
    List<PaymentReturnCodeImportReviewDTO> rejectReturnCodeImportReview(
            String requestNo, PaymentReturnCodeImportReviewActionRequestDTO request);

    /** 提交支付网关启停候选，不改变运行态。 */
    List<PaymentGatewayReviewDTO> submitGatewayStatusReview(PaymentGatewayStatusReviewRequestDTO request);

    /** 提交支付网关新增或编辑完整候选进入复核。 */
    List<PaymentGatewayReviewDTO> submitGatewayReview(PaymentGatewayReviewSubmitRequestDTO request);

    /** 查询支付网关配置变更复核申请。 */
    List<PaymentGatewayReviewDTO> listGatewayReviews();

    /** 通过复核并应用支付网关状态候选。 */
    List<PaymentGatewayReviewDTO> approveGatewayReview(
            String requestNo, PaymentGatewayReviewActionRequestDTO request);

    /** 驳回支付网关状态候选，不改变运行态。 */
    List<PaymentGatewayReviewDTO> rejectGatewayReview(
            String requestNo, PaymentGatewayReviewActionRequestDTO request);

    /** 提交支付控制策略启停候选，不改变运行态。 */
    List<PaymentControlPolicyReviewDTO> submitControlPolicyStatusReview(
            PaymentControlPolicyStatusReviewRequestDTO request);

    /** 提交支付控制策略新增或编辑完整候选，不改变运行态。 */
    List<PaymentControlPolicyReviewDTO> submitControlPolicyReview(
            PaymentControlPolicyReviewSubmitRequestDTO request);

    /** 查询支付控制策略配置变更复核申请。 */
    List<PaymentControlPolicyReviewDTO> listControlPolicyReviews();

    /** 通过复核并应用支付控制策略完整候选。 */
    List<PaymentControlPolicyReviewDTO> approveControlPolicyReview(
            String requestNo, PaymentControlPolicyReviewActionRequestDTO request);

    /** 驳回支付控制策略完整候选，不改变运行态。 */
    List<PaymentControlPolicyReviewDTO> rejectControlPolicyReview(
            String requestNo, PaymentControlPolicyReviewActionRequestDTO request);

    /** 查询支付控制策略不可变版本历史。 */
    List<PaymentControlPolicyVersionDTO> listControlPolicyVersions(String sourceAppId);

    /** 提交指定历史版本的回滚候选，不改变运行态。 */
    List<PaymentControlPolicyReviewDTO> submitControlPolicyRollbackReview(
            String sourceAppId, PaymentControlPolicyRollbackRequestDTO request);

    /**
     * 启停告警通知供应商配置。
     */
    PaymentConfigOverviewDTO toggleAlertProvider(PaymentConfigToggleRequestDTO request);

    /**
     * 执行支付控制策略自检并回写准入结果。
     */
    PaymentConfigOverviewDTO runControlPolicySelfCheck(PaymentConfigToggleRequestDTO request);

    /** 提交异常告警值班路由启停候选，不改变运行态。 */
    List<PaymentIssueDutyRosterReviewDTO> toggleIssueDutyRoster(
            PaymentIssueDutyRosterStatusReviewRequestDTO request);

    /**
     * 批量执行启用中支付控制策略自检并回写准入结果。
     */
    PaymentControlPolicySelfCheckSummaryDTO runAllEnabledControlPolicySelfChecks();
}
