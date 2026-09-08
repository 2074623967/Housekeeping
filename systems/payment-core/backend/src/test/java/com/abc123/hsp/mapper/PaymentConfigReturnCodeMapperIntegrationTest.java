package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import java.io.InputStream;
import java.sql.Statement;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 渠道返回码映射查询的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentConfigReturnCodeMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_return_code;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentConfigMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentConfigMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentConfigMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldOnlyResolveEnabledAndActiveMapping() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentConfigMapper mapper = session.getMapper(PaymentConfigMapper.class);

            PaymentChannelReturnCodeConfigDTO active =
                    mapper.findActiveReturnCodeMapping("wx_h5", "USERPAYING");

            assertEquals("PAYMENT-CHANNEL-1001", active.getStandardizedCode());
            assertEquals("v2026.07.1", active.getMappingVersion());
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "DISABLED_CODE"));
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "ARCHIVED_CODE"));
            assertNull(mapper.findActiveReturnCodeMapping("wx_h5", "UNKNOWN_CODE"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_return_code_map");
            statement.execute("CREATE TABLE t_payment_channel_return_code_map ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64), "
                    + "channel_return_code VARCHAR(64), standardized_code VARCHAR(64), "
                    + "standardized_message VARCHAR(256), handling_suggestion VARCHAR(256), "
                    + "retryable VARCHAR(16), manual_intervention_required VARCHAR(16), "
                    + "mapping_version VARCHAR(32), archive_status VARCHAR(32), archive_status_type VARCHAR(32), "
                    + "status VARCHAR(32), status_type VARCHAR(32), priority INT, updated_at DATETIME)");
            insert(statement, "USERPAYING", "ACTIVE", "ENABLED");
            insert(statement, "DISABLED_CODE", "ACTIVE", "DISABLED");
            insert(statement, "ARCHIVED_CODE", "ARCHIVED", "ENABLED");
            session.commit();
        }
    }

    private void insert(Statement statement, String returnCode, String archiveStatus, String status)
            throws Exception {
        statement.executeUpdate("INSERT INTO t_payment_channel_return_code_map "
                + "(channel_code, channel_return_code, standardized_code, standardized_message, "
                + "handling_suggestion, retryable, manual_intervention_required, mapping_version, "
                + "archive_status, archive_status_type, status, status_type, priority, updated_at) VALUES "
                + "('wx_h5', '" + returnCode + "', 'PAYMENT-CHANNEL-1001', '用户支付中', "
                + "'等待回调或主动查单', '是', '否', 'v2026.07.1', '" + archiveStatus
                + "', 'success', '" + status + "', 'success', 1, NOW())");
    }
}
