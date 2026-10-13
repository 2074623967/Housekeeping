package com.abc123.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.dto.PageResultDTO;
import com.abc123.deposit.service.DepositService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deposit_flow_literal;"
        + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class DepositFlowLiteralSearchIntegrationTest {

    @Autowired
    private DepositService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldSearchReferenceNumberLiterallyAcrossListPageAndExport() {
        String accountNo = "DEP-LITERAL-" + UUID.randomUUID();
        String targetReference = "REF-%_!\\\"-" + UUID.randomUUID();
        String ordinaryReference = "REF-ORDINARY-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_deposit_account "
                        + "(account_no, owner_id, owner_type, required_amount, balance) "
                        + "VALUES (?, ?, 'WORKER', 0.00, 2.00)",
                accountNo, "OWNER-LITERAL-" + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO t_deposit_flow "
                        + "(flow_no, account_no, flow_type, amount, before_balance, after_balance, reference_no) "
                        + "VALUES (?, ?, 'COLLECT', 1.00, 0.00, 1.00, ?)",
                "FLOW-TARGET-" + UUID.randomUUID(), accountNo, targetReference);
        jdbcTemplate.update("INSERT INTO t_deposit_flow "
                        + "(flow_no, account_no, flow_type, amount, before_balance, after_balance, reference_no) "
                        + "VALUES (?, ?, 'COLLECT', 1.00, 1.00, 2.00, ?)",
                "FLOW-ORDINARY-" + UUID.randomUUID(), accountNo, ordinaryReference);

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", targetReference)) {
            DepositFlowQueryDTO query = new DepositFlowQueryDTO();
            query.setFlowType("COLLECT");
            query.setReferenceNo(fragment);
            assertEquals(Arrays.asList(targetReference), service.flows(accountNo, query).stream()
                    .map(DepositFlowDTO::getReferenceNo).collect(Collectors.toList()), fragment);
            PageResultDTO<DepositFlowDTO> page = service.flowsPage(accountNo, query);
            assertEquals(1L, page.getTotal(), fragment);
            assertEquals(Arrays.asList(targetReference), page.getItems().stream()
                    .map(DepositFlowDTO::getReferenceNo).collect(Collectors.toList()), fragment);
            String csv = new String(service.exportFlows(accountNo, query), StandardCharsets.UTF_8);
            assertTrue(csv.contains("REF-%_!"), fragment);
            assertFalse(csv.contains(ordinaryReference), fragment);
            assertEquals(fragment, query.getReferenceNo());
        }
    }
}
