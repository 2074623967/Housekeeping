package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentConfigService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付配置中心默认实现。
 */
@Service
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final PaymentConfigMapper paymentConfigMapper;

    public PaymentConfigServiceImpl(PaymentConfigMapper paymentConfigMapper) {
        this.paymentConfigMapper = paymentConfigMapper;
    }

    @Override
    public PaymentConfigOverviewDTO overview() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        overview.setChannels(paymentConfigMapper.findChannels());
        overview.setRouteRules(paymentConfigMapper.findRouteRules());
        overview.setProtocols(paymentConfigMapper.findProtocols());
        overview.setProtocolTypeOptions(paymentConfigMapper.findProtocolTypeOptions());
        overview.setReturnCodeMappings(paymentConfigMapper.findReturnCodeMappings());
        overview.setGateways(paymentConfigMapper.findGateways());
        overview.setControlPolicies(paymentConfigMapper.findControlPolicies());
        return overview;
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleChannel(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateChannelStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付渠道配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleRouteRule(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateRouteRuleStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付路由规则不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleProtocol(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateProtocolStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO createProtocol(PaymentProtocolUpsertRequestDTO request) {
        PaymentProtocolConfigEntity entity = buildProtocolEntity(request, null);
        if (paymentConfigMapper.findProtocolByCode(entity.getProtocolCode()) != null) {
            throw new IllegalArgumentException("支付协议编码已存在");
        }
        paymentConfigMapper.insertProtocol(entity);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO updateProtocol(String protocolCode, PaymentProtocolUpsertRequestDTO request) {
        String normalizedProtocolCode = requireText(protocolCode, "协议编码不能为空");
        if (paymentConfigMapper.findProtocolByCode(normalizedProtocolCode) == null) {
            throw new IllegalArgumentException("支付协议配置不存在");
        }
        PaymentProtocolConfigEntity entity = buildProtocolEntity(request, normalizedProtocolCode);
        paymentConfigMapper.updateProtocol(entity);
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleReturnCodeMapping(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        String subCode = requireSubCode(request);
        int affectedRows = paymentConfigMapper.updateReturnCodeMappingStatus(
                configCode,
                subCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("渠道返回码映射配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleGateway(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateGatewayStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付网关接入配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO toggleControlPolicy(PaymentConfigToggleRequestDTO request) {
        String configCode = requireConfigCode(request);
        int affectedRows = paymentConfigMapper.updateControlPolicyStatus(
                configCode,
                resolveStatus(request.getEnabled()),
                resolveStatusType(request.getEnabled())
        );
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        return overview();
    }

    @Override
    @Transactional
    public PaymentConfigOverviewDTO runControlPolicySelfCheck(PaymentConfigToggleRequestDTO request) {
        String sourceAppId = requireConfigCode(request);
        PaymentControlPolicyDTO controlPolicy = paymentConfigMapper.findControlPolicyBySourceAppId(sourceAppId);
        if (controlPolicy == null) {
            throw new IllegalArgumentException("支付控制策略配置不存在");
        }
        SelfCheckResult selfCheckResult = evaluateControlPolicy(controlPolicy);
        paymentConfigMapper.updateControlPolicySelfCheck(
                sourceAppId,
                selfCheckResult.status,
                selfCheckResult.statusType,
                selfCheckResult.message
        );
        return overview();
    }

    private SelfCheckResult evaluateControlPolicy(PaymentControlPolicyDTO controlPolicy) {
        Set<String> allowedChannels = splitToSet(controlPolicy.getAllowedChannelCodes());
        Set<String> allowedMethods = splitToSet(controlPolicy.getAllowedPaymentMethods());
        if (allowedMethods.isEmpty() || allowedChannels.isEmpty()) {
            return new SelfCheckResult("FAIL", "danger", "支付方式或渠道授权为空，禁止进入严格模式提交");
        }

        List<PaymentChannelConfigDTO> channels = paymentConfigMapper.findChannels();
        Set<String> enabledChannelCodes = new HashSet<String>();
        Set<String> enabledPaymentMethods = new HashSet<String>();
        for (PaymentChannelConfigDTO channel : channels) {
            if ("ENABLED".equals(channel.getStatus()) && allowedChannels.contains(channel.getChannelCode())) {
                enabledChannelCodes.add(channel.getChannelCode());
                enabledPaymentMethods.add(channel.getPaymentMethod());
            }
        }

        StringJoiner warningJoiner = new StringJoiner("；");
        if (!enabledChannelCodes.containsAll(allowedChannels)) {
            warningJoiner.add("存在未启用或不存在的授权渠道");
        }
        if (!enabledPaymentMethods.containsAll(allowedMethods)) {
            warningJoiner.add("存在未被启用渠道覆盖的支付方式");
        }
        if (!hasEnabledGatewayForAnyChannel(allowedChannels)) {
            warningJoiner.add("未找到覆盖授权渠道的启用网关");
        }

        if (warningJoiner.length() > 0) {
            return new SelfCheckResult("WARN", "warn", warningJoiner.toString());
        }
        return new SelfCheckResult("PASS", "success", "支付方式、渠道和网关接入均已通过自检");
    }

    private boolean hasEnabledGatewayForAnyChannel(Set<String> allowedChannels) {
        List<PaymentGatewayConfigDTO> gateways = paymentConfigMapper.findGateways();
        for (PaymentGatewayConfigDTO gateway : gateways) {
            if ("ENABLED".equals(gateway.getStatus())
                    && hasAnyConfiguredValue(gateway.getChannelScope(), allowedChannels)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyConfiguredValue(String configuredValues, Set<String> expectedValues) {
        for (String configuredValue : splitToSet(configuredValues)) {
            if (expectedValues.contains(configuredValue)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> splitToSet(String configuredValues) {
        Set<String> result = new HashSet<String>();
        if (!StringUtils.hasText(configuredValues)) {
            return result;
        }
        for (String configuredValue : Arrays.asList(configuredValues.split(","))) {
            if (StringUtils.hasText(configuredValue)) {
                result.add(configuredValue.trim());
            }
        }
        return result;
    }

    private String requireConfigCode(PaymentConfigToggleRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getConfigCode())) {
            throw new IllegalArgumentException("配置编码不能为空");
        }
        return request.getConfigCode().trim();
    }

    private String requireSubCode(PaymentConfigToggleRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getSubCode())) {
            throw new IllegalArgumentException("配置子编码不能为空");
        }
        return request.getSubCode().trim();
    }

    private PaymentProtocolConfigEntity buildProtocolEntity(PaymentProtocolUpsertRequestDTO request,
                                                            String overrideProtocolCode) {
        if (request == null) {
            throw new IllegalArgumentException("支付协议请求不能为空");
        }
        PaymentProtocolConfigEntity entity = new PaymentProtocolConfigEntity();
        entity.setProtocolCode(StringUtils.hasText(overrideProtocolCode)
                ? overrideProtocolCode.trim()
                : requireText(request.getProtocolCode(), "协议编码不能为空"));
        entity.setProtocolName(requireText(request.getProtocolName(), "协议名称不能为空"));
        entity.setProtocolType(requireText(request.getProtocolType(), "协议类型不能为空"));
        entity.setProtocolTypeName(resolveProtocolTypeName(request.getProtocolType(), request.getProtocolTypeName()));
        entity.setTemplateCode(requireText(request.getTemplateCode(), "协议模板编码不能为空"));
        entity.setTemplateName(requireText(request.getTemplateName(), "协议模板名称不能为空"));
        entity.setTemplateVersion(requireText(request.getTemplateVersion(), "模板版本不能为空"));
        entity.setSignMode(requireText(request.getSignMode(), "签约模式不能为空"));
        entity.setSignElementSpec(requireText(request.getSignElementSpec(), "签约要素配置不能为空"));
        entity.setESignatureProvider(requireText(request.getESignatureProvider(), "电子签章服务商不能为空"));
        entity.setSceneScope(requireText(request.getSceneScope(), "适用场景不能为空"));
        entity.setChannelScope(requireText(request.getChannelScope(), "适用渠道不能为空"));
        entity.setMerchantAckRequired(requireText(request.getMerchantAckRequired(), "商户确认要求不能为空"));
        entity.setRiskControlTag(requireText(request.getRiskControlTag(), "风控标签不能为空"));
        entity.setProtocolBody(requireText(request.getProtocolBody(), "协议正文不能为空"));
        entity.setPriority(resolvePriority(request.getPriority()));
        entity.setStatus(resolveStatus(request.getEnabled()));
        entity.setStatusType(resolveStatusType(request.getEnabled()));
        return entity;
    }

    private String requireText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private Integer resolvePriority(Integer priority) {
        if (priority == null) {
            return 99;
        }
        if (priority.intValue() < 0) {
            throw new IllegalArgumentException("协议优先级不能小于0");
        }
        return priority;
    }

    private String resolveProtocolTypeName(String protocolType, String requestProtocolTypeName) {
        String normalizedProtocolType = requireText(protocolType, "协议类型不能为空");
        List<PaymentProtocolTypeOptionDTO> protocolTypeOptions = paymentConfigMapper.findProtocolTypeOptions();
        for (PaymentProtocolTypeOptionDTO protocolTypeOption : protocolTypeOptions) {
            if (normalizedProtocolType.equals(protocolTypeOption.getProtocolType())) {
                return protocolTypeOption.getProtocolTypeName();
            }
        }
        throw new IllegalArgumentException("协议类型未在字典中定义");
    }

    private String resolveStatus(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private String resolveStatusType(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? "danger" : "success";
    }

    private static final class SelfCheckResult {
        private final String status;
        private final String statusType;
        private final String message;

        private SelfCheckResult(String status, String statusType, String message) {
            this.status = status;
            this.statusType = statusType;
            this.message = message;
        }
    }
}
