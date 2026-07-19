package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import com.abc123.hsp.service.PaymentFlowService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付流水中心业务实现，仅承接支付过程追踪，不承接账务会计处理。
 */
@Service
public class PaymentFlowServiceImpl implements PaymentFlowService {

    private final PaymentFlowMapper paymentFlowMapper;

    public PaymentFlowServiceImpl(PaymentFlowMapper paymentFlowMapper) {
        this.paymentFlowMapper = paymentFlowMapper;
    }

    @Override
    public List<PaymentFlowListItemDTO> list() {
        return paymentFlowMapper.findAll();
    }
}
