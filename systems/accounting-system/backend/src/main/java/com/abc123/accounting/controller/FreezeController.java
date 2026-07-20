package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.CreateFreezeRequestDTO;
import com.abc123.accounting.dto.FreezeItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.UnfreezeRequestDTO;
import com.abc123.accounting.service.FreezeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 冻结控制器。
 */
@RestController
@RequestMapping("/api/accounting/freezes")
public class FreezeController {

    private final FreezeService freezeService;

    public FreezeController(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<FreezeItemDTO>> list(
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String freezeStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(freezeService.list(accountNo, freezeStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<FreezeItemDTO> create(@RequestBody CreateFreezeRequestDTO request) {
        return ApiResponse.success(freezeService.create(request));
    }

    @PostMapping("/{freezeNo}/unfreeze")
    public ApiResponse<FreezeItemDTO> unfreeze(@PathVariable String freezeNo, @RequestBody UnfreezeRequestDTO request) {
        return ApiResponse.success(freezeService.unfreeze(freezeNo, request));
    }
}
