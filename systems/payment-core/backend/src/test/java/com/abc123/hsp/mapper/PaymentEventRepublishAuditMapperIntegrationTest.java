package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.abc123.hsp.dto.PaymentEventListItemDTO;
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
 * 支付事件成功重发审计的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentEventRepublishAuditMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_event_republish_audit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentEventMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentEventMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentEventMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldPersistAndReadLastSuccessfulRepublishOperator() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertEquals(1, session.getMapper(PaymentEventMapper.class)
                    .updateLastRepublishAudit("EVT-AUDIT-001", "finance-ops"));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentEventListItemDTO event = session.getMapper(PaymentEventMapper.class)
                    .findByEventNo("EVT-AUDIT-001");

            assertEquals("finance-ops", event.getLastRepublishOperator());
            assertNotNull(event.getLastRepublishedAt());
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64) NOT NULL, "
                    + "event_type VARCHAR(64) NOT NULL, payment_order_id VARCHAR(64) NOT NULL, "
                    + "biz_no VARCHAR(64) NOT NULL, downstream_system VARCHAR(256) NOT NULL, "
                    + "event_topic VARCHAR(128) NOT NULL, publish_status VARCHAR(32) NOT NULL, "
                    + "publish_status_type VARCHAR(32) NOT NULL, retry_count INT NOT NULL, "
                    + "last_published_at DATETIME, next_retry_at DATETIME, "
                    + "last_republish_operator VARCHAR(64), last_republished_at DATETIME, "
                    + "event_payload VARCHAR(2048) NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                    + "downstream_system, event_topic, publish_status, publish_status_type, retry_count, "
                    + "event_payload, created_at) VALUES ('EVT-AUDIT-001', 'PAYMENT_SUCCESS', 'PAY-001', "
                    + "'ORD-001', 'accounting-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 1, "
                    + "'{}', NOW())");
            session.commit();
        }
    }
}
