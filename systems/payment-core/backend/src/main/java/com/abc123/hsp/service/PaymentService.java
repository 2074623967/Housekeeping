package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.repository.PaymentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public List<PaymentListItemDTO> list() {
        return paymentRepository.findAll();
    }
}
