package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            statement.execute("CREATE TABLE t_bill ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, bill_no VARCHAR(64) NOT NULL, "
                    + "order_no VARCHAR(64) NOT NULL, customer_name VARCHAR(128) NOT NULL, "
                    + "bill_amount DECIMAL(18, 2) NOT NULL, paid_amount DECIMAL(18, 2) NOT NULL, "
                    + "bill_status VARCHAR(32) NOT NULL, bill_status_type VARCHAR(32) NOT NULL, "
                    + "due_at DATETIME NOT NULL, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_bill "
                    + "(bill_no, order_no, customer_name, bill_amount, paid_amount, bill_status, "
                    + "bill_status_type, due_at, created_at) VALUES "
                    + "('BILL-STABLE-001', 'ORD-001', '客户一', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00'), "
                    + "('BILL-STABLE-002', 'ORD-002', '客户二', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00'), "
                    + "('BILL-STABLE-003', 'ORD-003', '客户三', 100.00, 20.00, '部分支付', 'warning', "
                    + "'2026-09-15 12:00:00', '2026-09-12 12:00:00')");
            session.commit();
        }
    }

    private List<String> billNos(List<BillListItemDTO> bills) {
        return bills.stream().map(BillListItemDTO::getBillNo).collect(Collectors.toList());
    }
}
