package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.CreateFreezeRequestDTO;
import com.abc123.accounting.dto.UnfreezeRequestDTO;
import com.abc123.accounting.service.FreezeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 冻结控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class FreezeControllerTest {

    @Mock
    private FreezeService freezeService;

    @Test
    void shouldListFreezes() {
        FreezeController controller = new FreezeController(freezeService);

        controller.list("ACT10001", "冻结中", 1, 20);

        verify(freezeService).list("ACT10001", "冻结中", 1, 20);
    }

    @Test
    void shouldCreateFreeze() {
        FreezeController controller = new FreezeController(freezeService);
        CreateFreezeRequestDTO request = new CreateFreezeRequestDTO();

        controller.create(request);

        verify(freezeService).create(request);
    }

    @Test
    void shouldUnfreeze() {
        FreezeController controller = new FreezeController(freezeService);
        UnfreezeRequestDTO request = new UnfreezeRequestDTO();

        controller.unfreeze("FRZ30001", request);

        verify(freezeService).unfreeze("FRZ30001", request);
    }
}
