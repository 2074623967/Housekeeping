package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import java.util.List;

/**
 * 支付请求 Service，面向联调和排障提供支付报文查询。
 */
public interface PaymentRequestService {

    /**
     * 查询支付请求列表。
     *
     * @return 支付请求列表
     */
    List<PaymentRequestListItemDTO> list();
}
