package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementOverviewDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.service.SettlementService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * 查询服务者结算概览。
     *
     * @param settlementOrderId 结算单号
     * @param workerKeyword 服务者关键字
     * @param settlementStatus 结算状态
     * @param payoutStatus 打款状态
     * @return 结算概览
     */
    @GetMapping("/workers/overview")
    public ApiResponse<WorkerSettlementOverviewDTO> workerOverview(
            @RequestParam(required = false) String settlementOrderId,
            @RequestParam(required = false) String workerKeyword,
            @RequestParam(defaultValue = "全部") String settlementStatus,
            @RequestParam(defaultValue = "全部") String payoutStatus) {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(settlementOrderId);
        query.setWorkerKeyword(workerKeyword);
        query.setSettlementStatus(settlementStatus);
        query.setPayoutStatus(payoutStatus);
        return ApiResponse.success(settlementService.workerOverview(query));
    }

    /**
     * 查询服务者结算分页列表。
     *
     * @param settlementOrderId 结算单号
     * @param workerKeyword 服务者关键字
     * @param settlementStatus 结算状态
     * @param payoutStatus 打款状态
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 结算分页结果
     */
    @GetMapping("/workers")
    public ApiResponse<PageResultDTO<WorkerSettlementListItemDTO>> workerList(
            @RequestParam(required = false) String settlementOrderId,
            @RequestParam(required = false) String workerKeyword,
            @RequestParam(defaultValue = "全部") String settlementStatus,
            @RequestParam(defaultValue = "全部") String payoutStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(settlementOrderId);
        query.setWorkerKeyword(workerKeyword);
        query.setSettlementStatus(settlementStatus);
        query.setPayoutStatus(payoutStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(settlementService.workerList(query));
    }

    /**
     * 导出服务者结算列表。
     *
     * @param settlementOrderId 结算单号
     * @param workerKeyword 服务者关键字
     * @param settlementStatus 结算状态
     * @param payoutStatus 打款状态
     * @return CSV 文件流
     */
    @GetMapping("/workers/export")
    public ResponseEntity<byte[]> exportWorkerList(
            @RequestParam(required = false) String settlementOrderId,
            @RequestParam(required = false) String workerKeyword,
            @RequestParam(defaultValue = "全部") String settlementStatus,
            @RequestParam(defaultValue = "全部") String payoutStatus) {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(settlementOrderId);
        query.setWorkerKeyword(workerKeyword);
        query.setSettlementStatus(settlementStatus);
        query.setPayoutStatus(payoutStatus);
        byte[] csvBytes = settlementService.exportCsv(query).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=worker-settlements.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }
}
