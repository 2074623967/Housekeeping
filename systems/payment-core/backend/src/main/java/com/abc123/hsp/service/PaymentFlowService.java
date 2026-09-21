package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentFlowDetailDTO;
import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;

/**
 * 支付流水中心 Service，面向运营和测试排障提供过程查询。
 */
public interface PaymentFlowService {

    /**
     * 查询唯一支付过程流水详情。
     */
    PaymentFlowDetailDTO detail(String flowSource, String flowNo);

    /**
     * 查询支付流水列表。
     *
     * @return 支付流水列表
     */
    PageResultDTO<PaymentFlowListItemDTO> list(PaymentFlowQueryDTO query);

    /**
     * 导出支付流水列表。
     *
     * @return 支付流水 CSV
     */
    String exportCsv(PaymentFlowQueryDTO query);
}
