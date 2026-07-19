package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import java.util.List;

/**
 * 支付流水中心 Mapper，负责聚合主链路中的多类过程流水。
 */
public interface PaymentFlowMapper {

    /**
     * 查询支付流水列表。
     *
     * @return 支付流水列表
     */
    List<PaymentFlowListItemDTO> findAll(PaymentFlowQueryDTO query);
}
