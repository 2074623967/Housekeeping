package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.RefundListItemDTO;
import java.util.List;

public interface RefundMapper {

    List<RefundListItemDTO> findAll();
}
