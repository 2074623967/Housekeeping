package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.repository.RefundRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RefundService {

    private final RefundRepository refundRepository;

    public RefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    public List<RefundListItemDTO> list() {
        return refundRepository.findAll();
    }
}
