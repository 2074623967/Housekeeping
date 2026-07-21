package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.StringJoiner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentCallbackSignatureService paymentCallbackSignatureService;
    private final PaymentChannelRoutingService paymentChannelRoutingService;
    private final PaymentChannelQueryService paymentChannelQueryService;
    private final PaymentChannelSubmitService paymentChannelSubmitService;

    public PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService) {
        this.paymentMapper = paymentMapper;
        this.paymentCallbackSignatureService = paymentCallbackSignatureService;
        this.paymentChannelRoutingService = paymentChannelRoutingService;
        this.paymentChannelQueryService = paymentChannelQueryService;
        this.paymentChannelSubmitService = paymentChannelSubmitService;
    }

    @Override
    public PageResultDTO<PaymentListItemDTO> list(PaymentListQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                paymentMapper.findAll(query),
                paymentMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentDetailDTO detail(String paymentOrderId) {
        return enrichDetail(paymentMapper.findDetail(paymentOrderId));
    }

    @Transactional
    @Override
    public PrepayOrderDTO prepay(PrepayRequestDTO request) {
        // 预付单创建会同时影响账单、支付单和收银台状态，必须放在同一个事务里。
        PrepayOrderDTO activePrepay = paymentMapper.findLatestActivePrepayByOrderNo(request.getOrderNo());
        if (activePrepay != null) {
            // 同一订单在收银台有效期内重复拉起时，直接复用已有预付单，避免支付单和预付单被拆成多份。
            return activePrepay;
        }
        BigDecimal orderAmount = paymentMapper.findOrderAmount(request.getOrderNo());
        BigDecimal paidAmount = paymentMapper.findPaidAmount(request.getOrderNo());
        if (orderAmount == null || paidAmount == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "订单金额或已付金额不存在");
        }
        String customerName = paymentMapper.findCustomerNameByOrderNo(request.getOrderNo());
        String billNo = paymentMapper.findBillNoByOrderNo(request.getOrderNo());
        BigDecimal remainAmount = orderAmount.subtract(paidAmount);
        if (billNo == null) {
            billNo = "BILL" + System.currentTimeMillis();
            String billStatus = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "待支付";
            String billStatusType = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "success" : "warn";
            paymentMapper.insertBill(billNo, request.getOrderNo(), customerName, orderAmount, paidAmount, billStatus, billStatusType);
        }
        // 每次拉起收银台都生成独立支付单，避免多个预付单复用同一支付单造成状态串单。
        String paymentOrderId = "PAY" + System.currentTimeMillis();
        paymentMapper.insertPaymentOrder(
                paymentOrderId,
                request.getOrderNo(),
                customerName,
                remainAmount,
                request.getPayScene()
        );
        String prepayOrderNo = "PRE" + System.currentTimeMillis();
        paymentMapper.insertPrepayOrder(
                prepayOrderNo,
                billNo,
                request.getOrderNo(),
                customerName,
                remainAmount,
                request.getPayScene(),
                "家政服务收银台",
                paymentOrderId
        );
        return paymentMapper.findPrepay(prepayOrderNo);
    }

    @Override
    public CashierPageDTO cashier(String prepayOrderNo) {
        PrepayOrderDTO prepayOrder = paymentMapper.findPrepay(prepayOrderNo);
        if (prepayOrder == null) {
            throw new BusinessException(ErrorCode.PREPAY_ORDER_NOT_FOUND, "预付单不存在");
        }
        CashierPageDTO cashierPage = new CashierPageDTO();
        cashierPage.setPrepayOrderNo(prepayOrder.getPrepayOrderNo());
        cashierPage.setOrderNo(prepayOrder.getOrderNo());
        cashierPage.setBillNo(prepayOrder.getBillNo());
        cashierPage.setCustomerName(prepayOrder.getCustomerName());
        cashierPage.setAmount(prepayOrder.getAmount());
        cashierPage.setPayScene(prepayOrder.getPayScene());
        cashierPage.setTitle(prepayOrder.getCashierTitle());
        cashierPage.setStatus(prepayOrder.getCashierStatus());
        cashierPage.setStatusType(prepayOrder.getCashierStatusType());
        cashierPage.setExpiresAt(prepayOrder.getExpiresAt());
        cashierPage.setChannels(Arrays.asList("微信支付", "支付宝", "银行卡"));
        return cashierPage;
    }

    @Transactional
    @Override
    public PrepayOrderDTO submit(PaymentSubmitRequestDTO request) {
        PrepayOrderDTO currentPrepay = paymentMapper.findPrepay(request.getPrepayOrderNo());
        if (currentPrepay == null || !StringUtils.hasText(currentPrepay.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.PREPAY_ORDER_NOT_FOUND, "预付单不存在或未绑定支付单");
        }
        String paymentOrderId = currentPrepay.getPaymentOrderId();
        PaymentDetailDTO currentDetail = paymentMapper.findDetail(paymentOrderId);
        if (currentDetail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if ("WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())
                || "SUCCESS".equalsIgnoreCase(currentDetail.getStatus())
                || "CLOSED".equalsIgnoreCase(currentDetail.getStatus())) {
            // 收银台重复点击提交按钮时直接复用当前支付状态，避免重复写支付尝试、路由和待回调日志。
            return currentPrepay;
        }
        String terminal = StringUtils.hasText(request.getTerminal()) ? request.getTerminal().trim() : "UNKNOWN";
        String clientIp = StringUtils.hasText(request.getClientIp()) ? request.getClientIp().trim() : "UNKNOWN";
        PaymentRouteDecisionDTO routeDecision = paymentChannelRoutingService.resolve(
                buildRouteContext(request, currentPrepay, terminal));
        String resolvedChannelCode = routeDecision.getChannelCode();
        String idempotencyKey = buildIdempotencyKey(request, currentPrepay, resolvedChannelCode);
        if (paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
            // 相同幂等键的提交已经落库时，直接返回当前预付单，避免重复下发支付尝试。
            return currentPrepay;
        }
        int occupiedRows = paymentMapper.updatePrepayToPaying(request.getPrepayOrderNo());
        if (occupiedRows == 0) {
            PrepayOrderDTO latestPrepay = paymentMapper.findPrepay(request.getPrepayOrderNo());
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(paymentOrderId);
            if (latestDetail != null && ("WAIT_CALLBACK".equalsIgnoreCase(latestDetail.getStatus())
                    || "SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    || "CLOSED".equalsIgnoreCase(latestDetail.getStatus()))) {
                return latestPrepay == null ? currentPrepay : latestPrepay;
            }
            if (latestPrepay != null && "支付中".equals(latestPrepay.getCashierStatus())) {
                return latestPrepay;
            }
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, "支付提交处理中，请稍后刷新结果");
        }
        PaymentChannelSubmitResultDTO submitResult = paymentChannelSubmitService.submit(
                buildSubmitAdapterRequest(
                        request,
                        currentPrepay,
                        paymentOrderId,
                        resolvedChannelCode,
                        terminal,
                        clientIp,
                        idempotencyKey));
        // 收银台提交后，补齐支付单上的支付方式和渠道。
        paymentMapper.updatePaymentMethodAndChannel(
                paymentOrderId,
                request.getPaymentMethod(),
                resolvedChannelCode,
                submitResult.getChannelTransactionNo());
        String routeNo = "RTR" + System.currentTimeMillis();
        paymentMapper.insertRouteRecord(
                routeNo,
                paymentOrderId,
                resolvedChannelCode,
                routeDecision.getRouteRule(),
                routeDecision.getRouteResult());
        String attemptNo = "ATT" + System.currentTimeMillis();
        paymentMapper.insertPaymentAttempt(
                attemptNo,
                request.getPrepayOrderNo(),
                paymentOrderId,
                resolvedChannelCode,
                request.getPaymentMethod(),
                terminal,
                clientIp,
                idempotencyKey,
                buildSubmitRequestPayload(request, terminal, clientIp, idempotencyKey, resolvedChannelCode),
                submitResult.getResponsePayload(),
                submitResult.getAttemptStatus(),
                submitResult.getAttemptStatusType()
        );
        paymentMapper.insertEvent(
                "EVT" + System.currentTimeMillis(),
                "PAYMENT_SUBMIT",
                paymentOrderId,
                paymentMapper.findOrderNoByPrepayOrderNo(request.getPrepayOrderNo()),
                "{\"channel\":\"" + resolvedChannelCode + "\",\"paymentMethod\":\"" + request.getPaymentMethod() + "\"}"
        );
        paymentMapper.insertNotifyLog(
                "NTF" + System.currentTimeMillis(),
                paymentOrderId,
                resolvedChannelCode,
                "SUBMIT",
                "{\"method\":\"" + request.getPaymentMethod() + "\"}",
                submitResult.getResponsePayload(),
                "待回调",
                "warn"
        );
        return paymentMapper.findPrepay(request.getPrepayOrderNo());
    }

    @Transactional
    @Override
    public PaymentDetailDTO callback(String channel, PaymentCallbackRequestDTO request) {
        if (!StringUtils.hasText(request.getPaymentOrderId())
                || !StringUtils.hasText(request.getTradeStatus())
                || !StringUtils.hasText(request.getChannelTransactionNo())) {
            throw new IllegalArgumentException(
                    "paymentOrderId, tradeStatus and channelTransactionNo are required");
        }
        paymentCallbackSignatureService.verify(channel, request);
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        // 已经成功或关闭的支付单不允许被迟到回调重新打开，保证回调幂等和状态单向收敛。
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())
                || "CLOSED".equalsIgnoreCase(detail.getStatus())) {
            return enrichDetail(detail);
        }
        boolean paySuccess = "SUCCESS".equalsIgnoreCase(request.getTradeStatus());
        // 回调必须落日志、改状态、写事件，三件事一起做才方便对账和排障。
        paymentMapper.insertNotifyLog(
                "NTF" + System.currentTimeMillis(),
                request.getPaymentOrderId(),
                channel,
                request.getTradeStatus(),
                "{\"tradeStatus\":\"" + request.getTradeStatus() + "\"}",
                "{\"code\":\"SUCCESS\"}",
                "已收口",
                "success"
        );
        paymentMapper.updatePaymentStatus(
                request.getPaymentOrderId(),
                paySuccess ? "SUCCESS" : "WAIT_CALLBACK",
                paySuccess ? "success" : "warn",
                request.getChannelTransactionNo()
        );
        paymentMapper.updatePaymentAttemptStatusByPaymentOrderId(
                request.getPaymentOrderId(),
                paySuccess ? "成功" : "待回调",
                paySuccess ? "success" : "warn"
        );
        paymentMapper.updatePrepayStatusByPaymentOrderId(
                request.getPaymentOrderId(),
                paySuccess ? "支付成功" : "待回调",
                paySuccess ? "success" : "warn"
        );
        if (paySuccess) {
            BigDecimal latestOrderAmount = paymentMapper.findOrderAmount(detail.getOrderNo());
            paymentMapper.updateOrderAfterPayment(detail.getOrderNo(), latestOrderAmount, "待履约", "info");
            paymentMapper.updateBillAfterPayment(detail.getOrderNo(), latestOrderAmount, "已结清", "success");
        }
        paymentMapper.insertEvent(
                "EVT" + System.currentTimeMillis(),
                paySuccess ? "PAYMENT_SUCCESS" : "PAYMENT_PENDING",
                request.getPaymentOrderId(),
                detail.getOrderNo(),
                "{\"channel\":\"" + channel + "\"}"
        );
        return enrichDetail(paymentMapper.findDetail(request.getPaymentOrderId()));
    }

    @Override
    public PaymentDetailDTO query(PaymentQueryRequestDTO request) {
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        return enrichDetail(paymentChannelQueryService.query(detail));
    }

    @Transactional
    @Override
    public PaymentDetailDTO close(PaymentCloseRequestDTO request) {
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus()) || "CLOSED".equalsIgnoreCase(detail.getStatus())) {
            return enrichDetail(detail);
        }
        // 关闭支付单时同步记录事件，后续可以从事件轨迹里看到是谁、什么时候关闭的。
        paymentMapper.updatePaymentStatus(request.getPaymentOrderId(), "CLOSED", "danger", detail.getChannelTransactionNo());
        paymentMapper.updatePaymentAttemptStatusByPaymentOrderId(request.getPaymentOrderId(), "已关闭", "danger");
        paymentMapper.updatePrepayStatusByPaymentOrderId(request.getPaymentOrderId(), "已关闭", "danger");
        paymentMapper.insertEvent("EVT" + System.currentTimeMillis(), "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo(), "{\"close\":\"manual\"}");
        return enrichDetail(paymentMapper.findDetail(request.getPaymentOrderId()));
    }

    /**
     * 幂等键优先使用前端显式透传值，未透传时退化为预付单+支付方式+渠道的组合键。
     */
    private String buildIdempotencyKey(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String resolvedChannelCode) {
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            return request.getIdempotencyKey().trim();
        }
        return currentPrepay.getPrepayOrderNo() + "|" + request.getPaymentMethod() + "|" + resolvedChannelCode;
    }

    /**
     * 提交支付前统一收口路由上下文，避免路由规则读取到的口径在不同入口下不一致。
     */
    private PaymentRouteContextDTO buildRouteContext(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String terminal) {
        PaymentRouteContextDTO routeContext = new PaymentRouteContextDTO();
        routeContext.setPaymentMethod(request.getPaymentMethod());
        routeContext.setRequestedChannelCode(request.getChannelCode());
        routeContext.setPayScene(currentPrepay.getPayScene());
        routeContext.setTerminal(terminal);
        routeContext.setAmount(parseAmount(currentPrepay.getAmount()));
        routeContext.setCustomerName(currentPrepay.getCustomerName());
        return routeContext;
    }

    /**
     * 渠道适配器只接收标准化上下文，避免后续接入真实微信/支付宝时还要反向依赖页面请求对象。
     */
    private PaymentChannelSubmitRequestDTO buildSubmitAdapterRequest(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String paymentOrderId,
            String resolvedChannelCode,
            String terminal,
            String clientIp,
            String idempotencyKey) {
        PaymentChannelSubmitRequestDTO submitRequest = new PaymentChannelSubmitRequestDTO();
        submitRequest.setPaymentOrderId(paymentOrderId);
        submitRequest.setPrepayOrderNo(currentPrepay.getPrepayOrderNo());
        submitRequest.setOrderNo(currentPrepay.getOrderNo());
        submitRequest.setPayScene(currentPrepay.getPayScene());
        submitRequest.setCustomerName(currentPrepay.getCustomerName());
        submitRequest.setAmount(parseAmount(currentPrepay.getAmount()));
        submitRequest.setPaymentMethod(request.getPaymentMethod());
        submitRequest.setRequestedChannelCode(request.getChannelCode());
        submitRequest.setResolvedChannelCode(resolvedChannelCode);
        submitRequest.setTerminal(terminal);
        submitRequest.setClientIp(clientIp);
        submitRequest.setIdempotencyKey(idempotencyKey);
        return submitRequest;
    }

    /**
     * 统一请求留痕口径，便于在支付请求管理页直接复盘一次发起请求。
     */
    private String buildSubmitRequestPayload(
            PaymentSubmitRequestDTO request,
            String terminal,
            String clientIp,
            String idempotencyKey,
            String resolvedChannelCode) {
        return new StringJoiner(",", "{", "}")
                .add("\"method\":\"" + request.getPaymentMethod() + "\"")
                .add("\"channelCode\":\"" + request.getChannelCode() + "\"")
                .add("\"resolvedChannelCode\":\"" + resolvedChannelCode + "\"")
                .add("\"terminal\":\"" + terminal + "\"")
                .add("\"clientIp\":\"" + clientIp + "\"")
                .add("\"idempotencyKey\":\"" + idempotencyKey + "\"")
                .toString();
    }

    /**
     * 预付单金额字段当前为展示口径，路由规则比较前统一还原成数值。
     */
    private BigDecimal parseAmount(String amountText) {
        if (!StringUtils.hasText(amountText)) {
            return BigDecimal.ZERO;
        }
        String normalizedAmount = amountText
                .replace("¥", "")
                .replace(",", "")
                .trim();
        return new BigDecimal(normalizedAmount);
    }

    private PaymentDetailDTO enrichDetail(PaymentDetailDTO detail) {
        if (detail == null) {
            return null;
        }
        detail.setRouteLogs(paymentMapper.findRouteLogs(detail.getPaymentOrderId()));
        detail.setNotifyLogs(paymentMapper.findNotifyLogs(detail.getPaymentOrderId()));
        detail.setEventLogs(paymentMapper.findEventItems(detail.getPaymentOrderId()));
        return detail;
    }

    /**
     * 支付单列表统一在服务层收敛分页边界，避免全量拉取影响后台排障性能。
     */
    private void normalizeQuery(PaymentListQueryDTO query) {
        query.setPaymentOrderId(StringUtils.hasText(query.getPaymentOrderId()) ? query.getPaymentOrderId().trim() : null);
        query.setOrderNo(StringUtils.hasText(query.getOrderNo()) ? query.getOrderNo().trim() : null);
        query.setPaymentMethod(StringUtils.hasText(query.getPaymentMethod()) ? query.getPaymentMethod().trim() : "全部");
        query.setStatus(StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : "全部");
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }
}
