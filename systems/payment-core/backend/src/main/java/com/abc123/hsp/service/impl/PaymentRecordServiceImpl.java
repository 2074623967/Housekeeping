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
import org.springframework.util.StringUtils;

/**
 * 收款记录业务实现。
 */
@Service
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private static final int RECORD_TYPE_MAX_LENGTH = 16;
    private static final int USER_ID_MAX_LENGTH = 128;
    private static final int BUSINESS_ORDER_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_TYPE_MAX_LENGTH = 32;
    private static final int PAYMENT_STATUS_MAX_LENGTH = 32;
    private static final int PAYMENT_CHANNEL_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;

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
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskRows(paymentRecordMapper.findAll(query)),
                paymentRecordMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    private void normalizeQuery(PaymentRecordQueryDTO query) {
        query.setRecordType(normalizeOptionalQueryField(
                query.getRecordType(), RECORD_TYPE_MAX_LENGTH, "记录维度", "ALL"));
        query.setUserId(normalizeOptionalQueryField(
                query.getUserId(), USER_ID_MAX_LENGTH, "用户ID", null));
        query.setBusinessOrderNo(normalizeOptionalQueryField(
                query.getBusinessOrderNo(), BUSINESS_ORDER_NO_MAX_LENGTH, "业务订单号", null));
        query.setPaymentType(normalizeOptionalQueryField(
                query.getPaymentType(), PAYMENT_TYPE_MAX_LENGTH, "支付类型", null));
        query.setPaymentStatus(normalizeOptionalQueryField(
                query.getPaymentStatus(), PAYMENT_STATUS_MAX_LENGTH, "支付状态", "全部"));
        query.setPaymentChannel(normalizeOptionalQueryField(
                query.getPaymentChannel(), PAYMENT_CHANNEL_MAX_LENGTH, "支付渠道", null));
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    @Override
    public PaymentRecordDetailDTO detail(String paymentOrderId) {
        String normalizedPaymentOrderId = normalizePaymentOrderId(paymentOrderId);
        PaymentRecordDetailDTO detail = paymentRecordMapper.findDetail(normalizedPaymentOrderId);
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
        outwardDetail.setRouteLogs(paymentMapper.findRouteLogs(normalizedPaymentOrderId));
        outwardDetail.setNotifyLogs(paymentMapper.findNotifyLogs(normalizedPaymentOrderId));
        outwardDetail.setEventLogs(paymentMapper.findEventItems(normalizedPaymentOrderId));
        return outwardDetail;
    }

    private String normalizeOptionalQueryField(
            String value, int maxLength, String fieldName, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        rejectControlCharacters(value, fieldName);
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizePaymentOrderId(String paymentOrderId) {
        if (!StringUtils.hasText(paymentOrderId)) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, "支付单号不能为空");
        }
        for (int index = 0; index < paymentOrderId.length(); index++) {
            if (Character.isISOControl(paymentOrderId.charAt(index))) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_REQUEST_INVALID, "支付单号不能包含控制字符");
            }
        }
        String normalized = paymentOrderId.trim();
        if (normalized.length() > PAYMENT_ORDER_ID_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_REQUEST_INVALID, "支付单号长度不能超过 64 个字符");
        }
        return normalized;
    }

    private void rejectControlCharacters(String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
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
