package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import java.util.List;

public interface RefundMapper {

    List<RefundListItemDTO> findAll(RefundQueryDTO query);
}
