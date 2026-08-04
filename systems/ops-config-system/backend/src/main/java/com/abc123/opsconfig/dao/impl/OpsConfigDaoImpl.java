package com.abc123.opsconfig.dao.impl;

import com.abc123.opsconfig.dao.OpsConfigDao;
import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.mapper.OpsConfigMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 运营配置 DAO 实现。
 */
@Repository
public class OpsConfigDaoImpl implements OpsConfigDao {

    private final OpsConfigMapper mapper;

    public OpsConfigDaoImpl(OpsConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AgreementTemplateDTO> findAgreementTemplates() {
        return mapper.findAgreementTemplates();
    }

    @Override
    public List<BusinessLineDTO> findBusinessLines() {
        return mapper.findBusinessLines();
    }

    @Override
    public List<PaymentTypeDTO> findPaymentTypes() {
        return mapper.findPaymentTypes();
    }

    @Override
    public List<CashierTemplateDTO> findCashierTemplates() {
        return mapper.findCashierTemplates();
    }

    @Override
    public List<ChannelProfileDTO> findChannelProfiles() {
        return mapper.findChannelProfiles();
    }

    @Override
    public List<RoutingRuleDTO> findRoutingRules() {
        return mapper.findRoutingRules();
    }

    @Override
    public List<SystemControlDTO> findSystemControls() {
        return mapper.findSystemControls();
    }

    @Override
    public CashierTemplateDTO findEnabledCashierTemplateByTerminal(String terminalType) {
        return mapper.findEnabledCashierTemplateByTerminal(terminalType);
    }

    @Override
    public RoutingRuleDTO findEnabledRoutingRule(String businessCode, String payType) {
        return mapper.findEnabledRoutingRule(businessCode, payType);
    }

    @Override
    public List<SystemControlDTO> findEnabledSystemControls() {
        return mapper.findEnabledSystemControls();
    }

    @Override
    public long countEnabledAgreementTemplates() {
        return mapper.countEnabledAgreementTemplates();
    }

    @Override
    public long countEnabledBusinessLines() {
        return mapper.countEnabledBusinessLines();
    }

    @Override
    public long countEnabledPaymentTypes() {
        return mapper.countEnabledPaymentTypes();
    }

    @Override
    public long countEnabledChannelProfiles() {
        return mapper.countEnabledChannelProfiles();
    }

    @Override
    public int updateAgreementTemplateStatus(String templateCode, String status, String statusType) {
        return mapper.updateAgreementTemplateStatus(templateCode, status, statusType);
    }

    @Override
    public int updateBusinessLineStatus(String businessCode, String status, String statusType) {
        return mapper.updateBusinessLineStatus(businessCode, status, statusType);
    }

    @Override
    public int updatePaymentTypeStatus(String typeCode, String status, String statusType) {
        return mapper.updatePaymentTypeStatus(typeCode, status, statusType);
    }

    @Override
    public int updateCashierTemplateStatus(String templateCode, String status, String statusType) {
        return mapper.updateCashierTemplateStatus(templateCode, status, statusType);
    }

    @Override
    public int updateChannelProfileStatus(String channelCode, String status, String statusType) {
        return mapper.updateChannelProfileStatus(channelCode, status, statusType);
    }

    @Override
    public int updateRoutingRuleStatus(String routeCode, String status, String statusType) {
        return mapper.updateRoutingRuleStatus(routeCode, status, statusType);
    }

    @Override
    public int updateSystemControlStatus(String controlCode, String status, String statusType) {
        return mapper.updateSystemControlStatus(controlCode, status, statusType);
    }
}
