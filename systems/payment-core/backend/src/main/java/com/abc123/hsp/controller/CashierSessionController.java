package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.service.CashierSessionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收银台会话管理控制器。
 */
@RestController
@RequestMapping("/api/cashier-sessions")
public class CashierSessionController {

    private final CashierSessionService cashierSessionService;

    public CashierSessionController(CashierSessionService cashierSessionService) {
        this.cashierSessionService = cashierSessionService;
    }

    /**
     * 查询收银台会话列表，供运营排查终端会话状态。
     */
    @GetMapping
    public ApiResponse<List<CashierSessionListItemDTO>> list() {
        return ApiResponse.success(cashierSessionService.list());
    }
}
