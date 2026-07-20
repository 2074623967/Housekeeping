package com.abc123.clearing.service;

import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.ShareItemDTO;

/**
 * 分账明细服务。
 */
public interface ShareService {

    PageResultDTO<ShareItemDTO> list(String clearingNo, String shareType, int pageNo, int pageSize);
}
