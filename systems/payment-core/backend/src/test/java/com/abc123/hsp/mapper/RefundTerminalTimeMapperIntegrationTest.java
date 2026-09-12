package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * 退款终态时间随状态迁移维护的 MySQL 兼容 SQL 集成测试。
 */
class RefundTerminalTimeMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_terminal_time;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(RefundMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldSetAndClearTerminalTimeWithRefundState() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);

            assertEquals(1, mapper.updateRefundStatus(
                    "REF-TERMINAL-TIME", "PROCESSING", "FAIL", "danger", false));
            assertTerminalFacts(session, "FAIL", false, true);

            assertEquals(1, mapper.restartFailedRefund("REF-TERMINAL-TIME"));
            assertTerminalFacts(session, "PROCESSING", false, false);

            assertEquals(1, mapper.updateRefundStatus(
                    "REF-TERMINAL-TIME", "PROCESSING", "SUCCESS", "success", true));
            assertTerminalFacts(session, "SUCCESS", true, true);
        }
    }

    private void assertTerminalFacts(SqlSession session,
                                     String status,
                                     boolean successTimeExpected,
                                     boolean terminalTimeExpected) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT status, success_at, terminal_at FROM t_refund_order WHERE refund_order_id = ?")) {
            statement.setString(1, "REF-TERMINAL-TIME");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals(status, result.getString("status"));
                if (successTimeExpected) {
                    assertNotNull(result.getTimestamp("success_at"));
                } else {
                    assertNull(result.getTimestamp("success_at"));
                }
                if (terminalTimeExpected) {
                    assertNotNull(result.getTimestamp("terminal_at"));
                } else {
                    assertNull(result.getTimestamp("terminal_at"));
                }
            }
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32) NOT NULL, "
                    + "success_at DATETIME, terminal_at DATETIME, channel_code VARCHAR(64), "
                    + "channel_refund_no VARCHAR(128), channel_submitted_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, status, status_type) VALUES "
                    + "('REF-TERMINAL-TIME', 'PROCESSING', 'warn')");
            session.commit();
        }
    }
}
