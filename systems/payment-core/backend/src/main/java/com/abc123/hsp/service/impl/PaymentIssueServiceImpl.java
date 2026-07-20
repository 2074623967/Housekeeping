package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.mapper.PaymentIssueMapper;
import com.abc123.hsp.service.PaymentIssueService;
import org.springframework.stereotype.Service;

/**
 * 支付交易异常中心业务实现。
 */
@Service
public class PaymentIssueServiceImpl implements PaymentIssueService {

    private final PaymentIssueMapper paymentIssueMapper;

    public PaymentIssueServiceImpl(PaymentIssueMapper paymentIssueMapper) {
        this.paymentIssueMapper = paymentIssueMapper;
    }

    @Override
    public PageResultDTO<PaymentIssueRowDTO> list(PaymentIssueQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setIssueType(query.getIssueType() == null ? "全部" : query.getIssueType().trim());
        query.setSeverity(query.getSeverity() == null ? "全部" : query.getSeverity().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setPaymentMethod(query.getPaymentMethod() == null ? "全部" : query.getPaymentMethod().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentIssueMapper.findAll(query),
                paymentIssueMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
