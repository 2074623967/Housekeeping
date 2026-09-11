package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundRetrySourceDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundChannelQueryRateLimitService;
import com.abc123.hsp.service.RefundChannelQueryService;
import com.abc123.hsp.service.RefundService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundServiceImpl.class);
    private static final String STATUS_REVIEWING = "REVIEWING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";
    private static final DateTimeFormatter REFUND_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String CALLBACK_OPERATOR = "channel-callback";
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int CALLBACK_CHANNEL_MAX_LENGTH = 64;
    private static final int REFUND_ORDER_ID_MAX_LENGTH = 64;
    private static final int CALLBACK_CHANNEL_REFUND_NO_MAX_LENGTH = 128;
    private static final int CALLBACK_REFUND_STATUS_MAX_LENGTH = 32;
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int REFUND_METHOD_MAX_LENGTH = 32;
    private static final int REFUND_STATUS_MAX_LENGTH = 32;
    private static final int REFUND_REASON_MAX_LENGTH = 255;
    private static final int OPERATION_REMARK_MAX_LENGTH = 255;

    private final RefundMapper refundMapper;
    private final PaymentEventDispatchService paymentEventDispatchService;
    private final PaymentCallbackSignatureService paymentCallbackSignatureService;
    private final RefundChannelQueryService refundChannelQueryService;
    private final RefundChannelQueryRateLimitService refundChannelQueryRateLimitService;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public RefundServiceImpl(RefundMapper refundMapper,
                             PaymentEventDispatchService paymentEventDispatchService,
                             PaymentCallbackSignatureService paymentCallbackSignatureService,
                             RefundChannelQueryService refundChannelQueryService,
                             RefundChannelQueryRateLimitService refundChannelQueryRateLimitService,
                             PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.refundMapper = refundMapper;
        this.paymentEventDispatchService = paymentEventDispatchService;
        this.paymentCallbackSignatureService = paymentCallbackSignatureService;
        this.refundChannelQueryService = refundChannelQueryService;
        this.refundChannelQueryRateLimitService = refundChannelQueryRateLimitService;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    RefundServiceImpl(RefundMapper refundMapper,
                      PaymentEventDispatchService paymentEventDispatchService,
                      PaymentCallbackSignatureService paymentCallbackSignatureService,
                      RefundChannelQueryService refundChannelQueryService) {
        this(refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService,
                (channelCode, refundOrderId) -> { },
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    RefundServiceImpl(RefundMapper refundMapper,
                      PaymentEventDispatchService paymentEventDispatchService,
                      PaymentCallbackSignatureService paymentCallbackSignatureService,
                      RefundChannelQueryService refundChannelQueryService,
                      RefundChannelQueryRateLimitService refundChannelQueryRateLimitService) {
        this(refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService,
                refundChannelQueryRateLimitService,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(refundMapper.findAll(query)),
                refundMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(RefundQueryDTO query) {
        normalizeQuery(query);
        List<RefundListItemDTO> items = maskItems(
                CsvExportRowLimiter.requireWithinLimit(refundMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("退款单号,支付单号,订单号,客户名称,退款金额,退款方式,退款状态,状态类型,申请时间,成功时间\n");
        for (RefundListItemDTO item : items) {
            builder.append(csvCell(item.getRefundOrderId())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getRefundAmount())).append(',')
                    .append(csvCell(item.getRefundMethod())).append(',')
                    .append(csvCell(item.getStatus())).append(',')
                    .append(csvCell(item.getStatusType())).append(',')
                    .append(csvCell(item.getAppliedAt())).append(',')
                    .append(csvCell(item.getSuccessAt())).append('\n');
        }
        return builder.toString();
    }

    @Override
    public RefundDetailDTO detail(String refundOrderId) {
        String requiredRefundOrderId = requireRefundOrderId(refundOrderId);
        RefundDetailDTO detail = refundMapper.findDetailByRefundOrderId(requiredRefundOrderId);
        if (detail == null) {
            throw new IllegalArgumentException("退款单不存在");
        }
        RefundDetailDTO outwardDetail = new RefundDetailDTO();
        BeanUtils.copyProperties(detail, outwardDetail);
        outwardDetail.setCustomerName(sensitiveDataMasker.maskName(detail.getCustomerName()));
        outwardDetail.setOperationLogs(refundMapper.findOperationLogs(requiredRefundOrderId));
        return outwardDetail;
    }

    private List<RefundListItemDTO> maskItems(List<RefundListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private RefundListItemDTO maskedCopy(RefundListItemDTO item) {
        if (item == null) {
            return null;
        }
        RefundListItemDTO outwardItem = new RefundListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return outwardItem;
    }

    @Override
    @Transactional
    public RefundListItemDTO apply(RefundApplyRequestDTO request) {
        String operator = normalizeOperator(request == null ? null : request.getOperator());
        validateApplyRequest(request);
        RefundPaymentSourceDTO source = refundMapper.findPaymentSourceForUpdate(request.getPaymentOrderId().trim());
        if (source == null) {
            throw new IllegalArgumentException("原支付单不存在");
        }
        if (!PAYMENT_STATUS_SUCCESS.equals(source.getStatus())) {
            throw new IllegalArgumentException("只有支付成功的支付单允许发起退款");
        }
        BigDecimal activeRefundAmount = refundMapper.sumActiveRefundAmount(source.getPaymentOrderId());
        BigDecimal refundedAmount = activeRefundAmount == null ? BigDecimal.ZERO : activeRefundAmount;
        if (refundedAmount.add(request.getRefundAmount()).compareTo(source.getPaidAmount()) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过原支付金额");
        }
        String refundOrderId = buildBusinessNo("REF");
        refundMapper.insertRefund(refundOrderId, source, request, STATUS_REVIEWING, "warn");
        refundMapper.insertOperationLog(buildOperationLog(
                refundOrderId,
                "APPLY",
                "发起退款申请",
                "INIT",
                STATUS_REVIEWING,
                operator,
                StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason().trim() : "发起退款申请"
        ));
        return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
    }

    @Override
    @Transactional
    public RefundListItemDTO approve(RefundActionRequestDTO request) {
        return changeStatus(request, STATUS_REVIEWING, STATUS_PROCESSING, "warn", false);
    }

    @Override
    @Transactional
    public RefundListItemDTO callback(String channel, RefundCallbackRequestDTO request) {
        String normalizedChannel = normalizeRefundCallback(channel, request);
        paymentCallbackSignatureService.verifyRefund(normalizedChannel, request);
        return applyChannelResult(
                request.getRefundOrderId(),
                normalizedChannel,
                request.getChannelRefundNo(),
                request.getRefundStatus(),
                CALLBACK_OPERATOR,
                false);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public RefundListItemDTO query(RefundStatusQueryRequestDTO request) {
        String operator = normalizeOperator(request == null ? null : request.getOperator());
        String refundOrderId = requireRefundOrderId(request == null ? null : request.getRefundOrderId());
        RefundCallbackSourceDTO snapshot = refundMapper.findCallbackSourceByRefundOrderId(refundOrderId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.REFUND_ORDER_NOT_FOUND, "退款单不存在");
        }
        if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAIL.equals(snapshot.getStatus())) {
            return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
        }
        validateQueryableRefund(snapshot);
        refundChannelQueryRateLimitService.acquire(snapshot.getChannelCode(), refundOrderId);
        RefundChannelQueryResultDTO result = queryChannel(snapshot, operator);
        return applyChannelResult(
                refundOrderId,
                snapshot.getChannelCode().trim().toLowerCase(Locale.ROOT),
                result.getChannelRefundNo(),
                result.getRefundStatus(),
                operator,
                true);
    }

    private RefundChannelQueryResultDTO queryChannel(RefundCallbackSourceDTO snapshot, String operator) {
        try {
            RefundChannelQueryResultDTO result = RefundChannelQueryResultValidator.validateAndNormalize(
                    refundChannelQueryService.query(snapshot));
            if (StringUtils.hasText(snapshot.getChannelRefundNo())
                    && !snapshot.getChannelRefundNo().equals(result.getChannelRefundNo())) {
                throw new BusinessException(ErrorCode.REFUND_CHANNEL_QUERY_FAILED,
                        "refund query transaction does not match accepted refund transaction");
            }
            return result;
        } catch (BusinessException exception) {
            recordChannelQueryFailure(snapshot, operator);
            throw exception;
        } catch (RuntimeException exception) {
            recordChannelQueryFailure(snapshot, operator);
            throw BusinessException.sanitized(ErrorCode.REFUND_CHANNEL_QUERY_FAILED,
                    "refund channel query failed unexpectedly");
        }
    }

    private void recordChannelQueryFailure(RefundCallbackSourceDTO snapshot, String operator) {
        refundMapper.insertOperationLog(buildOperationLog(
                snapshot.getRefundOrderId(),
                "CHANNEL_QUERY_FAILED",
                "退款渠道查单失败",
                snapshot.getStatus(),
                snapshot.getStatus(),
                operator,
                "退款渠道查单未获得可信结果"));
    }

    private RefundListItemDTO applyChannelResult(String refundOrderId,
                                                 String channel,
                                                 String channelRefundNo,
                                                 String targetStatus,
                                                 String operator,
                                                 boolean recordSameStatus) {
        RefundCallbackSourceDTO source = refundMapper.findCallbackSourceForUpdate(refundOrderId);
        if (source == null) {
            throw new BusinessException(ErrorCode.REFUND_ORDER_NOT_FOUND, "退款单不存在");
        }
        if (!StringUtils.hasText(source.getChannelCode())
                || !channel.equalsIgnoreCase(source.getChannelCode().trim())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_CHANNEL_MISMATCH,
                    "callback channel does not match refund channel");
        }
        boolean acceptanceRecovered = false;
        if (!StringUtils.hasText(source.getChannelRefundNo())) {
            acceptanceRecovered = recoverUnknownRefundAcceptance(source, channel, channelRefundNo, operator);
        } else if (!source.getChannelRefundNo().equals(channelRefundNo)) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH,
                    "callback transaction does not match refund transaction");
        }
        if (targetStatus.equals(source.getStatus())) {
            if (recordSameStatus) {
                refundMapper.insertOperationLog(buildOperationLog(
                        refundOrderId,
                        "CHANNEL_QUERY",
                        "退款渠道主动查单",
                        source.getStatus(),
                        source.getStatus(),
                        operator,
                        "渠道查询结果为" + targetStatus));
            }
            return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
        }
        if (recordSameStatus && STATUS_PROCESSING.equals(targetStatus)
                && (STATUS_SUCCESS.equals(source.getStatus()) || STATUS_FAIL.equals(source.getStatus()))) {
            return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
        }
        if (STATUS_SUCCESS.equals(source.getStatus()) || STATUS_FAIL.equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_FINAL_STATUS_CONFLICT,
                    "refund callback conflicts with finalized status");
        }
        if (!STATUS_PROCESSING.equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_STATUS_INVALID,
                    "refund status does not allow callback finalization");
        }
        boolean success = STATUS_SUCCESS.equals(targetStatus);
        String statusType = success ? "success" : "danger";
        int affectedRows = refundMapper.updateRefundStatus(
                refundOrderId, STATUS_PROCESSING, targetStatus, statusType, success);
        if (affectedRows == 0) {
            if (acceptanceRecovered) {
                throw new IllegalStateException(
                        "refund status update failed after unknown acceptance recovery");
            }
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_STATUS_INVALID,
                    "refund status does not allow callback finalization");
        }
        refundMapper.insertOperationLog(buildOperationLog(
                refundOrderId,
                recordSameStatus ? "CHANNEL_QUERY_" + targetStatus : targetStatus,
                recordSameStatus ? "退款渠道查单收口" : success ? "退款成功回调" : "退款失败回调",
                STATUS_PROCESSING,
                targetStatus,
                operator,
                recordSameStatus ? "渠道查单确认退款" + (success ? "成功" : "失败")
                        : success ? "渠道确认退款成功" : "渠道确认退款失败"));
        return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
    }

    private void validateQueryableRefund(RefundCallbackSourceDTO source) {
        if (!STATUS_PROCESSING.equals(source.getStatus())
                || !StringUtils.hasText(source.getChannelCode())
                || (!StringUtils.hasText(source.getChannelRefundNo())
                && !"UNKNOWN".equals(source.getSubmitEventStatus()))) {
            throw new BusinessException(ErrorCode.REFUND_CHANNEL_QUERY_FAILED,
                    "refund is not ready for channel query");
        }
    }

    private boolean recoverUnknownRefundAcceptance(RefundCallbackSourceDTO source,
                                                   String channel,
                                                   String channelRefundNo,
                                                   String operator) {
        if (!"UNKNOWN".equals(source.getSubmitEventStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH,
                    "callback transaction does not match refund transaction");
        }
        if (refundMapper.bindUnknownRefundChannelAccepted(
                source.getRefundOrderId(), channel, channelRefundNo) != 1
                || refundMapper.markUnknownRefundSubmitEventConfirmed(source.getRefundOrderId()) != 1) {
            throw new IllegalStateException("unknown refund acceptance recovery was not applied atomically");
        }
        refundMapper.insertOperationLog(buildOperationLog(
                source.getRefundOrderId(),
                "CHANNEL_ACCEPTANCE_RECOVERED",
                "退款渠道受理事实恢复",
                source.getStatus(),
                source.getStatus(),
                operator,
                "可信渠道核查已确认受理并收敛未知提交事件"));
        return true;
    }

    @Override
    @Transactional
    public RefundListItemDTO retry(RefundActionRequestDTO request) {
        String operator = normalizeOperator(request == null ? null : request.getOperator());
        validateRetryCapacity(requireRefundOrderId(request));
        return changeStatus(request, STATUS_FAIL, STATUS_PROCESSING, "warn", false,
                operator, "RETRY", "退款失败重试");
    }

    @Override
    @Transactional
    public void retryForTask(String refundOrderId, String triggeredBy) {
        String operator = normalizeOperator(triggeredBy);
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId(refundOrderId);
        request.setRemark("任务中心批量重试失败退款");
        validateRetryCapacity(requireRefundOrderId(refundOrderId));
        changeStatus(request,
                STATUS_FAIL,
                STATUS_PROCESSING,
                "warn",
                false,
                operator,
                "TASK_RETRY",
                "任务中心失败退款重试");
    }

    private void validateRetryCapacity(String refundOrderId) {
        RefundRetrySourceDTO retrySource = refundMapper.findRetrySourceForUpdate(refundOrderId);
        if (retrySource == null || !STATUS_FAIL.equals(retrySource.getStatus())) {
            throw new IllegalArgumentException("退款单状态不允许执行当前操作");
        }
        if (!StringUtils.hasText(retrySource.getPaymentOrderId())
                || retrySource.getRefundAmount() == null
                || retrySource.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款额度数据异常");
        }
        RefundPaymentSourceDTO paymentSource =
                refundMapper.findPaymentSourceForUpdate(retrySource.getPaymentOrderId());
        if (paymentSource == null) {
            throw new IllegalArgumentException("原支付单不存在");
        }
        if (paymentSource.getPaidAmount() == null
                || paymentSource.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("退款额度数据异常");
        }
        BigDecimal activeRefundAmount = refundMapper.sumActiveRefundAmount(retrySource.getPaymentOrderId());
        BigDecimal reservedAmount = activeRefundAmount == null ? BigDecimal.ZERO : activeRefundAmount;
        if (reservedAmount.add(retrySource.getRefundAmount()).compareTo(paymentSource.getPaidAmount()) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过原支付金额");
        }
    }

    /**
     * 列表查询统一收敛分页边界，避免后台误传 pageSize 造成大查询。
     */
    private void normalizeQuery(RefundQueryDTO query) {
        query.setRefundOrderId(normalizeOptionalRequestField(
                query.getRefundOrderId(), "退款单号", REFUND_ORDER_ID_MAX_LENGTH));
        query.setPaymentOrderId(normalizeOptionalRequestField(
                query.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        String refundStatus = normalizeOptionalRequestField(
                query.getRefundStatus(), "退款状态", REFUND_STATUS_MAX_LENGTH);
        query.setRefundStatus(refundStatus == null ? "全部" : refundStatus);
        String refundMethod = normalizeOptionalRequestField(
                query.getRefundMethod(), "退款方式", REFUND_METHOD_MAX_LENGTH);
        query.setRefundMethod(refundMethod == null ? "全部" : refundMethod);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    /**
     * 校验退款申请的业务必填项和金额边界。
     */
    private void validateApplyRequest(RefundApplyRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("原支付单号不能为空");
        }
        request.setPaymentOrderId(normalizeRequiredRequestField(
                request.getPaymentOrderId(), "原支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款金额必须大于0");
        }
        BigDecimal normalizedAmount = request.getRefundAmount().stripTrailingZeros();
        if (normalizedAmount.scale() > MONEY_MAX_SCALE
                || request.getRefundAmount().compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new IllegalArgumentException("退款金额最多支持 16 位整数和 2 位小数");
        }
        String refundMethod = normalizeOptionalRequestField(
                request.getRefundMethod(), "退款方式", REFUND_METHOD_MAX_LENGTH);
        request.setRefundMethod(refundMethod == null ? "原路退款" : refundMethod);
        String refundReason = normalizeOptionalRequestField(
                request.getRefundReason(), "退款原因", REFUND_REASON_MAX_LENGTH);
        request.setRefundReason(refundReason == null ? "" : refundReason);
    }

    private String normalizeRequiredRequestField(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalRequestField(value, fieldName, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalRequestField(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "字符");
        }
        return normalized;
    }

    private String normalizeRefundCallback(String channel, RefundCallbackRequestDTO request) {
        if (!StringUtils.hasText(channel)
                || request == null
                || !StringUtils.hasText(request.getRefundOrderId())
                || !StringUtils.hasText(request.getChannelRefundNo())
                || !StringUtils.hasText(request.getRefundStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_REQUEST_INVALID,
                    "channel, refundOrderId, channelRefundNo and refundStatus are required");
        }
        String normalizedChannel = channel.trim().toLowerCase(Locale.ROOT);
        request.setRefundOrderId(request.getRefundOrderId().trim());
        request.setChannelRefundNo(request.getChannelRefundNo().trim());
        request.setRefundStatus(request.getRefundStatus().trim().toUpperCase(Locale.ROOT));
        validateCallbackField(normalizedChannel, CALLBACK_CHANNEL_MAX_LENGTH);
        validateCallbackField(request.getRefundOrderId(), REFUND_ORDER_ID_MAX_LENGTH);
        validateCallbackField(request.getChannelRefundNo(), CALLBACK_CHANNEL_REFUND_NO_MAX_LENGTH);
        validateCallbackField(request.getRefundStatus(), CALLBACK_REFUND_STATUS_MAX_LENGTH);
        if (!STATUS_SUCCESS.equals(request.getRefundStatus()) && !STATUS_FAIL.equals(request.getRefundStatus())) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_STATUS_INVALID,
                    "refund callback status must be SUCCESS or FAIL");
        }
        return normalizedChannel;
    }

    private void validateCallbackField(String value, int maxLength) {
        if (value.length() > maxLength || containsIsoControl(value)) {
            throw new BusinessException(ErrorCode.REFUND_CALLBACK_REQUEST_INVALID,
                    "refund callback field is invalid");
        }
    }

    private boolean containsIsoControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 退款状态流转使用 compare-and-set 更新，避免重复点击导致状态回退。
     */
    private RefundListItemDTO changeStatus(RefundActionRequestDTO request,
                                           String fromStatus,
                                           String status,
                                           String statusType,
                                           boolean success) {
        return changeStatus(request, fromStatus, status, statusType, success,
                normalizeOperator(request == null ? null : request.getOperator()),
                resolveActionCode(fromStatus, status), resolveActionName(fromStatus, status));
    }

    private RefundListItemDTO changeStatus(RefundActionRequestDTO request,
                                           String fromStatus,
                                           String status,
                                           String statusType,
                                           boolean success,
                                           String operator,
                                           String actionCode,
                                           String actionName) {
        String refundOrderId = requireRefundOrderId(request);
        String remark = normalizeOptionalRequestField(
                request.getRemark(), "操作备注", OPERATION_REMARK_MAX_LENGTH);
        if (remark == null) {
            remark = resolveDefaultRemark(fromStatus, status);
        }
        int affectedRows = refundMapper.updateRefundStatus(refundOrderId, fromStatus, status, statusType, success);
        if (affectedRows == 0) {
            throw new IllegalArgumentException("退款单状态不允许执行当前操作");
        }
        refundMapper.insertOperationLog(buildOperationLog(
                refundOrderId,
                actionCode,
                actionName,
                fromStatus,
                status,
                operator,
                remark
        ));
        if (STATUS_PROCESSING.equals(status)) {
            enqueueChannelSubmit(refundOrderId, operator);
        }
        return maskedCopy(refundMapper.findByRefundOrderId(refundOrderId));
    }

    private void enqueueChannelSubmit(String refundOrderId, String operator) {
        RefundChannelSubmitRequestDTO channelSubmitRequest = refundMapper.findChannelSubmitRequestByRefundOrderId(refundOrderId);
        if (channelSubmitRequest == null) {
            throw new IllegalArgumentException("退款单不存在或缺少渠道下发信息");
        }
        String eventNo = buildRefundSubmitEventNo(refundOrderId);
        refundMapper.upsertRefundSubmitEvent(eventNo, refundOrderId, channelSubmitRequest.getPaymentOrderId());
        dispatchAfterCommit(eventNo, refundOrderId, operator);
    }

    private void dispatchAfterCommit(String eventNo, String refundOrderId, String operator) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tryDispatch(eventNo, refundOrderId, operator);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryDispatch(eventNo, refundOrderId, operator);
            }
        });
    }

    private void tryDispatch(String eventNo, String refundOrderId, String operator) {
        try {
            paymentEventDispatchService.publishRefundSubmit(eventNo, refundOrderId, operator);
        } catch (RuntimeException exception) {
            LOGGER.warn("退款渠道事件即时派发失败 eventNo={}, exceptionType={}",
                    eventNo, exception.getClass().getName());
        }
    }

    private String buildRefundSubmitEventNo(String refundOrderId) {
        return "EVTR" + UUID.nameUUIDFromBytes(
                ("REFUND_SUBMIT:" + refundOrderId).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private String requireRefundOrderId(RefundActionRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("退款单号不能为空");
        }
        String refundOrderId = requireRefundOrderId(request.getRefundOrderId());
        request.setRefundOrderId(refundOrderId);
        return refundOrderId;
    }

    public static RefundOperationLogEntity buildOperationLog(String refundOrderId,
                                                             String actionCode,
                                                             String actionName,
                                                             String fromStatus,
                                                             String toStatus,
                                                             String operatorName,
                                                             String operationRemark) {
        RefundOperationLogEntity entity = new RefundOperationLogEntity();
        entity.setLogNo(buildBusinessNo("ROL"));
        entity.setRefundOrderId(refundOrderId);
        entity.setActionCode(actionCode);
        entity.setActionName(actionName);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setOperatorName(operatorName);
        entity.setOperationRemark(operationRemark);
        return entity;
    }

    private static String buildBusinessNo(String prefix) {
        return prefix
                + LocalDateTime.now().format(REFUND_NO_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String requireRefundOrderId(String refundOrderId) {
        return normalizeRequiredRequestField(
                refundOrderId, "退款单号", REFUND_ORDER_ID_MAX_LENGTH);
    }

    private String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        for (int index = 0; index < operator.length(); index++) {
            if (Character.isISOControl(operator.charAt(index))) {
                throw new IllegalArgumentException("操作人不能包含控制字符");
            }
        }
        String normalized = operator.trim();
        if (normalized.length() > OPERATOR_MAX_LENGTH) {
            throw new IllegalArgumentException("操作人长度不能超过64字符");
        }
        return normalized;
    }

    private String resolveActionCode(String fromStatus, String toStatus) {
        if (STATUS_REVIEWING.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "APPROVE";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_SUCCESS.equals(toStatus)) {
            return "SUCCESS";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_FAIL.equals(toStatus)) {
            return "FAIL";
        }
        if (STATUS_FAIL.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "RETRY";
        }
        return "UPDATE";
    }

    private String resolveActionName(String fromStatus, String toStatus) {
        if (STATUS_REVIEWING.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "审核通过";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_SUCCESS.equals(toStatus)) {
            return "退款成功回调";
        }
        if (STATUS_PROCESSING.equals(fromStatus) && STATUS_FAIL.equals(toStatus)) {
            return "退款失败回调";
        }
        if (STATUS_FAIL.equals(fromStatus) && STATUS_PROCESSING.equals(toStatus)) {
            return "退款失败重试";
        }
        return "退款状态更新";
    }

    private String resolveDefaultRemark(String fromStatus, String toStatus) {
        return resolveActionName(fromStatus, toStatus);
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
