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
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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

    @Override
    public String exportCsv(PaymentRecordQueryDTO query) {
        normalizeQuery(query);
        List<PaymentRecordRowDTO> rows = maskRows(CsvExportRowLimiter.requireWithinLimit(
                paymentRecordMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("序号,支付单号,业务订单号,支付请求号,应用来源ID,业务线ID,外部交易流水号,")
                .append("支付通道,支付渠道,支付类型,支付状态,银行名,卡号,渠道返回码,返参类型,")
                .append("参数值,有效期,支付金额,退款次数,已退金额,商品名称,用户支付渠道唯一标识,")
                .append("收款账号,通知地址,回调MQ主题,失效时间,创建时间,更新时间,支付成功时间,用户ID\n");
        for (PaymentRecordRowDTO row : rows) {
            builder.append(csvCell(row.getSerialNo())).append(',')
                    .append(csvCell(row.getPaymentOrderId())).append(',')
                    .append(csvCell(row.getBusinessOrderNo())).append(',')
                    .append(csvCell(row.getPaymentRequestNo())).append(',')
                    .append(csvCell(row.getApplicationSourceId())).append(',')
                    .append(csvCell(row.getBusinessLineId())).append(',')
                    .append(csvCell(row.getExternalTransactionNo())).append(',')
                    .append(csvCell(row.getPaymentGateway())).append(',')
                    .append(csvCell(row.getPaymentChannel())).append(',')
                    .append(csvCell(row.getPaymentType())).append(',')
                    .append(csvCell(row.getPaymentStatus())).append(',')
                    .append(csvCell(row.getBankName())).append(',')
                    .append(csvCell(row.getCardNo())).append(',')
                    .append(csvCell(row.getChannelReturnCode())).append(',')
                    .append(csvCell(row.getReturnParameterType())).append(',')
                    .append(csvCell(row.getParameterValue())).append(',')
                    .append(csvCell(row.getValidityPeriod())).append(',')
                    .append(csvCell(row.getPaymentAmount())).append(',')
                    .append(csvCell(row.getRefundCount())).append(',')
                    .append(csvCell(row.getRefundedAmount())).append(',')
                    .append(csvCell(row.getProductName())).append(',')
                    .append(csvCell(row.getUserPaymentChannelId())).append(',')
                    .append(csvCell(row.getReceivingAccount())).append(',')
                    .append(csvCell(row.getNotifyUrl())).append(',')
                    .append(csvCell(row.getCallbackMqTopic())).append(',')
                    .append(csvCell(row.getExpireTime())).append(',')
                    .append(csvCell(row.getCreatedAt())).append(',')
                    .append(csvCell(row.getUpdatedAt())).append(',')
                    .append(csvCell(row.getPaidAt())).append(',')
                    .append(csvCell(row.getUserId())).append('\n');
        }
        return builder.toString();
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
        outwardDetail.setRouteLogs(safeList(paymentMapper.findRouteLogs(normalizedPaymentOrderId)));
        outwardDetail.setNotifyLogs(safeList(paymentMapper.findNotifyLogs(normalizedPaymentOrderId)));
        outwardDetail.setEventLogs(safeList(paymentMapper.findEventItems(normalizedPaymentOrderId)));
        return outwardDetail;
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
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
        return rows.stream()
                .filter(row -> row != null)
                .map(this::maskedRowCopy)
                .collect(Collectors.toList());
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

    private String csvCell(Object value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(
                value == null ? "" : String.valueOf(value)).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
