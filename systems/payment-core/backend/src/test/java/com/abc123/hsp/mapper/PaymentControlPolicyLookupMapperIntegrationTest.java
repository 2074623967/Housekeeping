package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentControlPolicyDTO;
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

class PaymentControlPolicyLookupMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_control_policy_lookup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_control_policy");
            statement.execute("CREATE TABLE t_payment_control_policy ("
                    + "source_app_id VARCHAR_IGNORECASE(64) NOT NULL, source_app_name VARCHAR(128), "
                    + "allowed_payment_methods VARCHAR(255), allowed_channel_codes VARCHAR(255), "
                    + "allowed_merchant_nos VARCHAR(255), minute_submit_limit INT, "
                    + "interface_minute_submit_limit INT, token_auth_required VARCHAR(32), "
                    + "access_token_value VARCHAR(128), strict_mode VARCHAR(32), self_check_status VARCHAR(32), "
                    + "self_check_status_type VARCHAR(32), self_check_message VARCHAR(255), "
                    + "status VARCHAR(32), status_type VARCHAR(32))");
            statement.execute("INSERT INTO t_payment_control_policy (source_app_id, allowed_payment_methods, "
                    + "status) VALUES ('housekeeping-app-web', '支付宝', 'ENABLED')");
            session.commit();
        }
    }

    @Test
    void shouldNotReadAnotherSourceAppPolicyThroughCaseInsensitiveAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertNull(mapper.findActiveControlPolicyBySourceAppId("HOUSEKEEPING-APP-WEB"));
            assertNull(mapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web "));
            assertEquals("housekeeping-app-web",
                    mapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web").getSourceAppId());
        }
    }

    @Test
    void shouldReadOnlyExactSourceWhenCaseVariantsCoexist() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO t_payment_control_policy (source_app_id, allowed_payment_methods, "
                    + "status) VALUES ('HOUSEKEEPING-APP-WEB', '微信支付', 'ENABLED')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentControlPolicyDTO upper = mapper.findActiveControlPolicyBySourceAppId("HOUSEKEEPING-APP-WEB");
            PaymentControlPolicyDTO lower = mapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web");
            assertEquals("微信支付", upper.getAllowedPaymentMethods());
            assertEquals("支付宝", lower.getAllowedPaymentMethods());
        }
    }
}
