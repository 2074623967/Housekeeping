package com.abc123.opsconfig.dao;

import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import java.util.List;

/**
 * 运营配置数据访问编排层。
 */
public interface OpsConfigDao {

    List<AgreementTemplateDTO> findAgreementTemplates();

    List<BusinessLineDTO> findBusinessLines();

    List<PaymentTypeDTO> findPaymentTypes();

    List<CashierTemplateDTO> findCashierTemplates();

    List<ChannelProfileDTO> findChannelProfiles();

    List<RoutingRuleDTO> findRoutingRules();

    List<SystemControlDTO> findSystemControls();

    CashierTemplateDTO findEnabledCashierTemplateByTerminal(String terminalType);

    RoutingRuleDTO findEnabledRoutingRule(String businessCode, String payType);

    List<SystemControlDTO> findEnabledSystemControls();

    long countEnabledAgreementTemplates();

    long countEnabledBusinessLines();

    long countEnabledPaymentTypes();

    long countEnabledChannelProfiles();

    int updateAgreementTemplateStatus(String templateCode, String status, String statusType);

    int updateBusinessLineStatus(String businessCode, String status, String statusType);

    int updatePaymentTypeStatus(String typeCode, String status, String statusType);

    int updateCashierTemplateStatus(String templateCode, String status, String statusType);

    int updateChannelProfileStatus(String channelCode, String status, String statusType);

    int updateRoutingRuleStatus(String routeCode, String status, String statusType);

    int updateSystemControlStatus(String controlCode, String status, String statusType);
}
