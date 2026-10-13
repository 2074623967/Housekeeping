package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementOverviewDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import java.io.InputStream;
import java.sql.Date;
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
 * 服务者结算列表与导出的 MySQL 兼容 SQL 集成测试。
 */
public class WorkerSettlementListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:worker_settlement_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SettlementMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/SettlementMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/SettlementMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseIdAsStableTieBreakerForListAndExport() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            SettlementMapper mapper = session.getMapper(SettlementMapper.class);
            WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
            query.setWorkerKeyword("稳定排序服务者");
            query.setPageSize(1);

            WorkerSettlementListItemDTO first = mapper.findWorkerSettlements(query).get(0);
            assertEquals("SETTLE-STABLE-003", first.getSettlementOrderId());
            assertEquals("2026-09-01 ~ 2026-09-07", first.getPeriod());
            assertEquals("¥303.00", first.getAmountShouldSettle());
            assertEquals("¥3.00", first.getDeductAmount());
            assertEquals("¥300.00", first.getAmountNetSettle());
            assertEquals("¥0.00", first.getDepositImpactAmount());

            query.setPageNo(2);
            assertEquals("SETTLE-STABLE-002", mapper.findWorkerSettlements(query).get(0).getSettlementOrderId());
            query.setPageNo(3);
            assertEquals("SETTLE-STABLE-001", mapper.findWorkerSettlements(query).get(0).getSettlementOrderId());
            assertEquals(3L, mapper.count(query));

            assertEquals(Arrays.asList(
                            "SETTLE-STABLE-003",
                            "SETTLE-STABLE-002",
                            "SETTLE-STABLE-001"),
                    settlementOrderIds(mapper.findWorkerSettlementsForExport(query)));
        }
    }

    @Test
    void shouldReturnZeroOverviewCountsWhenSettlementTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_worker_settlement_order");
            SettlementMapper mapper = session.getMapper(SettlementMapper.class);

            WorkerSettlementOverviewDTO overview = mapper.findWorkerOverview(new WorkerSettlementQueryDTO());

            assertEquals(0L, overview.getTotalSettlementCount());
            assertEquals(0L, overview.getPendingAuditCount());
            assertEquals(0L, overview.getPayoutPendingCount());
            assertEquals(0L, overview.getPayingCount());
            assertEquals(0L, overview.getPayoutSuccessCount());
            assertEquals("¥0.00", overview.getTotalNetSettleAmount());
            assertEquals("¥0.00", overview.getTotalDeductAmount());
            assertEquals("¥0.00", overview.getTotalDepositImpactAmount());
            assertEquals(0, overview.getNegativeNetSettleCount());
        }
    }

    @Test
    void shouldSearchSettlementNumberAndWorkerNameLiterallyAcrossListOverviewAndExport() throws Exception {
        String settlementOrderId = "SETTLE-%_!\\\"-SPECIAL";
        String workerName = "服务者%_!\\\"特例";
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement insert = session.getConnection().prepareStatement(
                     "INSERT INTO t_worker_settlement_order "
                             + "(settlement_order_id, worker_name, period_start, period_end, amount_should_settle, "
                             + "deduct_amount, amount_net_settle, deposit_impact_amount, status, status_type, "
                             + "payout_status, payout_status_type, created_at) VALUES "
                             + "(?, ?, '2026-09-01', '2026-09-07', 101.00, 1.00, 100.00, 0.00, "
                             + "'待审核', 'warning', '待出款', 'warning', '2026-09-12 15:00:00')")) {
            insert.setString(1, settlementOrderId);
            insert.setString(2, workerName);
            insert.executeUpdate();
            SettlementMapper mapper = session.getMapper(SettlementMapper.class);

            for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", settlementOrderId)) {
                WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
                query.setSettlementOrderId(fragment);
                assertEquals(Arrays.asList(settlementOrderId),
                        settlementOrderIds(mapper.findWorkerSettlements(query)), fragment);
                assertEquals(1L, mapper.count(query), fragment);
                assertEquals(1L, mapper.findWorkerOverview(query).getTotalSettlementCount(), fragment);
                assertEquals(Arrays.asList(settlementOrderId),
                        settlementOrderIds(mapper.findWorkerSettlementsForExport(query)), fragment);
                assertEquals(fragment, query.getSettlementOrderId());
            }

            for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", workerName)) {
                WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
                query.setWorkerKeyword(fragment);
                assertEquals(Arrays.asList(settlementOrderId),
                        settlementOrderIds(mapper.findWorkerSettlements(query)), fragment);
                assertEquals(1L, mapper.count(query), fragment);
                assertEquals(1L, mapper.findWorkerOverview(query).getTotalSettlementCount(), fragment);
                assertEquals(Arrays.asList(settlementOrderId),
                        settlementOrderIds(mapper.findWorkerSettlementsForExport(query)), fragment);
                assertEquals(fragment, query.getWorkerKeyword());
            }
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + WorkerSettlementListMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_worker_settlement_order");
            statement.execute("CREATE TABLE t_worker_settlement_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, settlement_order_id VARCHAR(64) UNIQUE, "
                    + "worker_name VARCHAR(128), period_start DATE, period_end DATE, "
                    + "amount_should_settle DECIMAL(18,2), deduct_amount DECIMAL(18,2), "
                    + "amount_net_settle DECIMAL(18,2), deposit_impact_amount DECIMAL(18,2), "
                    + "status VARCHAR(32), status_type VARCHAR(32), payout_status VARCHAR(32), "
                    + "payout_status_type VARCHAR(32), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_worker_settlement_order "
                    + "(settlement_order_id, worker_name, period_start, period_end, amount_should_settle, "
                    + "deduct_amount, amount_net_settle, deposit_impact_amount, status, status_type, "
                    + "payout_status, payout_status_type, created_at) VALUES "
                    + "('SETTLE-STABLE-001', '稳定排序服务者甲', '2026-09-01', '2026-09-07', "
                    + "101.00, 1.00, 100.00, 0.00, '待审核', 'warning', '待出款', 'warning', "
                    + "'2026-09-12 15:00:00'), "
                    + "('SETTLE-STABLE-002', '稳定排序服务者乙', '2026-09-01', '2026-09-07', "
                    + "202.00, 2.00, 200.00, 0.00, '待审核', 'warning', '待出款', 'warning', "
                    + "'2026-09-12 15:00:00'), "
                    + "('SETTLE-STABLE-003', '稳定排序服务者丙', '2026-09-01', '2026-09-07', "
                    + "303.00, 3.00, 300.00, 0.00, '待审核', 'warning', '待出款', 'warning', "
                    + "'2026-09-12 15:00:00')");
            session.commit();
        }
    }

    private List<String> settlementOrderIds(List<WorkerSettlementListItemDTO> settlements) {
        return settlements.stream()
                .map(WorkerSettlementListItemDTO::getSettlementOrderId)
                .collect(Collectors.toList());
    }

    /**
     * H2 测试环境中的 MySQL DATE_FORMAT 日期等价实现。
     */
    public static String dateFormat(Date date, String pattern) {
        return date == null ? null : date.toLocalDate().toString();
    }
}
