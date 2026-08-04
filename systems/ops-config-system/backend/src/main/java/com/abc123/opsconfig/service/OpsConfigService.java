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

    OpsConfigSummaryDTO toggleAgreementTemplate(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleBusinessLine(ToggleRequestDTO request);

    OpsConfigSummaryDTO togglePaymentType(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleCashierTemplate(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleChannelProfile(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleRoutingRule(ToggleRequestDTO request);

    OpsConfigSummaryDTO toggleSystemControl(ToggleRequestDTO request);
}
