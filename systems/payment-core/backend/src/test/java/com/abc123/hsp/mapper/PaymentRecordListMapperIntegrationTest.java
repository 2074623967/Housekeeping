package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
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
 * 支付记录列表与最近支付尝试的 MySQL 兼容 SQL 集成测试。
 */
class PaymentRecordListMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_record_list;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentRecordMapper.class);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentRecordMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentRecordMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseIdAsStableTieBreakerForEveryListSort() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE");
            query.setPageSize(1);

            assertEquals("PAY-RECORD-STABLE-003", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(2);
            assertEquals("PAY-RECORD-STABLE-002", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(3);
            assertEquals("PAY-RECORD-STABLE-001", mapper.findAll(query).get(0).getPaymentOrderId());
            assertEquals(3L, mapper.count(query));
            assertEquals("2026-09-12 16:00:10", mapper.findAll(query).get(0).getUpdatedAt());

            query.setPageNo(1);
            query.setPageSize(20);
            query.setSortField("paymentAmount");
            query.setSortOrder("asc");
            assertEquals(Arrays.asList(
                            "PAY-RECORD-STABLE-001",
                            "PAY-RECORD-STABLE-002",
                            "PAY-RECORD-STABLE-003"),
                    paymentOrderIds(mapper.findAll(query)));

            query.setSortField("paidAt");
            query.setSortOrder("desc");
            assertEquals(Arrays.asList(
                            "PAY-RECORD-STABLE-003",
                            "PAY-RECORD-STABLE-002",
                            "PAY-RECORD-STABLE-001"),
                    paymentOrderIds(mapper.findAll(query)));
            assertEquals("2026-09-12 16:00:30", mapper.findAll(query).get(0).getUpdatedAt());
        }
    }

    @Test
    void shouldSelectNewestAttemptByIdWhenCreationTimesMatch() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-RECORD-STABLE-003");

            assertEquals("NEW_TERMINAL", detail.getLatestTerminal());
            assertEquals("NEW_KEY", detail.getLatestIdempotencyKey());
            assertEquals("SUCCESS", detail.getLatestAttemptStatus());
            assertEquals("2026-09-12 16:01:05", detail.getLatestAttemptCompletedAt());
            assertEquals("new-response", detail.getLatestResponsePayload());
            assertEquals("2026-09-12 16:00:30", detail.getUpdatedAt());
            assertEquals("ATT-NEW", detail.getPaymentRequestNo());
            assertEquals("housekeeping-h5-web", detail.getApplicationSourceId());

            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0))) {
                assertEquals("ATT-NEW", row.getPaymentRequestNo());
                assertEquals("housekeeping-h5-web", row.getApplicationSourceId());
                assertEquals("SUCCESS", row.getLatestAttemptStatus());
                assertEquals("success", row.getLatestAttemptStatusType());
                assertEquals("2026-09-12 16:01:05", row.getLatestAttemptCompletedAt());
            }

            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
            assertNull(mapper.findAll(query).get(0).getPaymentRequestNo());
            assertNull(mapper.findAll(query).get(0).getLatestAttemptStatus());
        }
    }

    @Test
    void shouldExposeRecordedChannelReturnCodeWithoutInferringItFromPaymentStatus() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET last_channel_return_code = 'WX-BIZ-42' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");

            assertEquals("WX-BIZ-42", mapper.findAll(query).get(0).getChannelReturnCode());
            assertEquals("WX-BIZ-42", mapper.findAllForExport(query).get(0).getChannelReturnCode());
            assertEquals("WX-BIZ-42", mapper.findDetail("PAY-RECORD-STABLE-001").getChannelReturnCode());
            assertNull(mapper.findDetail("PAY-RECORD-STABLE-002").getChannelReturnCode());
        }
    }

    @Test
    void shouldOnlyDescribeRecordedChannelTransactionNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET channel_code = 'offline_bank', "
                    + "channel_transaction_no = 'BANK-001' WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-001"))) {
                assertEquals("渠道交易号", row.getReturnParameterType());
                assertEquals("BANK-001", row.getParameterValue());
            }

            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no = NULL "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-001"))) {
                assertNull(row.getReturnParameterType());
                assertNull(row.getParameterValue());
            }
        }
    }

    @Test
    void shouldNotInferHistoricalGatewayFromChannelCode() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
            for (String channelCode : Arrays.asList("wx_h5", "alipay_h5", "offline_bank", "unmapped_channel")) {
                statement.executeUpdate("UPDATE t_payment_order SET channel_code = '" + channelCode + "' "
                        + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
                session.clearCache();
                for (PaymentRecordRowDTO row : Arrays.asList(
                        mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                        mapper.findDetail("PAY-RECORD-STABLE-001"))) {
                    assertEquals(channelCode, row.getPaymentChannel());
                    assertNull(row.getPaymentGateway());
                }
            }
        }
    }

    @Test
    void shouldExposeOnlyGatewayCapturedOnLatestPaymentAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            statement.executeUpdate("UPDATE t_payment_attempt SET gateway_code = 'GW_WX' "
                    + "WHERE attempt_no = 'ATT-NEW'");
            session.clearCache();
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertEquals("GW_WX", row.getPaymentGateway());
            }
            assertNull(mapper.findDetail("PAY-RECORD-STABLE-001").getPaymentGateway());
        }
    }

    @Test
    void shouldNotInventBankOrReceivingAccountFromChannelCode() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET channel_code = 'offline_bank' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");

            assertUnavailableBankFacts(mapper.findAll(query).get(0));
            assertUnavailableBankFacts(mapper.findAllForExport(query).get(0));
            assertUnavailableBankFacts(mapper.findDetail("PAY-RECORD-STABLE-001"));
        }
    }

    @Test
    void shouldNotInventUserProductOrBusinessLineFromCustomerAndOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = 'WALLET_RECHARGE_APP' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setUserId("稳定用户甲");

            assertEquals(1L, mapper.count(query));
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-001"))) {
                assertEquals("充值支付", row.getPaymentType());
                assertNull(row.getBusinessLineId());
                assertNull(row.getProductName());
                assertNull(row.getUserPaymentChannelId());
                assertNull(row.getUserId());
            }
        }
    }

    @Test
    void shouldDerivePaymentTypeFromSceneAcrossListCountExportAndDetail() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            String[][] cases = {
                {"WALLET_RECHARGE_APP", "充值支付"},
                {"WITHDRAW_REPAY_H5", "提现补款"},
                {"ACCOUNT_TRANSFER_PC", "转账补款"},
                {"BALANCE_PAY_APP", "余额补款"},
                {"HOME_CLEAN", "消费支付"}
            };
            for (String[] sceneAndType : cases) {
                statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = '" + sceneAndType[0]
                        + "' WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
                session.clearCache();
                PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
                query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
                query.setPaymentType(sceneAndType[1]);

                assertEquals(1L, mapper.count(query));
                assertEquals(sceneAndType[1], mapper.findAll(query).get(0).getPaymentType());
                assertEquals(sceneAndType[1], mapper.findAllForExport(query).get(0).getPaymentType());
                assertEquals(sceneAndType[1], mapper.findDetail("PAY-RECORD-STABLE-001").getPaymentType());
            }

            statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = 'UNKNOWN_SCENE' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
            query.setPaymentType("未分类");
            assertEquals(1L, mapper.count(query));
            assertEquals("UNKNOWN_SCENE", mapper.findAll(query).get(0).getPayScene());
            statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = 'HOME_CLEAN' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");

            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at, expires_at) "
                    + "VALUES ('PAY-RECORD-STABLE-001', 'UNKNOWN_SCENE', '2026-09-12 15:00:00', '2026-09-12 15:30:00')");
            session.clearCache();
            query.setPaymentType("消费支付");
            assertEquals(0L, mapper.count(query));

            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at, expires_at) "
                    + "VALUES ('PAY-RECORD-STABLE-001', 'UNKNOWN_SCENE', '2026-09-12 17:00:00', '2026-09-12 20:30:00')");
            session.clearCache();
            query.setPaymentType("未分类");
            assertEquals(1L, mapper.count(query));
            assertEquals("未分类", mapper.findAll(query).get(0).getPaymentType());
            assertEquals("未分类", mapper.findAllForExport(query).get(0).getPaymentType());
            assertEquals("未分类", mapper.findDetail("PAY-RECORD-STABLE-001").getPaymentType());
            assertNull(mapper.findAll(query).get(0).getExpireTime());
            assertNull(mapper.findDetail("PAY-RECORD-STABLE-001").getValidityPeriod());

            statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = NULL "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001' AND created_at = '2026-09-12 17:00:00'");
            session.clearCache();
            assertEquals(1L, mapper.count(query));
            assertEquals("未分类", mapper.findDetail("PAY-RECORD-STABLE-001").getPaymentType());
        }
    }

    @Test
    void shouldNotInferRecordSceneOrExpiryFromMultipleExactPrepayBindings() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");

            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at, expires_at) "
                    + "VALUES ('pay-record-stable-001', 'WITHDRAW_REPAY_APP', "
                    + "'2026-09-12 17:00:00', '2026-09-12 18:00:00')");
            session.clearCache();
            assertEquals("HOME_CLEAN", mapper.findAll(query).get(0).getPayScene());
            assertNull(mapper.findAll(query).get(0).getPrepayBindingIssue());

            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at, expires_at) "
                    + "VALUES ('PAY-RECORD-STABLE-001', 'HOME_CLEAN', "
                    + "'2026-09-12 17:00:00', '2026-09-12 20:30:00')");
            session.clearCache();
            assertEquals(1L, mapper.count(query));
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0))) {
                assertNull(row.getPayScene());
                assertNull(row.getExpireTime());
                assertNull(row.getValidityPeriod());
                assertEquals("未分类", row.getPaymentType());
                assertEquals("MULTIPLE_PREPAY_BINDINGS", row.getPrepayBindingIssue());
            }
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-RECORD-STABLE-001");
            assertNull(detail.getPayScene());
            assertNull(detail.getExpireTime());
            assertNull(detail.getValidityPeriod());
            assertEquals("未分类", detail.getPaymentType());
            assertEquals("MULTIPLE_PREPAY_BINDINGS", detail.getPrepayBindingIssue());

            query.setPaymentType("消费支付");
            assertEquals(0L, mapper.count(query));
            assertEquals(0, mapper.findAll(query).size());
            assertEquals(0, mapper.findAllForExport(query).size());
            query.setPaymentType("未分类");
            assertEquals(1L, mapper.count(query));
        }
    }

    @Test
    void shouldKeepRecordReadableWhenExactPrepayBindingIsMissing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-001");
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-record-stable-001' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-001'");
            session.clearCache();

            assertEquals(1L, mapper.count(query));
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0))) {
                assertNull(row.getPayScene());
                assertNull(row.getExpireTime());
                assertNull(row.getValidityPeriod());
                assertEquals("未分类", row.getPaymentType());
                assertEquals("PREPAY_BINDING_MISSING", row.getPrepayBindingIssue());
            }
            PaymentRecordDetailDTO detail = mapper.findDetail("PAY-RECORD-STABLE-001");
            assertNull(detail.getPayScene());
            assertEquals("PREPAY_BINDING_MISSING", detail.getPrepayBindingIssue());
        }
    }

    @Test
    void shouldExportAllMatchingRowsWithTheSameStableOrder() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE");
            query.setPageNo(2);
            query.setPageSize(1);
            query.setSortField("paymentAmount");
            query.setSortOrder("asc");

            assertEquals(Arrays.asList(
                            "PAY-RECORD-STABLE-001",
                            "PAY-RECORD-STABLE-002",
                            "PAY-RECORD-STABLE-003"),
                    paymentOrderIds(mapper.findAllForExport(query)));
            assertEquals("HOME_CLEAN", mapper.findAllForExport(query).get(0).getPayScene());
            assertEquals("2026-09-12 16:00:10", mapper.findAllForExport(query).get(0).getUpdatedAt());
        }
    }

    @Test
    void shouldUseLatestPrepayExpiryAndActualValidityPeriod() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");

            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertEquals("2026-09-12 18:30:00", row.getExpireTime());
                assertEquals("9000秒", row.getValidityPeriod());
            }
        }
    }

    @Test
    void shouldExposeOnlyPersistedPaymentSuccessEventTopic() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");

            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertEquals("payment.trade.succeeded.v1", row.getCallbackMqTopic());
                assertNull(row.getNotifyUrl());
            }
            assertNull(mapper.findDetail("PAY-RECORD-STABLE-001").getCallbackMqTopic());
        }
    }

    @Test
    void shouldProjectOwningPaymentSuccessIssueAcrossRecordReads() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '成功', created_at = "
                    + "TIMESTAMP '2026-09-12 16:00:01', completed_at = "
                    + "TIMESTAMP '2026-09-12 16:00:02' WHERE attempt_no = 'ATT-NEW'");
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at = "
                    + "TIMESTAMP '2026-09-12 16:00:00' WHERE attempt_no = 'ATT-OLD'");
            session.clearCache();
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertNull(mapper.findDetail("PAY-RECORD-STABLE-003").getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = "
                    + "TIMESTAMP '2026-09-12 16:00:03.001' WHERE attempt_no = 'ATT-NEW'");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("PAY-RECORD-STABLE-003")
                    .getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-003'");
            session.clearCache();
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", mapper.findDetail("PAY-RECORD-STABLE-003")
                    .getSuccessFactIssue());
        }
    }

    @Test
    void shouldFlagCaseVariantPrepayAndOriginalOrderNumbersAsSuccessFactIssues() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='成功', "
                    + "created_at='2026-09-12 16:00:01', completed_at='2026-09-12 16:00:02' "
                    + "WHERE attempt_no='ATT-NEW'");
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-12 16:00:00' "
                    + "WHERE attempt_no='ATT-OLD'");
            session.clearCache();
            assertNull(mapper.findAll(query).get(0).getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no='order-record-stable-003' "
                    + "WHERE payment_order_id='PAY-RECORD-STABLE-003'");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAll(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findAllForExport(query).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("PAY-RECORD-STABLE-003")
                    .getSuccessFactIssue());

            statement.executeUpdate("UPDATE t_prepay_order SET order_no='ORDER-RECORD-STABLE-003', "
                    + "source_kind='STANDARD_ORDER' WHERE payment_order_id='PAY-RECORD-STABLE-003'");
            statement.executeUpdate("INSERT INTO t_order VALUES ('order-record-stable-003')");
            session.clearCache();
            assertEquals("SUCCESS_FACT_MISMATCH", mapper.findDetail("PAY-RECORD-STABLE-003")
                    .getSuccessFactIssue());
        }
    }

    @Test
    void shouldNotReadPaymentRecordDetailThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertNull(session.getMapper(PaymentRecordMapper.class).findDetail("pay-record-stable-003"));
        }
    }

    @Test
    void shouldNotAttachPrepayThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-record-stable-003' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-003'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertNull(row.getPayScene());
                assertEquals("未分类", row.getPaymentType());
            }
            query.setPaymentType("消费支付");
            assertEquals(0L, mapper.count(query));
        }
    }

    @Test
    void shouldNotAttachAttemptThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id = 'pay-record-stable-003' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-003'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertNull(row.getPaymentRequestNo());
                assertNull(row.getPaymentGateway());
            }
        }
    }

    @Test
    void shouldNotAttachSuccessEventThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id = 'pay-record-stable-003' "
                    + "WHERE payment_order_id = 'PAY-RECORD-STABLE-003'");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertNull(row.getCallbackMqTopic());
            }
        }
    }

    @Test
    void shouldNotAttachRefundSummaryThroughPaymentNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, refund_amount, status) "
                    + "VALUES ('REF-ALIEN', 'pay-record-stable-003', 10.00, 'SUCCESS')");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertEquals(0, row.getUnverifiedRefundCount());
                assertEquals(0, row.getRefundCount());
                assertEquals("¥0.00", row.getRefundedAmount());
            }
        }
    }

    @Test
    void shouldKeepCanonicalAndAliasRefundSummarySeparate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order "
                    + "(refund_order_id, payment_order_id, refund_amount, status) VALUES "
                    + "('REF-OWN', 'PAY-RECORD-STABLE-003', 10.00, 'SUCCESS'), "
                    + "('REF-ALIEN', 'pay-record-stable-003', 20.00, 'SUCCESS')");
            PaymentRecordMapper mapper = session.getMapper(PaymentRecordMapper.class);
            PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
            query.setBusinessOrderNo("ORDER-RECORD-STABLE-003");
            for (PaymentRecordRowDTO row : Arrays.asList(
                    mapper.findAll(query).get(0), mapper.findAllForExport(query).get(0),
                    mapper.findDetail("PAY-RECORD-STABLE-003"))) {
                assertEquals(1, row.getUnverifiedRefundCount());
            }
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentRecordRefundAggregationMapperIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR '"
                    + PaymentRecordRefundAggregationMapperIntegrationTest.class.getName()
                    + ".formatAmount'");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR_IGNORECASE(64))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) UNIQUE, "
                    + "order_no VARCHAR_IGNORECASE(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), last_channel_return_code VARCHAR(64), status VARCHAR(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME, updated_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR_IGNORECASE(64), amount DECIMAL(18,2), "
                    + "pay_scene VARCHAR(32), created_at DATETIME, expires_at DATETIME, "
                    + "source_kind VARCHAR(16) DEFAULT 'VIRTUAL')");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "attempt_no VARCHAR(64), channel_code VARCHAR(64), "
                    + "source_app_id VARCHAR(64), gateway_code VARCHAR(64), "
                    + "payment_method VARCHAR(32), terminal VARCHAR(32), client_ip VARCHAR(64), "
                    + "idempotency_key VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), completed_at DATETIME, request_payload VARCHAR(512), "
                    + "response_payload VARCHAR(512), created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), refund_amount DECIMAL(18,2), status VARCHAR(32), "
                    + "status_type VARCHAR(32), applied_at DATETIME, channel_code VARCHAR(64), "
                    + "channel_refund_no VARCHAR(128), channel_submitted_at DATETIME, "
                    + "success_at DATETIME, terminal_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "event_type VARCHAR(64), event_topic VARCHAR(128), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order "
                    + "(payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "channel_transaction_no, status, status_type, created_at, updated_at) VALUES "
                    + "('PAY-RECORD-STABLE-001', 'ORDER-RECORD-STABLE-001', '稳定用户甲', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-001', 'SUCCESS', 'success', '2026-09-12 16:00:00', '2026-09-12 16:00:10'), "
                    + "('PAY-RECORD-STABLE-002', 'ORDER-RECORD-STABLE-002', '稳定用户乙', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-002', 'SUCCESS', 'success', '2026-09-12 16:00:00', '2026-09-12 16:00:20'), "
                    + "('PAY-RECORD-STABLE-003', 'ORDER-RECORD-STABLE-003', '稳定用户丙', 100.00, "
                    + "'微信', 'wx_h5', 'CHANNEL-003', 'SUCCESS', 'success', '2026-09-12 16:00:00', '2026-09-12 16:00:30')");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = "
                    + "TIMESTAMPADD(SECOND, id, created_at)");
            statement.executeUpdate("INSERT INTO t_prepay_order (payment_order_id, pay_scene, created_at, expires_at) VALUES "
                    + "('PAY-RECORD-STABLE-001', 'HOME_CLEAN', '2026-09-12 16:00:00', '2026-09-12 17:00:00'), "
                    + "('PAY-RECORD-STABLE-002', 'HOME_REPAIR', '2026-09-12 16:00:00', '2026-09-12 17:00:00'), "
                    + "('PAY-RECORD-STABLE-003', 'HOME_CLEAN', '2026-09-12 16:00:00', '2026-09-12 18:30:00')");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = "
                    + "(SELECT order_no FROM t_payment_order WHERE payment_order_id = t_prepay_order.payment_order_id), "
                    + "amount = (SELECT amount FROM t_payment_order "
                    + "WHERE payment_order_id = t_prepay_order.payment_order_id)");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(payment_order_id, attempt_no, source_app_id, payment_method, terminal, client_ip, idempotency_key, "
                    + "attempt_status, attempt_status_type, completed_at, request_payload, response_payload, created_at) VALUES "
                    + "('PAY-RECORD-STABLE-003', 'ATT-OLD', 'housekeeping-app-web', '微信', 'OLD_TERMINAL', '127.0.0.1', 'OLD_KEY', "
                    + "'PROCESSING', 'warning', NULL, 'old-request', 'old-response', '2026-09-12 16:01:00'), "
                    + "('PAY-RECORD-STABLE-003', 'ATT-NEW', 'housekeeping-h5-web', '微信', 'NEW_TERMINAL', '127.0.0.2', 'NEW_KEY', "
                    + "'SUCCESS', 'success', '2026-09-12 16:01:05', 'new-request', 'new-response', '2026-09-12 16:01:00')");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code = "
                    + "(SELECT p.channel_code FROM t_payment_order p "
                    + "WHERE p.payment_order_id = t_payment_attempt.payment_order_id)");
            statement.executeUpdate("INSERT INTO t_payment_event (payment_order_id, event_type, event_topic, created_at) VALUES "
                    + "('PAY-RECORD-STABLE-003', 'PAYMENT_SUCCESS', 'payment.trade.old.v1', '2026-09-12 16:02:00'), "
                    + "('PAY-RECORD-STABLE-003', 'PAYMENT_SUCCESS', 'payment.trade.succeeded.v1', '2026-09-12 16:02:00'), "
                    + "('PAY-RECORD-STABLE-003', 'PAYMENT_SUBMIT', 'payment.trade.submitted.v1', '2026-09-12 16:03:00')");
            session.commit();
        }
    }

    private List<String> paymentOrderIds(List<PaymentRecordRowDTO> records) {
        return records.stream()
                .map(PaymentRecordRowDTO::getPaymentOrderId)
                .collect(Collectors.toList());
    }

    private void assertUnavailableBankFacts(PaymentRecordRowDTO record) {
        assertNull(record.getBankName());
        assertNull(record.getCardNo());
        assertNull(record.getReceivingAccount());
    }
}
