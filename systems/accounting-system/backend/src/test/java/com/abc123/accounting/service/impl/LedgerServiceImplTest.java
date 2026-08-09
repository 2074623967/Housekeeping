package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 账务流水服务读路径测试。
 */
@SpringBootTest
class LedgerServiceImplTest {

    @Autowired
    private LedgerServiceImpl ledgerService;

    @Test
    void shouldExportLedgerSnapshot() {
        String csv = new String(ledgerService.exportSnapshot("ACT10001", "", ""), StandardCharsets.UTF_8);

        assertTrue(csv.contains("流水号,账户号,业务类型"));
        assertTrue(csv.contains("ACT10001"));
        assertTrue(csv.contains("PAYMENT_SUCCESS"));
    }
}
