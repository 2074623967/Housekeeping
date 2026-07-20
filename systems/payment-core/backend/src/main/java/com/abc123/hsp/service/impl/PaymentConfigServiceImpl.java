package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentConfigService;
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
        overview.setReturnCodeMappings(paymentConfigMapper.findReturnCodeMappings());
        overview.setGateways(paymentConfigMapper.findGateways());
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

    private String resolveStatus(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private String resolveStatusType(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? "danger" : "success";
    }
}
