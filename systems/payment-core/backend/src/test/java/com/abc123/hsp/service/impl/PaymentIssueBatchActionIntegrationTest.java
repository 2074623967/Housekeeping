package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.service.PaymentIssueService;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class PaymentIssueBatchActionIntegrationTest {

    @Test
    void shouldReturnAllSelectedIssuesAfterActualMapperWritesBeyondDefaultPage() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=jdbc:h2:mem:payment_issue_batch_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("DROP TABLE t_payment_issue_action_request");
            DataSource dataSource = context.getBean(DataSource.class);
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                        "../database/migrations/2026-10-10-add-issue-action-request-idempotency.sql"));
            }
            PaymentIssueService service = context.getBean(PaymentIssueService.class);
            List<String> issueNos = new ArrayList<>();
            for (int index = 1; index <= 25; index++) {
                String paymentOrderId = String.format("PAY-ISSUE-%02d", index);
                jdbc.update("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, "
                                + "amount, payment_method, channel_code, status, status_type, created_at) "
                                + "VALUES (?, ?, '本地用户', 10.00, '支付宝', 'alipay_h5', "
                                + "'WAIT_CALLBACK', 'warn', NOW())",
                        paymentOrderId, "ORD-" + paymentOrderId);
                issueNos.add("ISSUE-WAIT-" + paymentOrderId);
            }
            assertTrue(service.list(new PaymentIssueQueryDTO()).getItems().size() < issueNos.size());

            PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
            request.setIssueNos(issueNos);
            request.setActionType("分派处理人");
            request.setAssignee("后端值班");
            request.setOperator("支付运营");
            request.setRemark("核对渠道回调");
            request.setIdempotencyKey("batch-25-issues");
            PageResultDTO<PaymentIssueRowDTO> result = service.batchAction(request);

            assertEquals(issueNos.size(), result.getItems().size());
            assertEquals(issueNos.size(), result.getTotal());
            assertEquals(1, result.getPageNo());
            assertEquals(issueNos.size(), result.getPageSize());
            for (int index = 0; index < issueNos.size(); index++) {
                PaymentIssueRowDTO item = result.getItems().get(index);
                assertEquals(issueNos.get(index), item.getIssueNo());
                assertEquals("已分派", item.getHandlingStatus());
                assertEquals("后端值班", item.getAssignee());
                assertEquals("本*", item.getCustomerName());
            }
            assertEquals(issueNos.size(), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_log WHERE issue_no LIKE 'ISSUE-WAIT-PAY-ISSUE-%'",
                    Integer.class));

            request.setIssueNos(new ArrayList<>(issueNos));
            java.util.Collections.reverse(request.getIssueNos());
            request.setRemark(" 核对渠道回调 ");
            PageResultDTO<PaymentIssueRowDTO> replay = service.batchAction(request);
            assertEquals(issueNos, replay.getItems().stream().map(PaymentIssueRowDTO::getIssueNo)
                    .collect(java.util.stream.Collectors.toList()));
            assertEquals(issueNos.size(), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_log WHERE issue_no LIKE 'ISSUE-WAIT-PAY-ISSUE-%'",
                    Integer.class));
            request.setAssignee("其他值班");
            BusinessException conflict = assertThrows(BusinessException.class, () -> service.batchAction(request));
            assertEquals(ErrorCode.PAYMENT_ISSUE_ACTION_IDEMPOTENCY_CONFLICT, conflict.getCode());
            request.setAssignee("后端值班");
            request.setIssueNos(Arrays.asList(issueNos.get(1)));
            BusinessException targetConflict = assertThrows(BusinessException.class,
                    () -> service.batchAction(request));
            assertEquals(ErrorCode.PAYMENT_ISSUE_ACTION_IDEMPOTENCY_CONFLICT, targetConflict.getCode());
            request.setIssueNos(new ArrayList<>(issueNos));

            jdbc.update("DELETE FROM t_payment_order WHERE payment_order_id = ?", "PAY-ISSUE-01");
            PageResultDTO<PaymentIssueRowDTO> vanishedReplay = service.batchAction(request);
            assertEquals(issueNos.get(0), vanishedReplay.getItems().get(0).getIssueNo());

            request.setIssueNos(Arrays.asList(issueNos.get(1), "ISSUE-MISSING"));
            request.setIdempotencyKey("batch-missing-issue");
            assertThrows(IllegalArgumentException.class, () -> service.batchAction(request));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_log WHERE issue_no = ?",
                    Integer.class, issueNos.get(1)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_request", Integer.class));

            ExecutorService workers = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<PageResultDTO<PaymentIssueRowDTO>>> attempts = new ArrayList<>();
                for (int index = 0; index < 2; index++) {
                    attempts.add(workers.submit(() -> {
                        PaymentIssueActionRequestDTO concurrent = new PaymentIssueActionRequestDTO();
                        concurrent.setIdempotencyKey("batch-concurrent-issue");
                        concurrent.setIssueNos(Arrays.asList(issueNos.get(2)));
                        concurrent.setActionType("分派处理人");
                        concurrent.setAssignee("后端值班");
                        concurrent.setOperator("支付运营");
                        concurrent.setRemark("并发复核");
                        start.await();
                        return service.batchAction(concurrent);
                    }));
                }
                start.countDown();
                for (Future<PageResultDTO<PaymentIssueRowDTO>> attempt : attempts) {
                    assertEquals(issueNos.get(2), attempt.get(10, TimeUnit.SECONDS).getItems().get(0).getIssueNo());
                }
                assertEquals(2, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_issue_action_log WHERE issue_no = ?",
                        Integer.class, issueNos.get(2)));
            } finally {
                workers.shutdownNow();
            }

            PaymentIssueActionRequestDTO caseVariant = new PaymentIssueActionRequestDTO();
            caseVariant.setIssueNos(Arrays.asList(issueNos.get(3)));
            caseVariant.setActionType("分派处理人");
            caseVariant.setAssignee("后端值班");
            caseVariant.setOperator("支付运营");
            caseVariant.setRemark("键大小写独立");
            caseVariant.setIdempotencyKey("Case-Key");
            service.batchAction(caseVariant);
            caseVariant.setIdempotencyKey("case-key");
            service.batchAction(caseVariant);
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_log WHERE issue_no = ?",
                    Integer.class, issueNos.get(3)));
            assertEquals(4, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_request", Integer.class));
        }
    }
}
