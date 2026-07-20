package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;

/**
 * 支付交易异常中心 Service。
 */
public interface PaymentIssueService {

    /**
     * 查询支付交易异常列表。
     */
    PageResultDTO<PaymentIssueRowDTO> list(PaymentIssueQueryDTO query);
}
