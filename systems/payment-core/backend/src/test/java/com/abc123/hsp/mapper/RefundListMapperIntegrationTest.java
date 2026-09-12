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
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR(64) NOT NULL, order_no VARCHAR(64) NOT NULL, "
                    + "customer_name VARCHAR(128) NOT NULL, refund_amount DECIMAL(18, 2) NOT NULL, "
                    + "refund_method VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) NOT NULL, applied_at DATETIME NOT NULL, success_at DATETIME)");
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
