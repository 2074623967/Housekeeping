package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;

public interface RefundService {

    PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query);
}
