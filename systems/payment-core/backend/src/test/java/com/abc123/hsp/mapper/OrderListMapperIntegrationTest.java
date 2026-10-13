package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderDetailDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import java.io.InputStream;
import java.sql.PreparedStatement;
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
 * 订单列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class OrderListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:order_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OrderMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/OrderMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/OrderMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableIdTieBreakerWithoutChangingLatestFactJoins() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            OrderQueryDTO query = new OrderQueryDTO();
            query.setOrderNo("ORDER-STABLE");
            query.setPageSize(1);

            assertEquals("ORDER-STABLE-003", mapper.findAll(query).get(0).getOrderNo());
            query.setPageNo(2);
            assertEquals("ORDER-STABLE-002", mapper.findAll(query).get(0).getOrderNo());
            query.setPageNo(3);
            OrderListItemDTO oldestOrder = mapper.findAll(query).get(0);
            assertEquals("ORDER-STABLE-001", oldestOrder.getOrderNo());
            assertEquals("BILL-STABLE-NEW", oldestOrder.getBillNo());
            assertEquals("PAY-STABLE-NEW", oldestOrder.getLatestPaymentOrderId());
            assertEquals("支付成功", oldestOrder.getLatestPaymentStatus());
            assertEquals("PRE-STABLE-NEW", oldestOrder.getLatestPrepayOrderNo());
            assertEquals("支付完成", oldestOrder.getLatestCashierStatus());

            assertEquals(3L, mapper.count(query));
            assertEquals(Arrays.asList("ORDER-STABLE-003", "ORDER-STABLE-002", "ORDER-STABLE-001"),
                    orderNos(mapper.findForExport(query)));
        }
    }

    @Test
    void shouldSearchOrderListCountAndExportByLiteralSubstring() throws Exception {
        String orderNo = "ORDER-LITERAL-%_!\\";
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement update = session.getConnection().prepareStatement(
                     "UPDATE t_order SET order_no = ? WHERE order_no = 'ORDER-STABLE-003'")) {
            update.setString(1, orderNo);
            update.executeUpdate();
            OrderMapper mapper = session.getMapper(OrderMapper.class);
            for (String fragment : Arrays.asList("%", "_", "!", "\\", "%_!\\")) {
                OrderQueryDTO query = new OrderQueryDTO();
                query.setOrderNo(fragment);
                assertEquals(1L, mapper.count(query));
                assertEquals(Arrays.asList(orderNo), orderNos(mapper.findAll(query)));
                assertEquals(Arrays.asList(orderNo), orderNos(mapper.findForExport(query)));
                assertEquals(fragment, query.getOrderNo());
            }
        }
    }

    @Test
    void shouldReturnOrderDetailWithStableLatestFacts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            OrderDetailDTO detail = session.getMapper(OrderMapper.class).findDetail("ORDER-STABLE-001");

            assertEquals("ORDER-STABLE-001", detail.getOrderNo());
            assertEquals("¥100.00", detail.getOrderAmount());
            assertEquals("¥0.00", detail.getPaidAmount());
            assertEquals("¥100.00", detail.getOutstandingAmount());
            assertEquals("BILL-STABLE-NEW", detail.getBillNo());
            assertEquals("待支付", detail.getBillStatus());
            assertEquals("PAY-STABLE-NEW", detail.getLatestPaymentOrderId());
            assertEquals("微信支付", detail.getLatestPaymentMethod());
            assertEquals("WX_H5", detail.getLatestPaymentChannel());
            assertEquals("PRE-STABLE-NEW", detail.getLatestPrepayOrderNo());
            assertEquals("HOME_CLEAN", detail.getLatestPayScene());
            assertEquals("支付完成", detail.getLatestCashierStatus());
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
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64), customer_name VARCHAR(128), "
                    + "service_type VARCHAR(64), worker_name VARCHAR(128), order_amount DECIMAL(18,2), "
                    + "paid_amount DECIMAL(18,2), order_status VARCHAR(32), order_status_type VARCHAR(32), "
                    + "fulfillment_status VARCHAR(32), fulfillment_status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64), order_no VARCHAR(64), "
                    + "bill_amount DECIMAL(18,2), paid_amount DECIMAL(18,2), bill_status VARCHAR(32), "
                    + "bill_status_type VARCHAR(32), due_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2), payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "status VARCHAR(32), status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64), order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2), pay_scene VARCHAR(32), cashier_status VARCHAR(32), "
                    + "cashier_status_type VARCHAR(32), expires_at DATETIME, created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_order "
                    + "(order_no, customer_name, service_type, worker_name, order_amount, paid_amount, "
                    + "order_status, order_status_type, fulfillment_status, fulfillment_status_type, created_at) "
                    + "VALUES "
                    + "('ORDER-STABLE-001', '客户一', '深度保洁', '服务者一', 100.00, 0.00, "
                    + "'待支付', 'warning', '待履约', 'info', '2026-09-12 12:00:00'), "
                    + "('ORDER-STABLE-002', '客户二', '深度保洁', '服务者二', 100.00, 0.00, "
                    + "'待支付', 'warning', '待履约', 'info', '2026-09-12 12:00:00'), "
                    + "('ORDER-STABLE-003', '客户三', '深度保洁', '服务者三', 100.00, 0.00, "
                    + "'待支付', 'warning', '待履约', 'info', '2026-09-12 12:00:00')");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, bill_amount, paid_amount, bill_status, bill_status_type, due_at, created_at) VALUES "
                    + "('BILL-STABLE-OLD', 'ORDER-STABLE-001', 100.00, 0.00, '待支付', 'warning', "
                    + "'2026-09-13 12:30:00', '2026-09-12 12:30:00'), "
                    + "('BILL-STABLE-NEW', 'ORDER-STABLE-001', 100.00, 0.00, '待支付', 'warning', "
                    + "'2026-09-14 12:30:00', '2026-09-12 12:30:00')");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, order_no, amount, payment_method, channel_code, status, status_type, "
                    + "success_at, created_at) VALUES "
                    + "('PAY-STABLE-OLD', 'ORDER-STABLE-001', 100.00, '支付宝', 'ALIPAY_H5', "
                    + "'支付中', 'warning', NULL, '2026-09-12 12:30:00'), "
                    + "('PAY-STABLE-NEW', 'ORDER-STABLE-001', 100.00, '微信支付', 'WX_H5', "
                    + "'支付成功', 'success', '2026-09-12 12:40:00', '2026-09-12 12:30:00')");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, order_no, amount, pay_scene, cashier_status, cashier_status_type, "
                    + "expires_at, created_at) VALUES "
                    + "('PRE-STABLE-OLD', 'ORDER-STABLE-001', 100.00, 'HOME_CLEAN', "
                    + "'待支付', 'warning', '2026-09-12 13:30:00', '2026-09-12 12:30:00'), "
                    + "('PRE-STABLE-NEW', 'ORDER-STABLE-001', 100.00, 'HOME_CLEAN', "
                    + "'支付完成', 'success', '2026-09-12 13:30:00', '2026-09-12 12:30:00')");
            session.commit();
        }
    }

    private List<String> orderNos(List<OrderListItemDTO> orders) {
        return orders.stream().map(OrderListItemDTO::getOrderNo).collect(Collectors.toList());
    }
}
