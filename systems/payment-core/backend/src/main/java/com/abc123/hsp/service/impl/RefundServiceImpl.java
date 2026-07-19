package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;

    public RefundServiceImpl(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    @Override
    public List<RefundListItemDTO> list() {
        return refundMapper.findAll();
    }
}
