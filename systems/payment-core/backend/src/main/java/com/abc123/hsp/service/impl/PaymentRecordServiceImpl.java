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
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 收款记录业务实现。
 */
@Service
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public PaymentRecordServiceImpl(PaymentRecordMapper paymentRecordMapper,
                                    PaymentMapper paymentMapper,
                                    PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentMapper = paymentMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    PaymentRecordServiceImpl(PaymentRecordMapper paymentRecordMapper, PaymentMapper paymentMapper) {
        this(paymentRecordMapper, paymentMapper,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
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
                maskRows(paymentRecordMapper.findAll(query)),
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
        PaymentRecordDetailDTO outwardDetail = new PaymentRecordDetailDTO();
        BeanUtils.copyProperties(detail, outwardDetail);
        maskRecordFields(outwardDetail);
        outwardDetail.setLatestClientIp(sensitiveDataMasker.maskClientIp(detail.getLatestClientIp()));
        outwardDetail.setLatestIdempotencyKey(
                sensitiveDataMasker.maskIdempotencyKey(detail.getLatestIdempotencyKey()));
        outwardDetail.setLatestRequestPayload(sensitiveDataMasker.maskPayload(detail.getLatestRequestPayload()));
        outwardDetail.setLatestResponsePayload(sensitiveDataMasker.maskPayload(detail.getLatestResponsePayload()));
        outwardDetail.setRouteLogs(paymentMapper.findRouteLogs(paymentOrderId));
        outwardDetail.setNotifyLogs(paymentMapper.findNotifyLogs(paymentOrderId));
        outwardDetail.setEventLogs(paymentMapper.findEventItems(paymentOrderId));
        return outwardDetail;
    }

    private List<PaymentRecordRowDTO> maskRows(List<PaymentRecordRowDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::maskedRowCopy).collect(Collectors.toList());
    }

    private PaymentRecordRowDTO maskedRowCopy(PaymentRecordRowDTO row) {
        if (row == null) {
            return null;
        }
        PaymentRecordRowDTO outwardRow = new PaymentRecordRowDTO();
        BeanUtils.copyProperties(row, outwardRow);
        maskRecordFields(outwardRow);
        return outwardRow;
    }

    private void maskRecordFields(PaymentRecordRowDTO row) {
        row.setUserId(sensitiveDataMasker.maskUserReference(row.getUserId()));
        row.setUserPaymentChannelId(
                sensitiveDataMasker.maskUserReference(row.getUserPaymentChannelId()));
    }
}
