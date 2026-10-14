package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.math.BigDecimal;
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

class PaymentOrderSettlementMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_order_settlement;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR_IGNORECASE(64) PRIMARY KEY, "
                    + "order_amount DECIMAL(18,2), paid_amount DECIMAL(18,2), "
                    + "customer_name VARCHAR(64), worker_name VARCHAR(64), "
                    + "order_status VARCHAR(32), order_status_type VARCHAR(32))");
            statement.execute("CREATE TABLE t_bill (bill_no VARCHAR_IGNORECASE(64) PRIMARY KEY, "
                    + "order_no VARCHAR_IGNORECASE(64), "
                    + "customer_name VARCHAR(64), bill_amount DECIMAL(18,2), paid_amount DECIMAL(18,2), "
                    + "bill_status VARCHAR(32), bill_status_type VARCHAR(32))");
            statement.executeUpdate("INSERT INTO t_order VALUES "
                    + "('ORD-001', 8800.00, 2000.00, '张女士', '李阿姨', '待支付', 'warn')");
            statement.executeUpdate("INSERT INTO t_bill VALUES "
                    + "('BILL-001', 'ORD-001', '张女士', 8800.00, 2000.00, '部分支付', 'warn')");
            session.commit();
        }
    }

    @Test
    void shouldNotOverwriteChangesMadeAfterSettlementSnapshotWasRead() throws Exception {
        update("UPDATE t_order SET paid_amount=2050.00 WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        assertEquals(new BigDecimal("2050.00"), paidAmount());

        update("UPDATE t_order SET paid_amount=2000.00, order_amount=8900.00 "
                + "WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        assertEquals(new BigDecimal("2000.00"), paidAmount());

        update("UPDATE t_order SET order_amount=8800.00, customer_name='李女士' "
                + "WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        assertEquals(new BigDecimal("2000.00"), paidAmount());

        update("UPDATE t_order SET customer_name='张女士' WHERE order_no='ORD-001'");
        update("UPDATE t_order SET order_status='已取消', order_status_type='danger' WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        update("UPDATE t_order SET order_status='待支付' WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        update("UPDATE t_order SET order_status_type='warn' WHERE order_no='ORD-001'");
        assertEquals(1, settle());
        assertEquals(new BigDecimal("8800.00"), paidAmount());
    }

    @Test
    void shouldNotReadOriginalOrderAmountsThroughCaseVariantNumber() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(new BigDecimal("8800.00"), mapper.findOrderAmount("ORD-001"));
            assertEquals(new BigDecimal("2000.00"), mapper.findPaidAmount("ORD-001"));
            assertNull(mapper.findOrderAmount("ord-001"));
            assertNull(mapper.findPaidAmount("ord-001"));
        }
    }

    @Test
    void shouldNotReadOrderNamesThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals("张女士", mapper.findCustomerNameByOrderNo("ORD-001"));
            assertEquals("李阿姨", mapper.findWorkerNameByOrderNo("ORD-001"));
            assertNull(mapper.findCustomerNameByOrderNo("ord-001"));
            assertNull(mapper.findWorkerNameByOrderNo("ord-001"));
            assertNull(mapper.findCustomerNameByOrderNo("ORD-001 "));
            assertNull(mapper.findWorkerNameByOrderNo("ORD-001 "));
        }
    }

    @Test
    void shouldNotSettleBillThroughNumberAlias() throws Exception {
        update("UPDATE t_bill SET bill_no='bill-001' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());
        assertEquals(new BigDecimal("2000.00"), billPaidAmount());
        update("UPDATE t_bill SET bill_no='BILL-001' WHERE bill_no='bill-001'");
        assertEquals(1, settleBill());
        assertEquals(new BigDecimal("8800.00"), billPaidAmount());
    }

    @Test
    void shouldRejectCaseVariantOrderNumbersDuringSettlementWrites() throws Exception {
        update("UPDATE t_order SET order_no='ord-001' WHERE order_no='ORD-001'");
        assertEquals(0, settle());
        update("UPDATE t_order SET order_no='ORD-001' WHERE order_no='ord-001'");
        update("UPDATE t_bill SET order_no='ord-001' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());
        update("UPDATE t_bill SET order_no='ORD-001' WHERE bill_no='BILL-001'");
        assertEquals(1, settle());
        assertEquals(1, settleBill());
    }

    @Test
    void shouldNotOverwriteBillWhenItsOwnerOrAmountsHaveChanged() throws Exception {
        update("UPDATE t_bill SET paid_amount=2050.00 WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());
        assertEquals(new BigDecimal("2050.00"), billPaidAmount());

        update("UPDATE t_bill SET paid_amount=2000.00, bill_amount=8900.00 WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());

        update("UPDATE t_bill SET bill_amount=8800.00, customer_name='李女士' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());

        update("UPDATE t_bill SET customer_name='张女士', order_no='ORD-OTHER' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());

        update("UPDATE t_bill SET order_no='ORD-001' WHERE bill_no='BILL-001'");
        update("UPDATE t_bill SET bill_status='待支付' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());
        update("UPDATE t_bill SET bill_status='部分支付', bill_status_type='success' WHERE bill_no='BILL-001'");
        assertEquals(0, settleBill());
        update("UPDATE t_bill SET bill_status_type='warn' WHERE bill_no='BILL-001'");
        assertEquals(1, settleBill());
        assertEquals(new BigDecimal("8800.00"), billPaidAmount());
    }

    private int settle() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int updated = session.getMapper(PaymentMapper.class).updateOrderAfterPayment(
                    "ORD-001", new BigDecimal("8800.00"), "待履约", "info",
                    new BigDecimal("2000.00"), "张女士");
            session.commit();
            return updated;
        }
    }

    private BigDecimal paidAmount() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.getMapper(PaymentMapper.class).findPaidAmount("ORD-001");
        }
    }

    private int settleBill() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            int updated = session.getMapper(PaymentMapper.class).updateBillAfterPayment(
                    "BILL-001", new BigDecimal("8800.00"), "已结清", "success",
                    "ORD-001", "张女士", new BigDecimal("2000.00"));
            session.commit();
            return updated;
        }
    }

    private BigDecimal billPaidAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT paid_amount FROM t_bill WHERE bill_no='BILL-001'")) {
            result.next();
            return result.getBigDecimal(1);
        }
    }

    private void update(String sql) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate(sql);
            session.commit();
        }
    }
}
