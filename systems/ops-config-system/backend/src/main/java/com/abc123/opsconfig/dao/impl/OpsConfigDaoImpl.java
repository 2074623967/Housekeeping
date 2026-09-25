package com.abc123.opsconfig.dao.impl;

import com.abc123.opsconfig.dao.OpsConfigDao;
import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeDTO;
import com.abc123.opsconfig.dto.OpsConfigRefreshEventDTO;
import com.abc123.opsconfig.mapper.OpsConfigMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
        return safeList(mapper.findAgreementTemplates());
    }

    @Override
    public List<BusinessLineDTO> findBusinessLines() {
        return safeList(mapper.findBusinessLines());
    }

    @Override
    public List<PaymentTypeDTO> findPaymentTypes() {
        return safeList(mapper.findPaymentTypes());
    }

    @Override
    public List<CashierTemplateDTO> findCashierTemplates() {
        return safeList(mapper.findCashierTemplates());
    }

    @Override
    public List<ChannelProfileDTO> findChannelProfiles() {
        return safeList(mapper.findChannelProfiles());
    }

    @Override
    public List<RoutingRuleDTO> findRoutingRules() {
        return safeList(mapper.findRoutingRules());
    }

    @Override
    public List<SystemControlDTO> findSystemControls() {
        return safeList(mapper.findSystemControls());
    }

    @Override
    public BusinessLineDTO findEnabledBusinessLineByCode(String businessCode) {
        return mapper.findEnabledBusinessLineByCode(businessCode);
    }

    @Override
    public PaymentTypeDTO findEnabledPaymentTypeByCode(String typeCode) {
        return mapper.findEnabledPaymentTypeByCode(typeCode);
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
    public ChannelProfileDTO findEnabledChannelProfileByCode(String channelCode) {
        return mapper.findEnabledChannelProfileByCode(channelCode);
    }

    @Override
    public List<SystemControlDTO> findEnabledSystemControls() {
        return safeList(mapper.findEnabledSystemControls());
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

    @Override
    public List<OpsConfigChangeDTO> findConfigChanges() {
        return safeList(mapper.findConfigChanges());
    }

    @Override
    public OpsConfigChangeDTO findConfigChangeByRequestNo(String requestNo) {
        return mapper.findConfigChangeByRequestNo(requestNo);
    }

    @Override
    public int insertConfigChange(String requestNo,
                                  String configType,
                                  String configCode,
                                  String targetStatus,
                                  String reason,
                                  String grayScope,
                                  String grayStartAt,
                                  String grayEndAt,
                                  String applicant) {
        return mapper.insertConfigChange(requestNo, configType, configCode, targetStatus, reason,
                grayScope, grayStartAt, grayEndAt, applicant);
    }

    @Override
    public int approveConfigChange(String requestNo,
                                   String status,
                                   String statusType,
                                   String approver,
                                   String approvalComment) {
        return mapper.approveConfigChange(requestNo, status, statusType, approver, approvalComment);
    }

    @Override
    public int publishConfigChange(String requestNo) {
        return mapper.publishConfigChange(requestNo);
    }

    @Override
    public int expireConfigChange(String requestNo) {
        return mapper.expireConfigChange(requestNo);
    }

    @Override
    public int insertSnapshotVersion(String grayScope, String sourceRequestNo) {
        return mapper.insertSnapshotVersion(grayScope, sourceRequestNo);
    }

    @Override
    public Long findSnapshotVersionByRequestNo(String sourceRequestNo) {
        return mapper.findSnapshotVersionByRequestNo(sourceRequestNo);
    }

    @Override
    public Long findLatestSnapshotVersion() {
        return mapper.findLatestSnapshotVersion();
    }

    @Override
    public int insertRefreshEvent(String eventNo, Long snapshotVersion, String grayScope, String sourceRequestNo) {
        return mapper.insertRefreshEvent(eventNo, snapshotVersion, grayScope, sourceRequestNo);
    }

    @Override
    public List<OpsConfigRefreshEventDTO> findRefreshEvents() {
        return safeList(mapper.findRefreshEvents());
    }

    @Override
    public int acknowledgeRefreshEvent(String eventNo, String acknowledgedBy) {
        return mapper.acknowledgeRefreshEvent(eventNo, acknowledgedBy);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : values.stream().filter(value -> value != null).collect(Collectors.toList());
    }
}
