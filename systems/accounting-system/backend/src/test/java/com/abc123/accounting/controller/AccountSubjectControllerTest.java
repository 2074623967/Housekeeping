package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.CreateAccountSubjectRequestDTO;
import com.abc123.accounting.service.AccountSubjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账户主体控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class AccountSubjectControllerTest {

    @Mock
    private AccountSubjectService accountSubjectService;

    @Test
    void shouldListAccountSubjects() {
        AccountSubjectController controller = new AccountSubjectController(accountSubjectService);

        controller.list("服务者", "WORKER", "正常", 1, 20);

        verify(accountSubjectService).list("服务者", "WORKER", "正常", 1, 20);
    }

    @Test
    void shouldCreateAccountSubject() {
        AccountSubjectController controller = new AccountSubjectController(accountSubjectService);
        CreateAccountSubjectRequestDTO request = new CreateAccountSubjectRequestDTO();

        controller.create(request);

        verify(accountSubjectService).create(request);
    }

    @Test
    void shouldReturnAccountSubjectDetail() {
        AccountSubjectController controller = new AccountSubjectController(accountSubjectService);

        controller.detail("SUBJECT-001");

        verify(accountSubjectService).detail("SUBJECT-001");
    }
}
