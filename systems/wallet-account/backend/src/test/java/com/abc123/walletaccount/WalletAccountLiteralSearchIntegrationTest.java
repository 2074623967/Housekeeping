package com.abc123.walletaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.walletaccount.dto.WalletAccountDTO;
import com.abc123.walletaccount.dto.WalletAccountQueryDTO;
import com.abc123.walletaccount.dto.PageResultDTO;
import com.abc123.walletaccount.service.WalletAccountService;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
        "spring.datasource.url=jdbc:h2:mem:wallet_account_literal;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
class WalletAccountLiteralSearchIntegrationTest {

    @Autowired
    private WalletAccountService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldSearchOwnerNameAndExternalNumberLiterallyWithoutChangingExactAccountNumber() {
        String ownerId = "WO-LITERAL-" + UUID.randomUUID();
        String accountNo = "WA-LITERAL-" + UUID.randomUUID();
        String ownerName = "用户%_!\\\"-" + UUID.randomUUID();
        String externalNumber = "EXT-%_!\\\"-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_wallet_owner "
                        + "(wallet_owner_id, owner_type, owner_name, owner_status, ext_ref_no) "
                        + "VALUES (?, 'USER', ?, 'ENABLED', ?)",
                ownerId, ownerName, externalNumber);
        jdbcTemplate.update("INSERT INTO t_wallet_account "
                        + "(wallet_account_no, wallet_owner_id, owner_type, owner_name, account_type, "
                        + "account_scene, currency_code, account_status, risk_level) "
                        + "VALUES (?, ?, 'USER', ?, 'MAIN', 'USER_STORE', 'CNY', 'ACTIVE', 'LOW')",
                accountNo, ownerId, ownerName);

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", ownerName)) {
            WalletAccountQueryDTO query = new WalletAccountQueryDTO();
            query.setOwnerType("USER");
            query.setKeyword(fragment);
            assertSingleAccount(accountNo, service.pageAccounts(query), fragment);
            assertEquals(fragment, query.getKeyword());
        }

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", externalNumber)) {
            WalletAccountQueryDTO query = new WalletAccountQueryDTO();
            query.setOwnerType("USER");
            query.setExtRefNo(fragment);
            assertSingleAccount(accountNo, service.pageAccounts(query), fragment);
            assertEquals(fragment, query.getExtRefNo());
        }

        WalletAccountQueryDTO exactQuery = new WalletAccountQueryDTO();
        exactQuery.setKeyword(accountNo);
        assertSingleAccount(accountNo, service.pageAccounts(exactQuery), accountNo);
    }

    private void assertSingleAccount(String accountNo, PageResultDTO<WalletAccountDTO> page, String filter) {
        assertEquals(1L, page.getTotal(), filter);
        assertEquals(Arrays.asList(accountNo), page.getRecords().stream()
                .map(WalletAccountDTO::getWalletAccountNo).collect(Collectors.toList()), filter);
    }
}
