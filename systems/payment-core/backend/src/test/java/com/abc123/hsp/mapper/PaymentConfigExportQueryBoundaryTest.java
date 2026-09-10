package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 配置治理快照导出查询的容量边界测试。 */
class PaymentConfigExportQueryBoundaryTest {

    private static final String MAPPER_NAMESPACE = PaymentConfigMapper.class.getName() + ".";
    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(PaymentConfigMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentConfigMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentConfigMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void shouldLimitEveryGovernanceSnapshotExportQueryToSentinelRow() {
        for (String statementId : exportStatementIds()) {
            String sql = normalizedSql(statementId);
            assertTrue(sql.endsWith("LIMIT 1001"),
                    () -> statementId + " must end with LIMIT 1001, but was: " + sql);
        }
    }

    @Test
    void shouldKeepOrdinaryConfigurationQueriesUnbounded() {
        for (String statementId : ordinaryStatementIds()) {
            String sql = normalizedSql(statementId);
            assertFalse(sql.contains("LIMIT 1001"),
                    () -> statementId + " must remain unbounded for business reads, but was: " + sql);
        }
    }

    private String normalizedSql(String statementId) {
        return configuration.getMappedStatement(MAPPER_NAMESPACE + statementId)
                .getBoundSql(null)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private List<String> exportStatementIds() {
        return Arrays.asList(
                "findChannelsForExport",
                "findRouteRulesForExport",
                "findProtocolsForExport",
                "findReturnCodeMappingsForExport",
                "findGatewaysForExport",
                "findControlPoliciesForExport",
                "findAlertProvidersForExport",
                "findIssueDutyRostersForExport");
    }

    private List<String> ordinaryStatementIds() {
        return Arrays.asList(
                "findChannels",
                "findRouteRules",
                "findProtocols",
                "findReturnCodeMappings",
                "findGateways",
                "findControlPolicies",
                "findAlertProviders",
                "findIssueDutyRosters");
    }
}
