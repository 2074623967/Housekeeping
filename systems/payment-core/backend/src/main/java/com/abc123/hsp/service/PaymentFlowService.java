package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import java.util.List;

/**
 * 支付流水中心 Service，面向运营和测试排障提供过程查询。
 */
public interface PaymentFlowService {

    /**
     * 查询支付流水列表。
     *
     * @return 支付流水列表
     */
    List<PaymentFlowListItemDTO> list(PaymentFlowQueryDTO query);
}
