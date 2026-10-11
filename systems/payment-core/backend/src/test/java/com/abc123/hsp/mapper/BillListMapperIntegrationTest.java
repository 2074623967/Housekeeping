package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.BillDetailDTO;
import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillQueryDTO;
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
 * 账单列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class BillListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:bill_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(BillMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/BillMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/BillMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableIdTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BillMapper mapper = session.getMapper(BillMapper.class);
            BillQueryDTO query = new BillQueryDTO();
            query.setBillNo("BILL-STABLE");
            query.setPageSize(1);

            assertEquals("BILL-STABLE-003", mapper.findAll(query).get(0).getBillNo());
            query.setPageNo(2);
            assertEquals("BILL-STABLE-002", mapper.findAll(query).get(0).getBillNo());
            query.setPageNo(3);
            assertEquals("BILL-STABLE-001", mapper.findAll(query).get(0).getBillNo());

            query.setSortField("dueAt");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("BILL-STABLE-001", "BILL-STABLE-002", "BILL-STABLE-003"),
                    billNos(mapper.findAllForExport(query)));
            query.setSortField("billAmount");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("BILL-STABLE-003", "BILL-STABLE-002", "BILL-STABLE-001"),
                    billNos(mapper.findAllForExport(query)));
            query.setSortField("unpaidAmount");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("BILL-STABLE-001", "BILL-STABLE-002", "BILL-STABLE-003"),
                    billNos(mapper.findAllForExport(query)));
        }
    }

    @Test
    void shouldReturnBillDetailWithStableExactLinkedFacts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BillDetailDTO detail = session.getMapper(BillMapper.class).findDetail("BILL-STABLE-001");

            assertEquals("BILL-STABLE-001", detail.getBillNo());
            assertEquals("ORD-001", detail.getStandardOrderNo());
            assertEquals("¥100.00", detail.getBillAmount());
            assertEquals("¥20.00", detail.getPaidAmount());
            assertEquals("¥80.00", detail.getUnpaidAmount());
            assertEquals("PRE-BILL-NEW", detail.getLatestPrepayOrderNo());
            assertEquals("支付中", detail.getLatestCashierStatus());
            assertEquals("PAY-BILL-NEW", detail.getLatestPaymentOrderId());
            assertEquals("PRE-BILL-NEW", detail.getLatestPaymentPrepayOrderNo());
            assertEquals("支付宝", detail.getLatestPaymentMethod());
            assertEquals("ALIPAY_H5", detail.getLatestPaymentChannel());
        }
    }

    @Test
    void shouldNotReadBillDetailThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BillMapper mapper = session.getMapper(BillMapper.class);
            assertNull(mapper.findDetail("bill-stable-001"));
            assertNull(mapper.findDetail("BILL-STABLE-001 "));
            assertEquals("BILL-STABLE-001", mapper.findDetail("BILL-STABLE-001").getBillNo());
        }
    }

    @Test
    void shouldNotLinkPrepayThroughBillNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET bill_no = 'bill-stable-001' "
                    + "WHERE prepay_order_no = 'PRE-BILL-NEW'");
            BillMapper mapper = session.getMapper(BillMapper.class);
            BillDetailDTO detail = mapper.findDetail("BILL-STABLE-001");
            assertEquals("PRE-BILL-OLD", detail.getLatestPrepayOrderNo());
            assertEquals("PAY-BILL-OLD", detail.getLatestPaymentOrderId());
            BillQueryDTO query = new BillQueryDTO();
            query.setBillNo("BILL-STABLE-001");
            assertEquals("APP", mapper.findAll(query).get(0).getLatestPayScene());
            assertEquals("APP", mapper.findAllForExport(query).get(0).getLatestPayScene());
        }
    }

    @Test
    void shouldNotLinkPaymentThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET payment_order_id = 'pay-bill-new' "
                    + "WHERE payment_order_id = 'PAY-BILL-NEW'");
            BillDetailDTO detail = session.getMapper(BillMapper.class).findDetail("BILL-STABLE-001");
            assertEquals("PRE-BILL-NEW", detail.getLatestPrepayOrderNo());
            assertEquals("PAY-BILL-OLD", detail.getLatestPaymentOrderId());
            assertEquals("PRE-BILL-OLD", detail.getLatestPaymentPrepayOrderNo());
        }
    }

    @Test
    void shouldNotLinkPaymentToAnotherPrepayThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, payment_order_id, created_at) VALUES "
                    + "('PRE-BILL-ALIAS', 'BILL-STABLE-001', 'ORD-001', 'pay-bill-new', "
                    + "'2026-09-12 18:00:00')");
            BillDetailDTO detail = session.getMapper(BillMapper.class).findDetail("BILL-STABLE-001");
            assertEquals("PAY-BILL-NEW", detail.getLatestPaymentOrderId());
            assertEquals("PRE-BILL-NEW", detail.getLatestPaymentPrepayOrderNo());
        }
    }

    @Test
    void shouldNotLinkPaymentToPrepayFromBillNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, payment_order_id, created_at) VALUES "
                    + "('PRE-BILL-OTHER', 'bill-stable-001', 'ORD-001', 'PAY-BILL-NEW', "
                    + "'2026-09-12 18:00:00')");
            BillDetailDTO detail = session.getMapper(BillMapper.class).findDetail("BILL-STABLE-001");
            assertEquals("PAY-BILL-NEW", detail.getLatestPaymentOrderId());
            assertEquals("PRE-BILL-NEW", detail.getLatestPaymentPrepayOrderNo());
        }
    }

    @Test
    void shouldNotLinkVirtualBillToCollidingStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-002')");
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE bill_no = 'BILL-STABLE-002'");
            BillMapper mapper = session.getMapper(BillMapper.class);
            assertEquals("ORD-002", mapper.findDetail("BILL-STABLE-002").getOrderNo());
            assertNull(mapper.findDetail("BILL-STABLE-002").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'standard_order' "
                    + "WHERE bill_no = 'BILL-STABLE-002'");
            session.clearCache();
            assertNull(mapper.findDetail("BILL-STABLE-002").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER' "
                    + "WHERE bill_no = 'BILL-STABLE-002'");
            session.clearCache();
            assertEquals("ORD-002", mapper.findDetail("BILL-STABLE-002").getStandardOrderNo());
        }
    }

    @Test
    void shouldNotLinkBillWithMixedPrepaySourcesToStandardOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            BillMapper mapper = session.getMapper(BillMapper.class);
            assertEquals("ORD-001", mapper.findDetail("BILL-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'VIRTUAL' "
                    + "WHERE prepay_order_no = 'PRE-BILL-OLD'");
            session.clearCache();
            assertNull(mapper.findDetail("BILL-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind = 'STANDARD_ORDER', "
                    + "order_no = 'OTHER-ORDER' WHERE prepay_order_no = 'PRE-BILL-OLD'");
            session.clearCache();
            assertNull(mapper.findDetail("BILL-STABLE-001").getStandardOrderNo());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-001' "
                    + "WHERE prepay_order_no = 'PRE-BILL-OLD'");
            session.clearCache();
            assertEquals("ORD-001", mapper.findDetail("BILL-STABLE-001").getStandardOrderNo());
        }
    }

    @Test
    void shouldProjectLatestPaySceneInBillListAndExport() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            BillMapper mapper = session.getMapper(BillMapper.class);
            BillQueryDTO query = new BillQueryDTO();
            query.setBillNo("BILL-STABLE-001");

            assertEquals("H5", mapper.findAll(query).get(0).getLatestPayScene());
            assertEquals("H5", mapper.findAllForExport(query).get(0).getLatestPayScene());
        }
    }

    @Test
    void shouldReturnZeroBillCountersWhenBillTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_bill");
            com.abc123.hsp.dto.BillOverviewDTO overview = session.getMapper(BillMapper.class)
                    .findOverview(new BillQueryDTO());

            assertEquals(0L, overview.getTotalBillCount());
            assertEquals(0L, overview.getPaidBillCount());
            assertEquals(0L, overview.getUnpaidBillCount());
            assertEquals(0L, overview.getPartialPaidBillCount());
            assertEquals(0L, overview.getOverdueBillCount());
            assertEquals("¥0.00", overview.getTotalBillAmount());
            assertEquals("¥0.00", overview.getTotalPaidAmount());
            assertEquals("¥0.00", overview.getTotalUnpaidAmount());
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
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "bill_amount DECIMAL(18, 2) NOT NULL, paid_amount DECIMAL(18, 2) NOT NULL, "
                    + "bill_status VARCHAR(32) NOT NULL, bill_status_type VARCHAR(32) NOT NULL, "
                    + "due_at DATETIME NOT NULL, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64) NOT NULL, "
                    + "bill_no VARCHAR_IGNORECASE(64) NOT NULL, order_no VARCHAR(64) NOT NULL, amount DECIMAL(18,2), "
                    + "pay_scene VARCHAR(32), cashier_status VARCHAR(32), cashier_status_type VARCHAR(32), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), source_kind VARCHAR(16) DEFAULT 'STANDARD_ORDER', "
                    + "expires_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "amount DECIMAL(18,2), payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "status VARCHAR(32), status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_order (order_no) VALUES ('ORD-001')");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, customer_name, bill_amount, paid_amount, bill_status, "
                    + "bill_status_type, due_at, created_at) VALUES "
                    + "('BILL-STABLE-001', 'ORD-001', '客户一', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00'), "
                    + "('BILL-STABLE-002', 'ORD-002', '客户二', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00'), "
                    + "('BILL-STABLE-003', 'ORD-003', '客户三', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00')");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, amount, pay_scene, cashier_status, "
                    + "cashier_status_type, payment_order_id, expires_at, created_at) VALUES "
                    + "('PRE-BILL-OLD', 'BILL-STABLE-001', 'ORD-001', 80.00, 'APP', '已失效', "
                    + "'info', 'PAY-BILL-OLD', '2026-09-13 13:00:00', '2026-09-12 13:00:00'), "
                    + "('PRE-BILL-NEW', 'BILL-STABLE-001', 'ORD-001', 80.00, 'H5', '支付中', "
                    + "'warning', 'PAY-BILL-NEW', '2026-09-13 14:00:00', '2026-09-12 13:00:00'), "
                    + "('PRE-OTHER', 'BILL-STABLE-002', 'ORD-002', 80.00, 'PC', '支付完成', "
                    + "'success', 'PAY-OTHER', '2026-09-13 15:00:00', '2026-09-12 15:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, amount, payment_method, channel_code, status, status_type, "
                    + "success_at, created_at) VALUES "
                    + "('PAY-BILL-OLD', 80.00, '微信支付', 'WX_H5', 'CLOSED', 'info', NULL, "
                    + "'2026-09-12 14:00:00'), "
                    + "('PAY-BILL-NEW', 80.00, '支付宝', 'ALIPAY_H5', 'WAIT_CALLBACK', 'warning', NULL, "
                    + "'2026-09-12 14:00:00'), "
                    + "('PAY-OTHER', 80.00, '银行卡', 'BANK_CARD', 'SUCCESS', 'success', "
                    + "'2026-09-12 16:00:00', '2026-09-12 15:00:00')");
            session.commit();
        }
    }

    private List<String> billNos(List<BillListItemDTO> bills) {
        return bills.stream().map(BillListItemDTO::getBillNo).collect(Collectors.toList());
    }
}
