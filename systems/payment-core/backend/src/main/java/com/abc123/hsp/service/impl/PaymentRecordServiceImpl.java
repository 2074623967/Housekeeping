package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import com.abc123.hsp.mapper.PaymentMapper;
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
    private final PaymentMapper paymentMapper;

    public PaymentRecordServiceImpl(PaymentRecordMapper paymentRecordMapper, PaymentMapper paymentMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public PageResultDTO<PaymentRecordRowDTO> list(PaymentRecordQueryDTO query) {
        query.setRecordType(query.getRecordType() == null ? "ALL" : query.getRecordType().trim());
        query.setUserId(query.getUserId() == null ? null : query.getUserId().trim());
        query.setBusinessOrderNo(query.getBusinessOrderNo() == null ? null : query.getBusinessOrderNo().trim());
        query.setPaymentType(query.getPaymentType() == null ? null : query.getPaymentType().trim());
        query.setPaymentStatus(query.getPaymentStatus() == null ? "全部" : query.getPaymentStatus().trim());
        query.setPaymentChannel(query.getPaymentChannel() == null ? null : query.getPaymentChannel().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentRecordMapper.findAll(query),
                paymentRecordMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentRecordDetailDTO detail(String paymentOrderId) {
        PaymentRecordDetailDTO detail = paymentRecordMapper.findDetail(paymentOrderId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        detail.setRouteLogs(paymentMapper.findRouteLogs(paymentOrderId));
        detail.setNotifyLogs(paymentMapper.findNotifyLogs(paymentOrderId));
        detail.setEventLogs(paymentMapper.findEventItems(paymentOrderId));
        return detail;
    }
}
