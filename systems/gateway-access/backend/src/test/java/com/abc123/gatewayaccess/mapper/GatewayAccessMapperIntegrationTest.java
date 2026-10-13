package com.abc123.gatewayaccess.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.abc123.gatewayaccess.dto.GatewayAuditQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayAuditLogDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteQueryDTO;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用默认 H2 运行配置验证正式 Mapper SQL，而不是只验证 Mockito 调用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class GatewayAccessMapperIntegrationTest {

    @Autowired
    private GatewayAccessMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReadGovernanceRecordsWithH2CompatibleDateColumns() {
        assertFalse(mapper.findApplications().isEmpty());
        assertFalse(mapper.findGateways(new GatewayChannelQueryDTO()).isEmpty());
        assertFalse(mapper.findCertificates().isEmpty());
        assertFalse(mapper.findPermissions().isEmpty());
        assertFalse(mapper.findReleaseRoutes(new GatewayReleaseRouteQueryDTO()).isEmpty());
        assertFalse(mapper.findAuditLogs(new GatewayAuditQueryDTO()).isEmpty());

        assertNotNull(mapper.findApplicationByCode("APP_PAY_CORE"));
        assertNotNull(mapper.findPermissionByCode("PERM_PAYMENT_CORE"));
        assertNotNull(mapper.findGatewayByCode("GW_WX"));
        assertNotNull(mapper.findEnabledCertificateByGatewayCode("GW_WX"));
        assertNotNull(mapper.findEnabledReleaseRoute("GW_WX", "PROD"));
    }

    @Test
    void shouldPersistTransactionAuditWithDefaultH2Schema() {
        GatewayAuditLogEntity auditLog = new GatewayAuditLogEntity();
        auditLog.setRequestId("REQ-INTEGRATION-001");
        auditLog.setAppCode("APP_PAY_CORE");
        auditLog.setGatewayCode("GW_WX");
        auditLog.setOperationType("PAYMENT_SUBMIT");
        auditLog.setSignType("HMAC-SHA256");
        auditLog.setClientIp("127.0.0.1");
        auditLog.setResultStatus("SUCCESS");
        auditLog.setResultStatusType("success");
        auditLog.setRiskHint("INTEGRATION_TEST");

        assertEquals(1, mapper.insertAuditLog(auditLog));
    }

    @Test
    void shouldSearchAuditIdentifiersAsLiteralSubstrings() {
        String suffix = "_%!\\\"-" + UUID.randomUUID();
        GatewayAuditLogEntity auditLog = new GatewayAuditLogEntity();
        auditLog.setRequestId("REQ-" + suffix);
        auditLog.setAppCode("APP-" + suffix);
        auditLog.setGatewayCode("GW-" + suffix);
        auditLog.setOperationType("PAYMENT_SUBMIT");
        auditLog.setSignType("HMAC-SHA256");
        auditLog.setClientIp("127.0.0.1");
        auditLog.setResultStatus("SUCCESS");
        auditLog.setResultStatusType("success");
        auditLog.setRiskHint("ISOLATED_SEARCH_TEST");
        assertEquals(1, mapper.insertAuditLog(auditLog));

        for (String keyword : Arrays.asList("%", "REQ-_", "!", "\\", "\"", auditLog.getRequestId(),
                auditLog.getAppCode(), auditLog.getGatewayCode())) {
            GatewayAuditQueryDTO query = new GatewayAuditQueryDTO();
            query.setKeyword(keyword);
            assertEquals(Arrays.asList(auditLog.getRequestId()), mapper.findAuditLogs(query).stream()
                    .map(GatewayAuditLogDTO::getRequestId).collect(Collectors.toList()), keyword);
            assertEquals(keyword, query.getKeyword());
        }
    }

    @Test
    void shouldSearchGatewayCodeNameAndEndpointAsLiteralSubstrings() {
        String suffix = "_%!\\\"-" + UUID.randomUUID();
        String gatewayCode = "GW-" + suffix;
        String gatewayName = "网关" + suffix;
        String endpoint = "https://gateway.invalid/" + suffix;
        jdbcTemplate.update("INSERT INTO t_gateway_channel "
                        + "(gateway_code, gateway_name, channel_type, protocol_type, sign_algorithm, "
                        + "endpoint, status, status_type, updated_at) "
                        + "VALUES (?, ?, '支付渠道', 'HTTP+JSON', 'HMAC-SHA256', ?, 'DISABLED', "
                        + "'danger', CURRENT_TIMESTAMP)",
                gatewayCode, gatewayName, endpoint);
        jdbcTemplate.update("INSERT INTO t_gateway_channel "
                        + "(gateway_code, gateway_name, channel_type, protocol_type, sign_algorithm, "
                        + "endpoint, status, status_type, updated_at) "
                        + "VALUES (?, '普通对照渠道', '支付渠道', 'HTTP+JSON', 'HMAC-SHA256', "
                        + "'https://gateway.invalid/ordinary', 'DISABLED', 'danger', CURRENT_TIMESTAMP)",
                "GW-A-" + UUID.randomUUID());

        for (String keyword : Arrays.asList("%", "GW-_", "!", "\\", "\"", gatewayCode,
                gatewayName, endpoint)) {
            GatewayChannelQueryDTO query = new GatewayChannelQueryDTO();
            query.setKeyword(keyword);
            assertEquals(Arrays.asList(gatewayCode), mapper.findGateways(query).stream()
                    .map(GatewayChannelDTO::getGatewayCode).collect(Collectors.toList()), keyword);
            assertEquals(keyword, query.getKeyword());
        }
    }
}
