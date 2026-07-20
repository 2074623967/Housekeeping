package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关接入初始化数据。
 */
public final class GatewayAccessSeedData {

    private GatewayAccessSeedData() {
    }

    public static List<GatewayAppDTO> apps() {
        List<GatewayAppDTO> list = new ArrayList<>();
        list.add(app("APP_PAY_CORE", "支付核心应用", "payment-core", "支付平台主管", "10.0.0.0/24", "收款/退款", "ENABLED"));
        list.add(app("APP_SETTLEMENT", "结算平台应用", "settlement-system", "结算平台主管", "10.0.1.0/24", "出款/回单", "ENABLED"));
        list.add(app("APP_RISK", "风控平台应用", "risk-control-system", "风控平台主管", "10.0.2.0/24", "拦截/告警", "DISABLED"));
        return list;
    }

    public static List<GatewayChannelDTO> gateways() {
        List<GatewayChannelDTO> list = new ArrayList<>();
        list.add(gateway("GW_WX", "微信渠道接入", "支付渠道", "HTTP+JSON", "HMAC-SHA256", "https://gateway.local/wechat", "ENABLED"));
        list.add(gateway("GW_ALI", "支付宝渠道接入", "支付渠道", "HTTP+FORM", "RSA2", "https://gateway.local/alipay", "ENABLED"));
        list.add(gateway("GW_BANK", "银行银企直连", "银行直联", "SFTP+FILE", "RSA2048", "https://gateway.local/bank", "DISABLED"));
        return list;
    }

    public static List<GatewayCertificateDTO> certificates() {
        List<GatewayCertificateDTO> list = new ArrayList<>();
        list.add(certificate("CERT_WX_2026", "GW_WX", "v2026.07", "2026-09-30", "ENABLED"));
        list.add(certificate("CERT_ALI_2026", "GW_ALI", "v2026.07", "2026-08-20", "ENABLED"));
        list.add(certificate("CERT_BANK_2026", "GW_BANK", "v2026.06", "2026-07-25", "DISABLED"));
        return list;
    }

    public static List<GatewayPermissionDTO> permissions() {
        List<GatewayPermissionDTO> list = new ArrayList<>();
        list.add(permission("PERM_PAYMENT_CORE", "APP_PAY_CORE", "支付收款/退款", "ENABLED"));
        list.add(permission("PERM_SETTLEMENT_OUT", "APP_SETTLEMENT", "资金出款/回单", "ENABLED"));
        list.add(permission("PERM_RISK_SIGNAL", "APP_RISK", "风险事件/拦截", "DISABLED"));
        return list;
    }

    public static String now() {
        return "2026-07-20 22:40:00";
    }

    private static GatewayAppDTO app(String code, String name, String sourceSystem, String owner, String ipWhitelist, String scope, String status) {
        GatewayAppDTO dto = new GatewayAppDTO();
        dto.setAppCode(code);
        dto.setAppName(name);
        dto.setSourceSystem(sourceSystem);
        dto.setOwner(owner);
        dto.setIpWhitelist(ipWhitelist);
        dto.setPermissionScope(scope);
        dto.setStatus(status);
        dto.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        dto.setUpdatedAt(now());
        return dto;
    }

    private static GatewayChannelDTO gateway(String code, String name, String type, String protocol, String sign, String endpoint, String status) {
        GatewayChannelDTO dto = new GatewayChannelDTO();
        dto.setGatewayCode(code);
        dto.setGatewayName(name);
        dto.setChannelType(type);
        dto.setProtocolType(protocol);
        dto.setSignAlgorithm(sign);
        dto.setEndpoint(endpoint);
        dto.setStatus(status);
        dto.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        dto.setUpdatedAt(now());
        return dto;
    }

    private static GatewayCertificateDTO certificate(String code, String gatewayCode, String version, String expireAt, String status) {
        GatewayCertificateDTO dto = new GatewayCertificateDTO();
        dto.setCertificateCode(code);
        dto.setGatewayCode(gatewayCode);
        dto.setCertificateVersion(version);
        dto.setExpireAt(expireAt);
        dto.setStatus(status);
        dto.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        dto.setUpdatedAt(now());
        return dto;
    }

    private static GatewayPermissionDTO permission(String code, String appCode, String scope, String status) {
        GatewayPermissionDTO dto = new GatewayPermissionDTO();
        dto.setPermissionCode(code);
        dto.setAppCode(appCode);
        dto.setScope(scope);
        dto.setStatus(status);
        dto.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        dto.setUpdatedAt(now());
        return dto;
    }
}
