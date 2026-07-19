package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentRecordRowDTO;
import com.abc123.hsp.mapper.PaymentRecordMapper;
import com.abc123.hsp.service.PaymentRecordService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 收款记录业务实现。
 */
@Service
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentRecordServiceImpl(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public List<PaymentRecordRowDTO> list(String recordType) {
        return paymentRecordMapper.findAll(recordType);
    }
}
