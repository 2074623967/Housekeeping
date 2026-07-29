package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.service.ShareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分账明细控制器。
 */
@RestController
@RequestMapping("/api/clearing/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<ShareItemDTO>> list(
            @RequestParam(required = false) String clearingNo,
            @RequestParam(required = false) String shareType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(shareService.list(clearingNo, shareType, pageNo, pageSize));
    }
}
