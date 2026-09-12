package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
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
 * 收银台会话列表与导出的 MySQL 兼容 SQL 集成测试。
 */
class CashierSessionListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:cashier_session_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CashierSessionMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/CashierSessionMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/CashierSessionMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableIdTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            CashierSessionMapper mapper = session.getMapper(CashierSessionMapper.class);
            CashierSessionQueryDTO query = new CashierSessionQueryDTO();
            query.setSessionNo("PRE-STABLE");
            query.setPageSize(1);

            assertEquals("PRE-STABLE-003", mapper.findAll(query).get(0).getSessionNo());
            query.setPageNo(2);
            assertEquals("PRE-STABLE-002", mapper.findAll(query).get(0).getSessionNo());
            query.setPageNo(3);
            assertEquals("PRE-STABLE-001", mapper.findAll(query).get(0).getSessionNo());

            query.setSortField("expiresAt");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList("PRE-STABLE-001", "PRE-STABLE-002", "PRE-STABLE-003"),
                    sessionNos(mapper.findAllForExport(query)));
            query.setSortField("amount");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList("PRE-STABLE-003", "PRE-STABLE-002", "PRE-STABLE-001"),
                    sessionNos(mapper.findAllForExport(query)));
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
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR(64), order_no VARCHAR(64) NOT NULL, bill_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, cashier_title VARCHAR(128) NOT NULL, "
                    + "pay_scene VARCHAR(32) NOT NULL, amount DECIMAL(18, 2) NOT NULL, "
                    + "cashier_status VARCHAR(32) NOT NULL, cashier_status_type VARCHAR(32) NOT NULL, "
                    + "created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, bill_no, customer_name, cashier_title, "
                    + "pay_scene, amount, cashier_status, cashier_status_type, created_at, expires_at) VALUES "
                    + "('PRE-STABLE-001', 'PAY-001', 'ORD-001', 'BILL-001', '客户一', '收银台一', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00'), "
                    + "('PRE-STABLE-002', 'PAY-002', 'ORD-002', 'BILL-002', '客户二', '收银台二', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00'), "
                    + "('PRE-STABLE-003', 'PAY-003', 'ORD-003', 'BILL-003', '客户三', '收银台三', "
                    + "'H5', 100.00, '待支付', 'warning', '2026-09-12 12:00:00', '2026-09-15 12:00:00')");
            session.commit();
        }
    }

    private List<String> sessionNos(List<CashierSessionListItemDTO> sessions) {
        return sessions.stream().map(CashierSessionListItemDTO::getSessionNo).collect(Collectors.toList());
    }
}
