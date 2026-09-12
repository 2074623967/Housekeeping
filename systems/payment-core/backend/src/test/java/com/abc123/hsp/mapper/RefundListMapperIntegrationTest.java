package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import java.io.InputStream;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
 * 退款列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class RefundListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
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
    void shouldUseStableIdTieBreakerForListAndExport() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setPageSize(1);

            assertEquals("REF-003", mapper.findAll(query).get(0).getRefundOrderId());
            query.setPageNo(2);
            assertEquals("REF-002", mapper.findAll(query).get(0).getRefundOrderId());
            query.setPageNo(3);
            assertEquals("REF-001", mapper.findAll(query).get(0).getRefundOrderId());

            assertEquals(Arrays.asList("REF-003", "REF-002", "REF-001"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldApplyPendingStatusToListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at) VALUES "
                    + "('REF-PENDING-REVIEW', 'PAY-PENDING-1', 'ORD-PENDING-1', '客户甲', 1.00, "
                    + "'待收口测试', 'REVIEWING', 'warning', '2026-09-12 12:00:00'), "
                    + "('REF-PENDING-PROCESS', 'PAY-PENDING-2', 'ORD-PENDING-2', '客户乙', 2.00, "
                    + "'待收口测试', 'PROCESSING', 'warning', '2026-09-12 12:00:01'), "
                    + "('REF-PENDING-SUCCESS', 'PAY-PENDING-3', 'ORD-PENDING-3', '客户丙', 3.00, "
                    + "'待收口测试', 'SUCCESS', 'success', '2026-09-12 12:00:02'), "
                    + "('REF-PENDING-FAIL', 'PAY-PENDING-4', 'ORD-PENDING-4', '客户丁', 4.00, "
                    + "'待收口测试', 'FAIL', 'danger', '2026-09-12 12:00:03')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("PENDING");
            query.setRefundMethod("待收口测试");

            assertEquals(Arrays.asList("REF-PENDING-PROCESS", "REF-PENDING-REVIEW"),
                    refundOrderIds(mapper.findAll(query)));
            assertEquals(2L, mapper.count(query));
            assertEquals(Arrays.asList("REF-PENDING-PROCESS", "REF-PENDING-REVIEW"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldFilterSuccessfulRefundsWithoutSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at, success_at) VALUES "
                    + "('REF-SUCCESS-MISSING', 'PAY-MISSING', 'ORD-MISSING', '客户甲', 1.00, "
                    + "'完整性测试', 'SUCCESS', 'success', '2026-09-11 12:00:00', NULL), "
                    + "('REF-SUCCESS-PRESENT', 'PAY-PRESENT', 'ORD-PRESENT', '客户乙', 2.00, "
                    + "'完整性测试', 'SUCCESS', 'success', '2026-09-11 12:00:01', '2026-09-12 12:00:00')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("SUCCESS_TIME_MISSING");
            query.setRefundMethod("完整性测试");

            assertEquals(Arrays.asList("REF-SUCCESS-MISSING"), refundOrderIds(mapper.findAll(query)));
            assertEquals(1L, mapper.count(query));
            assertEquals(Arrays.asList("REF-SUCCESS-MISSING"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldFilterTerminalFactMismatchesForListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at, success_at) VALUES "
                    + "('REF-TERMINAL-OK', 'PAY-T1', 'ORD-T1', '客户甲', 1.00, "
                    + "'终态测试', 'SUCCESS', 'success', '2026-09-11 12:00:00', '2026-09-11 13:00:00'), "
                    + "('REF-TERMINAL-MISMATCH', 'PAY-T2', 'ORD-T2', '客户乙', 2.00, "
                    + "'终态测试', 'SUCCESS', 'success', '2026-09-11 12:01:00', '2026-09-11 13:01:00'), "
                    + "('REF-TERMINAL-NO-ATTEMPT', 'PAY-T3', 'ORD-T3', '客户丙', 3.00, "
                    + "'终态测试', 'FAIL', 'danger', '2026-09-11 12:02:00', NULL)");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, completed_at, created_at) VALUES "
                    + "('REF-TERMINAL-OK', 'SUCCESS', '2026-09-11 13:00:00', '2026-09-11 12:00:01'), "
                    + "('REF-TERMINAL-MISMATCH', 'ACCEPTED', NULL, '2026-09-11 12:01:01')");
            RefundMapper mapper = session.getMapper(RefundMapper.class);
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundStatus("TERMINAL_FACT_MISMATCH");
            query.setRefundMethod("终态测试");

            assertEquals(Arrays.asList("REF-TERMINAL-NO-ATTEMPT", "REF-TERMINAL-MISMATCH"),
                    refundOrderIds(mapper.findAll(query)));
            assertEquals(2L, mapper.count(query));
            assertEquals(Arrays.asList("REF-TERMINAL-NO-ATTEMPT", "REF-TERMINAL-MISMATCH"),
                    refundOrderIds(mapper.findAllForExport(query)));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, refund_amount DECIMAL(18, 2) NOT NULL, "
                    + "refund_method VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL, applied_at DATETIME NOT NULL, success_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "attempt_status VARCHAR(32) NOT NULL, completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, order_no, customer_name, refund_amount, "
                    + "refund_method, status, status_type, applied_at) VALUES "
                    + "('REF-001', 'PAY-001', 'ORD-001', '客户一', 1.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00'), "
                    + "('REF-002', 'PAY-002', 'ORD-002', '客户二', 2.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00'), "
                    + "('REF-003', 'PAY-003', 'ORD-003', '客户三', 3.00, '原路退回', 'AUDITING', 'warning', "
                    + "'2026-09-12 11:00:00')");
            session.commit();
        }
    }

    private List<String> refundOrderIds(List<RefundListItemDTO> refunds) {
        return refunds.stream().map(RefundListItemDTO::getRefundOrderId).collect(Collectors.toList());
    }
}
