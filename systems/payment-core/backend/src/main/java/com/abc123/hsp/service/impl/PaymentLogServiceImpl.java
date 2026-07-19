package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import com.abc123.hsp.service.PaymentLogService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付处理日志业务实现。
 */
@Service
public class PaymentLogServiceImpl implements PaymentLogService {

    private final PaymentLogMapper paymentLogMapper;

    public PaymentLogServiceImpl(PaymentLogMapper paymentLogMapper) {
        this.paymentLogMapper = paymentLogMapper;
    }

    @Override
    public List<PaymentLogListItemDTO> list() {
        return paymentLogMapper.findAll();
    }
}
