package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.service.BillService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账单中心控制器。
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    /**
     * 查询交易账单中心列表，供运营查看账单支付进展。
     */
    @GetMapping
    public ApiResponse<List<BillListItemDTO>> list(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String billStatus) {
        BillQueryDTO query = new BillQueryDTO();
        query.setBillNo(billNo);
        query.setOrderNo(orderNo);
        query.setBillStatus(billStatus);
        return ApiResponse.success(billService.list(query));
    }
}
