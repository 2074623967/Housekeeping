package com.abc123.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deposit_account_literal;"
        + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class DepositAccountLiteralSearchIntegrationTest {

    @Autowired
    private DepositService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldSearchAccountAndOwnerNumbersLiterallyAcrossListPageAndExport() {
        String accountNo = "DEP-%_!\\\"-" + UUID.randomUUID();
        String ownerId = "OWNER-%_!\\\"-" + UUID.randomUUID();
        String ordinaryAccountNo = "DEP-ORDINARY-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_deposit_account "
                        + "(account_no, owner_id, owner_type, required_amount) "
                        + "VALUES (?, ?, 'WORKER', 0.00)",
                accountNo, ownerId);
        jdbcTemplate.update("INSERT INTO t_deposit_account "
                        + "(account_no, owner_id, owner_type, required_amount) "
                        + "VALUES (?, ?, 'WORKER', 0.00)",
                ordinaryAccountNo, "OWNER-ORDINARY-" + UUID.randomUUID());

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", accountNo, ownerId)) {
            DepositAccountQueryDTO query = new DepositAccountQueryDTO();
            query.setOwnerType("WORKER");
            query.setKeyword(fragment);
            assertEquals(Arrays.asList(accountNo), service.accounts(query).stream()
                    .map(DepositAccountDTO::getAccountNo).collect(Collectors.toList()), fragment);
            PageResultDTO<DepositAccountDTO> page = service.accountsPage(query);
            assertEquals(1L, page.getTotal(), fragment);
            assertEquals(Arrays.asList(accountNo), page.getItems().stream()
                    .map(DepositAccountDTO::getAccountNo).collect(Collectors.toList()), fragment);
            String csv = new String(service.exportAccounts(query), StandardCharsets.UTF_8);
            assertTrue(csv.contains("DEP-%_!"), fragment);
            assertFalse(csv.contains(ordinaryAccountNo), fragment);
            assertEquals(fragment, query.getKeyword());
        }
    }
}
