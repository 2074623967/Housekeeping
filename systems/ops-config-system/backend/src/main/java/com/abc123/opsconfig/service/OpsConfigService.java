package com.abc123.opsconfig.service;

import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.OpsConfigSummaryDTO;
import com.abc123.opsconfig.dto.OpsConfigEffectiveSnapshotDTO;
import com.abc123.opsconfig.dto.OpsConfigSnapshotQueryDTO;
import com.abc123.opsconfig.dto.PageResultDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.dto.ToggleRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeApprovalRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeCreateRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeDTO;
import com.abc123.opsconfig.dto.OpsConfigChangePublishRequestDTO;

/**
 * 运营配置服务。
 */
public interface OpsConfigService {

    OpsConfigSummaryDTO summary();

    PageResultDTO<AgreementTemplateDTO> agreementTemplates();

    PageResultDTO<BusinessLineDTO> businessLines();

    PageResultDTO<PaymentTypeDTO> paymentTypes();

    PageResultDTO<CashierTemplateDTO> cashierTemplates();

    PageResultDTO<ChannelProfileDTO> channelProfiles();

    PageResultDTO<RoutingRuleDTO> routingRules();

    PageResultDTO<SystemControlDTO> systemControls();

    OpsConfigEffectiveSnapshotDTO effectiveSnapshot(OpsConfigSnapshotQueryDTO query);

    /**
     * 导出有效配置快照，便于支付、风控和运营留档。
     *
     * @param query 快照查询条件
     * @return CSV 二进制内容
     */
    byte[] exportEffectiveSnapshot(OpsConfigSnapshotQueryDTO query);

    OpsConfigSummaryDTO toggleAgreementTemplate(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleBusinessLine(ToggleRequestDTO request);

    OpsConfigSummaryDTO togglePaymentType(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleCashierTemplate(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleChannelProfile(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleRoutingRule(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleSystemControl(ToggleRequestDTO request);

    /**
     * 创建需要审批的配置变更。
     *
     * @param request 变更申请
     * @return 变更记录
     */
    OpsConfigChangeDTO createConfigChange(OpsConfigChangeCreateRequestDTO request);

    /**
     * 查询配置变更记录。
     *
     * @return 变更记录列表
     */
    PageResultDTO<OpsConfigChangeDTO> configChanges();

    /**
     * 审批配置变更。
     *
     * @param request 审批请求
     * @return 最新变更记录
     */
    OpsConfigChangeDTO approveConfigChange(OpsConfigChangeApprovalRequestDTO request);

    /**
     * 在灰度时间窗内发布已审批变更。
     *
     * @param request 发布请求
     * @return 最新变更记录
     */
    OpsConfigChangeDTO publishConfigChange(OpsConfigChangePublishRequestDTO request);
}
