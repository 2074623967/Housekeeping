package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
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
     * 启停路由规则。
     */
    PaymentConfigOverviewDTO toggleRouteRule(PaymentConfigToggleRequestDTO request);

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
     * 查询路由规则历史版本。
     */
    List<PaymentRouteRuleVersionDTO> listRouteRuleVersions(String ruleCode);

    /**
     * 将路由规则恢复为指定历史版本，并生成新的回滚版本。
     */
    PaymentConfigOverviewDTO rollbackRouteRule(String ruleCode, PaymentRouteRuleRollbackRequestDTO request);

    /**
     * 启停支付协议配置。
     */
    PaymentConfigOverviewDTO toggleProtocol(PaymentConfigToggleRequestDTO request);

    /**
     * 新增支付协议配置。
     */
    PaymentConfigOverviewDTO createProtocol(PaymentProtocolUpsertRequestDTO request);

    /**
     * 编辑支付协议配置。
     */
    PaymentConfigOverviewDTO updateProtocol(String protocolCode, PaymentProtocolUpsertRequestDTO request);

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
