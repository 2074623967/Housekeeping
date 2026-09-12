package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * 支付异常推荐路由的正式 Mapper SQL 契约测试。
 */
class PaymentIssueMapperRouteContractTest {

    @Test
    void shouldTargetDisabledChannelFilterFromIssueListAndDetail() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(PaymentIssueMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentIssueMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentIssueMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        Map<String, Object> listParameters = new HashMap<>();
        listParameters.put("query", new PaymentIssueQueryDTO());
        Map<String, Object> detailParameters = new HashMap<>();
        detailParameters.put("issueNo", "ISSUE-CHANNEL-PAY-001");

        assertDisabledChannelRoute(configuration
                .getMappedStatement(PaymentIssueMapper.class.getName() + ".findAll")
                .getBoundSql(listParameters)
                .getSql());
        assertDisabledChannelRoute(configuration
                .getMappedStatement(PaymentIssueMapper.class.getName() + ".findByIssueNo")
                .getBoundSql(detailParameters)
                .getSql());
    }

    private void assertDisabledChannelRoute(String sql) {
        assertTrue(sql.contains("'/payment-config?channelStatus=DISABLED' AS recommended_route"));
        assertFalse(sql.contains("'/payment-config' AS recommended_route"));
    }
}
