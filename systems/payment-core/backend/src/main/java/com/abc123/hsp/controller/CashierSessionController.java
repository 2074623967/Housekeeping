package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.service.CashierSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<PageResultDTO<CashierSessionListItemDTO>> list(
            @RequestParam(required = false) String sessionNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(defaultValue = "全部") String terminal,
            @RequestParam(defaultValue = "全部") String sessionStatus,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        CashierSessionQueryDTO query = new CashierSessionQueryDTO();
        query.setSessionNo(sessionNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setCustomerName(customerName);
        query.setTerminal(terminal);
        query.setSessionStatus(sessionStatus);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(cashierSessionService.list(query));
    }
}
