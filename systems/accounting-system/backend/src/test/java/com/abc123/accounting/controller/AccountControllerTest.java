package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.OpenAccountRequestDTO;
import com.abc123.accounting.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账户控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Test
    void shouldListAccounts() {
        AccountController controller = new AccountController(accountService);

        controller.list("SUBJECT-001", "收入账户", "正常", 1, 20);

        verify(accountService).list("SUBJECT-001", "收入账户", "正常", 1, 20);
    }

    @Test
    void shouldOpenAccount() {
        AccountController controller = new AccountController(accountService);
        OpenAccountRequestDTO request = new OpenAccountRequestDTO();

        controller.open(request);

        verify(accountService).open(request);
    }

    @Test
    void shouldReturnAccountDetail() {
        AccountController controller = new AccountController(accountService);

        controller.detail("ACT10001");

        verify(accountService).detail("ACT10001");
    }
}
