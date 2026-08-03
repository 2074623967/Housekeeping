package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.service.ShareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 分账明细控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock
    private ShareService shareService;

    @Test
    void shouldListShareItems() {
        ShareController controller = new ShareController(shareService);

        controller.list("CLO20001", "WORKER", 1, 20);

        verify(shareService).list("CLO20001", "WORKER", 1, 20);
    }
}
