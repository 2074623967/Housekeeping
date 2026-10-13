package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentDeadLetterTaskLiteralSearchIntegrationTest {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger transactionLogger = (Logger) LoggerFactory.getLogger(TransactionTemplate.class);
    private final Level originalRootLevel = rootLogger.getLevel();
    private final Level originalTransactionLevel = transactionLogger.getLevel();

    @AfterEach
    void restoreLoggingLevels() {
        rootLogger.setLevel(originalRootLevel);
        transactionLogger.setLevel(originalTransactionLevel);
    }

    @Test
    void shouldSearchDeadLetterMessageIdsLiterallyWithoutChangingReplayState() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=jdbc:h2:mem:dead_letter_literal_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            PaymentDeadLetterTaskMapper mapper = context.getBean(PaymentDeadLetterTaskMapper.class);
            String messageId = "MSG-LITERAL-%_!\\\"";
            String insert = "INSERT INTO t_payment_dead_letter_task (task_no, message_id, "
                    + "dead_letter_routing_key, target_system, replay_exchange, replay_routing_key, "
                    + "task_status, task_status_type, payload_snapshot, source_signature_verified, "
                    + "replay_count, created_at, updated_at) VALUES (?, ?, 'payment.dead', "
                    + "'local-test-system', 'payment.replay', 'payment.retry', 'PENDING_REVIEW', "
                    + "'warn', '{}', 0, 0, NOW(), NOW())";
            jdbc.update(insert, "DLQ-LITERAL", messageId);
            jdbc.update(insert, "DLQ-PLAIN", "MSG-PLAIN");

            for (String fragment : Arrays.asList("%", "_", "!", "\\", "%_!\\\"")) {
                PaymentDeadLetterTaskQueryDTO query = new PaymentDeadLetterTaskQueryDTO();
                query.setTaskStatus("PENDING_REVIEW");
                query.setTargetSystem("local-test-system");
                query.setMessageId(fragment);
                assertEquals(1L, mapper.count(query), query.toString());
                assertEquals(1, mapper.findAll(query).size());
                assertEquals("DLQ-LITERAL", mapper.findAll(query).get(0).getTaskNo());
                assertEquals(fragment, query.getMessageId());
            }
            assertEquals("PENDING_REVIEW", mapper.findByTaskNo("DLQ-LITERAL").getTaskStatus());
            assertEquals(0, mapper.findByTaskNo("DLQ-LITERAL").getReplayCount().intValue());
        }
    }
}
