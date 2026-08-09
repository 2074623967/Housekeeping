package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;

/**
 * 支付请求 Service，面向联调和排障提供支付报文查询。
 */
public interface PaymentRequestService {

    /**
     * 查询支付请求列表。
     *
     * @return 支付请求列表
     */
    PageResultDTO<PaymentRequestListItemDTO> list(PaymentRequestQueryDTO query);

    /**
     * 查询支付请求总览，供运营筛选缩圈和复盘使用。
     */
    PaymentRequestOverviewDTO overview(PaymentRequestQueryDTO query);

    /**
     * 导出支付请求列表，供联调、排障和留痕使用。
     *
     * @return CSV 文本内容
     */
    String exportCsv(PaymentRequestQueryDTO query);
}
