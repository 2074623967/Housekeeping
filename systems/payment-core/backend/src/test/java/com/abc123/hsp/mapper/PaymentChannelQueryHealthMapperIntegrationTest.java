package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentChannelQueryHealthDTO;
import java.io.InputStream;
import java.sql.Statement;
import java.util.List;
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
 * 支付渠道短窗查单健康统计的 MySQL 兼容 SQL 集成测试。
 */
class PaymentChannelQueryHealthMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_channel_query_health;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldGroupOnlyRecentQueryFactsByNormalizedChannel() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<PaymentChannelQueryHealthDTO> metrics = session.getMapper(PaymentMapper.class)
                    .findRecentChannelQueryHealth(24);

            assertEquals(3, metrics.size());
            assertEquals("alipay_h5", metrics.get(0).getChannelCode());
            assertEquals(Integer.valueOf(1), metrics.get(0).getQueryCount());
            assertEquals(Integer.valueOf(1), metrics.get(0).getFailureCount());
            assertEquals("wx_h5", metrics.get(1).getChannelCode());
            assertEquals(Integer.valueOf(3), metrics.get(1).getQueryCount());
            assertEquals(Integer.valueOf(2), metrics.get(1).getFailureCount());
            assertEquals("wx_jsapi", metrics.get(2).getChannelCode());
            assertEquals(Integer.valueOf(1), metrics.get(2).getQueryCount());
            assertEquals(Integer.valueOf(1), metrics.get(2).getFailureCount());
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel_code VARCHAR(64), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_notify_log(channel_code, notify_status, created_at) VALUES "
                    + "('WX_H5', '已查单', TIMESTAMPADD(MINUTE, -10, NOW())),"
                    + "('wx_h5', '查单失败', TIMESTAMPADD(MINUTE, -20, NOW())),"
                    + "('wx_h5', '查单失败', TIMESTAMPADD(MINUTE, -30, NOW())),"
                    + "('wx_jsapi', '查单失败', TIMESTAMPADD(MINUTE, -35, NOW())),"
                    + "('alipay_h5', '查单失败', TIMESTAMPADD(MINUTE, -40, NOW())),"
                    + "('alipay_h5', '待回调', TIMESTAMPADD(MINUTE, -50, NOW())),"
                    + "('wx_h5', '查单失败', TIMESTAMPADD(HOUR, -25, NOW()))");
            session.commit();
        }
    }
}
