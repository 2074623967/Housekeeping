package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import java.util.List;

public interface RefundService {

    List<RefundListItemDTO> list(RefundQueryDTO query);
}
