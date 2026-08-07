package com.abc123.deposit.controller;

import com.abc123.deposit.common.ApiResponse;
import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.dto.DepositMigrationPreviewDTO;
import com.abc123.deposit.dto.DepositOverviewDTO;
import com.abc123.deposit.dto.DepositTotalAccountDTO;
import com.abc123.deposit.dto.DepositTotalAccountDetailDTO;
import com.abc123.deposit.dto.DepositV2OverviewDTO;
import com.abc123.deposit.service.DepositService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保证金后台接口。
 */
@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<DepositOverviewDTO> overview() {
        return ApiResponse.success(service.overview());
    }

    @GetMapping("/accounts")
    public ApiResponse<List<DepositAccountDTO>> accounts(@RequestParam(required = false) String ownerType,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String keyword) {
        DepositAccountQueryDTO query = new DepositAccountQueryDTO();
        query.setOwnerType(ownerType);
        query.setStatus(status);
        query.setKeyword(keyword);
        return ApiResponse.success(service.accounts(query));
    }

    @GetMapping("/accounts/{accountNo}")
    public ApiResponse<DepositAccountDetailDTO> accountDetail(@PathVariable String accountNo) {
        return ApiResponse.success(service.accountDetail(accountNo));
    }

    @PostMapping("/accounts")
    public ApiResponse<DepositAccountDTO> openAccount(@RequestBody DepositAccountCreateRequestDTO request) {
        return ApiResponse.success(service.openAccount(request));
    }

    @PostMapping("/accounts/{accountNo}/collect")
    public ApiResponse<DepositAccountDTO> collect(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.collect(request));
    }

    @PostMapping("/accounts/{accountNo}/freeze")
    public ApiResponse<DepositAccountDTO> freeze(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.freeze(request));
    }

    @PostMapping("/accounts/{accountNo}/unfreeze")
    public ApiResponse<DepositAccountDTO> unfreeze(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.unfreeze(request));
    }

    @PostMapping("/accounts/{accountNo}/deduct")
    public ApiResponse<DepositAccountDTO> deduct(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.deduct(request));
    }

    @PostMapping("/accounts/{accountNo}/refund")
    public ApiResponse<DepositAccountDTO> refund(@PathVariable String accountNo, @RequestBody DepositActionRequestDTO request) {
        request.setAccountNo(accountNo);
        return ApiResponse.success(service.refund(request));
    }

    @PostMapping("/offset-debt")
    public ApiResponse<DepositAccountDTO> offsetDebt(@RequestBody DebtOffsetRequestDTO request) {
        return ApiResponse.success(service.offsetDebt(request));
    }

    @GetMapping("/accounts/{accountNo}/flows")
    public ApiResponse<List<DepositFlowDTO>> flows(@PathVariable String accountNo,
                                                   @RequestParam(required = false) String flowType,
                                                   @RequestParam(required = false) String referenceNo) {
        DepositFlowQueryDTO query = new DepositFlowQueryDTO();
        query.setFlowType(flowType);
        query.setReferenceNo(referenceNo);
        return ApiResponse.success(service.flows(accountNo, query));
    }

    @GetMapping("/v2/overview")
    public ApiResponse<DepositV2OverviewDTO> v2Overview() {
        return ApiResponse.success(service.v2Overview());
    }

    @GetMapping("/v2/total-accounts")
    public ApiResponse<List<DepositTotalAccountDTO>> totalAccounts() {
        return ApiResponse.success(service.totalAccounts());
    }

    @GetMapping("/v2/total-accounts/{totalAccountNo}")
    public ApiResponse<DepositTotalAccountDetailDTO> totalAccountDetail(@PathVariable String totalAccountNo) {
        return ApiResponse.success(service.totalAccountDetail(totalAccountNo));
    }

    @GetMapping("/v2/migrations")
    public ApiResponse<List<DepositMigrationPreviewDTO>> migrationPreview() {
        return ApiResponse.success(service.migrationPreview());
    }
}
