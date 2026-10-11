package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 账户服务读路径测试。
 */
@SpringBootTest
class AccountServiceImplTest {

    @Autowired
    private AccountServiceImpl accountService;

    @Test
    void shouldExportAccountDetailSnapshot() {
        String csv = new String(accountService.exportDetailSnapshot("ACT10001"), StandardCharsets.UTF_8);

        assertTrue(csv.contains("账户号,主体ID,主体名称"));
        assertTrue(csv.contains("账户状态,状态样式,币种"));
        assertTrue(csv.contains("ACT10001"));
        assertTrue(csv.contains("正常,success"));
        assertTrue(csv.contains("最近流水"));
        assertTrue(csv.contains("状态,状态样式,操作人"));
        assertTrue(csv.contains("PAYMENT_SUCCESS"));
    }
}
