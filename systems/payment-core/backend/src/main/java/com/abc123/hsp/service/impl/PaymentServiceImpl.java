package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitConcurrencyTokenDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelQueryRateLimitService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.PaymentOpsConfigService;
import com.abc123.hsp.service.PaymentRiskControlService;
import com.abc123.hsp.service.PaymentSubmitRateLimitService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final int BUSINESS_ORDER_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int PREPAY_ORDER_NO_MAX_LENGTH = 64;
    private static final int PREPAY_PAY_SCENE_MAX_LENGTH = 32;
    private static final int PREPAY_CUSTOMER_NAME_MAX_LENGTH = 128;
    private static final int PREPAY_CASHIER_TITLE_MAX_LENGTH = 128;
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");

    private static final int SUBMIT_IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final int SUBMIT_PREPAY_ORDER_NO_MAX_LENGTH = 64;
    private static final int SUBMIT_PAYMENT_METHOD_MAX_LENGTH = 32;
    private static final int SUBMIT_CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int SUBMIT_SOURCE_APP_ID_MAX_LENGTH = 64;
    private static final int SUBMIT_MERCHANT_NO_MAX_LENGTH = 128;
    private static final int SUBMIT_ACCESS_TOKEN_MAX_LENGTH = 128;
    private static final int SUBMIT_CLIENT_IP_MAX_LENGTH = 64;
    private static final int SUBMIT_CLIENT_DEVICE_ID_MAX_LENGTH = 128;
    private static final int SUBMIT_PAYER_PHONE_MAX_LENGTH = 32;

    private static final int CALLBACK_PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int CALLBACK_TRADE_STATUS_MAX_LENGTH = 32;
    private static final int CALLBACK_CHANNEL_TRANSACTION_NO_MAX_LENGTH = 128;
    private static final int CALLBACK_CHANNEL_MAX_LENGTH = 64;

    private static final int SUBMIT_CONCURRENCY_TOKEN_EXPIRE_SECONDS = 120;

    private final PaymentMapper paymentMapper;
    private final PaymentCallbackSignatureService paymentCallbackSignatureService;
    private final PaymentChannelRoutingService paymentChannelRoutingService;
    private final PaymentChannelQueryService paymentChannelQueryService;
    private final PaymentChannelSubmitService paymentChannelSubmitService;
    private final PaymentEventDispatchService paymentEventDispatchService;
    private final PaymentRiskControlService paymentRiskControlService;
    private final PaymentOpsConfigService paymentOpsConfigService;
    private final PaymentChannelQueryRateLimitService paymentChannelQueryRateLimitService;
    private PaymentSubmitRateLimitService paymentSubmitRateLimitService;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;
    private final TransactionTemplate submitTransactionTemplate;

    @Autowired
    public PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService,
            PaymentEventDispatchService paymentEventDispatchService,
            PaymentRiskControlService paymentRiskControlService,
            PaymentOpsConfigService paymentOpsConfigService,
            PaymentChannelQueryRateLimitService paymentChannelQueryRateLimitService,
            PaymentSensitiveDataMasker sensitiveDataMasker,
            PlatformTransactionManager transactionManager) {
        this.paymentMapper = paymentMapper;
        this.paymentCallbackSignatureService = paymentCallbackSignatureService;
        this.paymentChannelRoutingService = paymentChannelRoutingService;
        this.paymentChannelQueryService = paymentChannelQueryService;
        this.paymentChannelSubmitService = paymentChannelSubmitService;
        this.paymentEventDispatchService = paymentEventDispatchService;
        this.paymentRiskControlService = paymentRiskControlService;
        this.paymentOpsConfigService = paymentOpsConfigService;
        this.paymentChannelQueryRateLimitService = paymentChannelQueryRateLimitService;
        this.paymentSubmitRateLimitService = (sourceAppId, paymentMethod, terminal, clientIp,
                                              interfaceMinuteLimit, sourceMethodMinuteLimit) -> {
            // 单元测试兼容构造器默认不启用共享提交限流。
        };
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.submitTransactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
        if (this.submitTransactionTemplate != null) {
            this.submitTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    @Autowired
    void setPaymentSubmitRateLimitService(PaymentSubmitRateLimitService paymentSubmitRateLimitService) {
        this.paymentSubmitRateLimitService = paymentSubmitRateLimitService;
    }

    PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService,
            PaymentEventDispatchService paymentEventDispatchService,
            PaymentRiskControlService paymentRiskControlService,
            PaymentOpsConfigService paymentOpsConfigService,
            PaymentChannelQueryRateLimitService paymentChannelQueryRateLimitService,
            PaymentSensitiveDataMasker sensitiveDataMasker) {
        this(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                paymentChannelQueryRateLimitService,
                sensitiveDataMasker,
                null);
    }

    PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService,
            PaymentEventDispatchService paymentEventDispatchService,
            PaymentRiskControlService paymentRiskControlService,
            PaymentOpsConfigService paymentOpsConfigService,
            PaymentChannelQueryRateLimitService paymentChannelQueryRateLimitService) {
        this(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                paymentChannelQueryRateLimitService,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService,
            PaymentEventDispatchService paymentEventDispatchService,
            PaymentRiskControlService paymentRiskControlService,
            PaymentOpsConfigService paymentOpsConfigService) {
        this(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                (channelCode, paymentOrderId) -> {
                    // 单元测试兼容构造器默认不启用共享查单限流。
                });
    }

    PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService) {
        this(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                new PaymentEventDispatchService() {
                    @Override
                    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
                        // 单元测试兼容构造器默认不触发下游联动。
                    }

                    @Override
                    public boolean republish(String eventNo) {
                        return false;
                    }
                },
                new PaymentRiskControlService() {
                    @Override
                    public PaymentRiskDecisionResultDTO evaluateSubmitRisk(PaymentRiskDecisionRequestDTO request) {
                        PaymentRiskDecisionResultDTO result = new PaymentRiskDecisionResultDTO();
                        result.setDecision("PASS");
                        result.setDecisionType("success");
                        result.setMessage("单元测试兼容构造器默认放行");
                        return result;
                    }
                },
                new PaymentOpsConfigService() {
                    @Override
                    public PaymentOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType) {
                        return null;
                    }
                });
    }

    PaymentServiceImpl(
            PaymentMapper paymentMapper,
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentChannelRoutingService paymentChannelRoutingService,
            PaymentChannelQueryService paymentChannelQueryService,
            PaymentChannelSubmitService paymentChannelSubmitService,
            PaymentEventDispatchService paymentEventDispatchService) {
        this(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                new PaymentRiskControlService() {
                    @Override
                    public PaymentRiskDecisionResultDTO evaluateSubmitRisk(PaymentRiskDecisionRequestDTO request) {
                        PaymentRiskDecisionResultDTO result = new PaymentRiskDecisionResultDTO();
                        result.setDecision("PASS");
                        result.setDecisionType("success");
                        result.setMessage("未注入独立风控服务，默认放行");
                        return result;
                    }
                },
                new PaymentOpsConfigService() {
                    @Override
                    public PaymentOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType) {
                        return null;
                    }
                });
    }

    @Override
    public PageResultDTO<PaymentListItemDTO> list(PaymentListQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskPaymentItems(paymentMapper.findAll(query)),
                paymentMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(PaymentListQueryDTO query) {
        normalizeQuery(query);
        List<PaymentListItemDTO> items = maskPaymentItems(paymentMapper.findAllForExport(query));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("支付单号,订单号,客户名称,支付金额,支付方式,支付渠道,渠道交易号,支付状态,创建时间\n");
        for (PaymentListItemDTO item : items) {
            builder.append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getAmount())).append(',')
                    .append(csvCell(item.getPaymentMethod())).append(',')
                    .append(csvCell(item.getChannel())).append(',')
                    .append(csvCell(item.getChannelTransactionNo())).append(',')
                    .append(csvCell(item.getStatus())).append(',')
                    .append(csvCell(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    @Override
    public PaymentDetailDTO detail(String paymentOrderId) {
        String normalizedPaymentOrderId = normalizeRequiredRequestField(
                paymentOrderId, "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH);
        return outwardDetail(paymentMapper.findDetail(normalizedPaymentOrderId));
    }

    @Transactional
    @Override
    public PrepayOrderDTO prepay(PrepayRequestDTO request) {
        // 预付单创建会同时影响账单、支付单和收银台状态，必须放在同一个事务里。
        normalizeAndValidatePrepayRequest(request);
        paymentMapper.initPrepayCreationLock(request.getOrderNo());
        String lockedOrderNo = paymentMapper.lockPrepayCreation(request.getOrderNo());
        if (!request.getOrderNo().equals(lockedOrderNo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "预付单创建并发锁获取失败");
        }
        PrepayOrderDTO activePrepay = paymentMapper.findLatestActivePrepayByOrderNo(request.getOrderNo());
        if (activePrepay != null) {
            validateActivePrepayRequest(activePrepay, request);
            // 同一订单在收银台有效期内重复拉起时，直接复用已有预付单，避免支付单和预付单被拆成多份。
            return activePrepay;
        }
        BigDecimal orderAmount = paymentMapper.findOrderAmount(request.getOrderNo());
        BigDecimal paidAmount = paymentMapper.findPaidAmount(request.getOrderNo());
        if (orderAmount == null && paidAmount == null) {
            return createVirtualPrepay(request);
        }
        if (orderAmount == null || paidAmount == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                    "订单金额数据不完整");
        }
        BigDecimal remainAmount = orderAmount.subtract(paidAmount);
        if (remainAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ORDER_ALREADY_SETTLED,
                    "订单已结清或已超额支付，无需再次创建支付单");
        }
        String customerName = paymentMapper.findCustomerNameByOrderNo(request.getOrderNo());
        String billNo = paymentMapper.findBillNoByOrderNo(request.getOrderNo());
        if (billNo == null) {
            billNo = buildBusinessNo("BILL");
            String billStatus = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "待支付";
            String billStatusType = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "success" : "warn";
            paymentMapper.insertBill(billNo, request.getOrderNo(), customerName, orderAmount, paidAmount, billStatus, billStatusType);
        }
        // 每次拉起收银台都生成独立支付单，避免多个预付单复用同一支付单造成状态串单。
        String paymentOrderId = buildBusinessNo("PAY");
        paymentMapper.insertPaymentOrder(
                paymentOrderId,
                request.getOrderNo(),
                customerName,
                remainAmount,
                request.getPayScene()
        );
        String prepayOrderNo = buildBusinessNo("PRE");
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

    /**
     * 钱包充值、转账补款等非标准订单场景在当前阶段通过虚拟单方式接入统一收银台。
     */
    private PrepayOrderDTO createVirtualPrepay(PrepayRequestDTO request) {
        if (!StringUtils.hasText(request.getOrderNo())
                || !StringUtils.hasText(request.getCustomerName())
                || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "虚拟支付场景缺少订单号、客户名称或金额");
        }
        String customerName = request.getCustomerName().trim();
        String cashierTitle = StringUtils.hasText(request.getCashierTitle())
                ? request.getCashierTitle().trim()
                : "家政服务收银台";
        String billNo = buildBusinessNo("BILL");
        paymentMapper.insertBill(
                billNo,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                BigDecimal.ZERO,
                "待支付",
                "warn");
        String paymentOrderId = buildBusinessNo("PAY");
        paymentMapper.insertPaymentOrder(
                paymentOrderId,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                request.getPayScene()
        );
        String prepayOrderNo = buildBusinessNo("PRE");
        paymentMapper.insertPrepayOrder(
                prepayOrderNo,
                billNo,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                request.getPayScene(),
                cashierTitle,
                paymentOrderId
        );
        return paymentMapper.findPrepay(prepayOrderNo);
    }

    @Override
    public CashierPageDTO cashier(String prepayOrderNo) {
        String normalizedPrepayOrderNo = normalizeRequiredRequestField(
                prepayOrderNo, "预付单号", PREPAY_ORDER_NO_MAX_LENGTH);
        PrepayOrderDTO prepayOrder = paymentMapper.findPrepay(normalizedPrepayOrderNo);
        if (prepayOrder == null) {
            throw new BusinessException(ErrorCode.PREPAY_ORDER_NOT_FOUND, "预付单不存在");
        }
        CashierPageDTO cashierPage = new CashierPageDTO();
        cashierPage.setPaymentOrderId(prepayOrder.getPaymentOrderId());
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

    @Override
    public PrepayOrderDTO submit(PaymentSubmitRequestDTO request) {
        normalizeAndValidateSubmitRequest(request);
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
                || "CLOSED".equalsIgnoreCase(currentDetail.getStatus())
                || "RISK_BLOCKED".equalsIgnoreCase(currentDetail.getStatus())
                || "RISK_REJECTED".equalsIgnoreCase(currentDetail.getStatus())) {
            // 收银台重复点击提交按钮时直接复用当前支付状态，避免重复写支付尝试、路由和待回调日志。
            return currentPrepay;
        }
        String terminal = request.getTerminal();
        String clientIp = request.getClientIp();
        String sourceAppId = resolveSourceAppId(request);
        Long opsConfigSnapshotVersion = applyOpsConfigDefaults(request, currentPrepay, terminal);
        normalizeAndValidateResolvedSubmitFields(request);
        PaymentRouteDecisionDTO routeDecision = paymentChannelRoutingService.resolve(
                buildRouteContext(request, currentPrepay, terminal));
        String resolvedChannelCode = routeDecision.getChannelCode();
        validateRequiredSubmitField(
                resolvedChannelCode,
                "支付路由渠道",
                SUBMIT_CHANNEL_CODE_MAX_LENGTH);
        String idempotencyKey = buildIdempotencyKey(request, currentPrepay, resolvedChannelCode);
        validateSubmitIdempotencyKey(idempotencyKey);
        boolean existingAttempt = paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey);
        validateSubmitControlPolicy(
                sourceAppId,
                request.getMerchantNo(),
                request.getAccessToken(),
                request.getPaymentMethod(),
                resolvedChannelCode,
                terminal,
                clientIp,
                existingAttempt);
        PaymentRiskDecisionResultDTO riskDecision = paymentRiskControlService.evaluateSubmitRisk(
                buildRiskDecisionRequest(
                        request,
                        currentPrepay,
                        paymentOrderId,
                        resolvedChannelCode,
                        terminal,
                        clientIp));
        if (!"PASS".equalsIgnoreCase(riskDecision.getDecision())) {
            return executeSubmitTransaction(() -> handleRiskDecision(
                        request,
                        currentPrepay,
                        currentDetail,
                        resolvedChannelCode,
                        riskDecision));
        }
        if (existingAttempt || paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
            // 相同幂等键的提交已经落库时，直接返回当前预付单，避免重复下发支付尝试。
            return currentPrepay;
        }
        String orderNo = paymentMapper.findOrderNoByPrepayOrderNo(request.getPrepayOrderNo());
        String routeNo = buildBusinessNo("RTR");
        String attemptNo = buildBusinessNo("ATT");
        String requestPayload = buildSubmitRequestPayload(
                request, sourceAppId, terminal, clientIp, idempotencyKey, resolvedChannelCode);
        PrepayOrderDTO concurrentResult = executeSubmitTransaction(() -> prepareSubmit(
                request,
                currentPrepay,
                paymentOrderId,
                sourceAppId,
                terminal,
                clientIp,
                idempotencyKey,
                routeDecision,
                orderNo,
                routeNo,
                attemptNo,
                requestPayload,
                opsConfigSnapshotVersion));
        if (concurrentResult != null) {
            return concurrentResult;
        }
        PaymentChannelSubmitResultDTO submitResult = null;
        try {
            submitResult = paymentChannelSubmitService.submit(
                    buildSubmitAdapterRequest(
                            request,
                            currentPrepay,
                            paymentOrderId,
                            sourceAppId,
                            resolvedChannelCode,
                            terminal,
                            clientIp,
                            idempotencyKey));
            PaymentChannelSubmitResultDTO finalSubmitResult = submitResult;
            return executeSubmitTransaction(() -> completeSubmit(
                    request,
                    paymentOrderId,
                    resolvedChannelCode,
                    orderNo,
                    attemptNo,
                    finalSubmitResult));
        } catch (RuntimeException exception) {
            PaymentChannelSubmitResultDTO uncertainResult = submitResult;
            try {
                executeSubmitTransaction(() -> {
                    markSubmitResultUnknown(paymentOrderId, attemptNo, uncertainResult);
                    return null;
                });
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            throw exception;
        }
    }

    private PrepayOrderDTO prepareSubmit(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String paymentOrderId,
            String sourceAppId,
            String terminal,
            String clientIp,
            String idempotencyKey,
            PaymentRouteDecisionDTO routeDecision,
            String orderNo,
            String routeNo,
            String attemptNo,
            String requestPayload,
            Long opsConfigSnapshotVersion) {
        if (paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
            return currentPrepay;
        }
        occupySubmitConcurrencyToken(
                currentPrepay.getPrepayOrderNo(),
                paymentOrderId,
                sourceAppId,
                terminal,
                clientIp,
                idempotencyKey);
        int occupiedRows = paymentMapper.updatePrepayToPaying(request.getPrepayOrderNo());
        if (occupiedRows == 0) {
            paymentMapper.releaseSubmitConcurrencyToken(paymentOrderId, "PREPAY_ALREADY_PAYING");
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
        String resolvedChannelCode = routeDecision.getChannelCode();
        paymentMapper.updatePaymentMethodAndChannel(
                paymentOrderId, request.getPaymentMethod(), resolvedChannelCode, null);
        paymentMapper.insertRouteRecord(
                routeNo,
                paymentOrderId,
                resolvedChannelCode,
                routeDecision.getRouteRule(),
                routeDecision.getRouteResult());
        recordProcessLog(
                routeNo,
                paymentOrderId,
                orderNo,
                attemptNo,
                "PAYMENT_ROUTE",
                "支付路由",
                "info",
                "路由规则=" + routeDecision.getRouteRule() + "；路由结果=" + routeDecision.getRouteResult(),
                resolvedChannelCode);
        paymentMapper.insertPaymentAttempt(
                attemptNo,
                request.getPrepayOrderNo(),
                paymentOrderId,
                resolvedChannelCode,
                request.getPaymentMethod(),
                sourceAppId,
                terminal,
                clientIp,
                idempotencyKey,
                requestPayload,
                null,
                "渠道提交中",
                "warn");
        recordProcessLog(
                attemptNo,
                paymentOrderId,
                orderNo,
                attemptNo,
                "PAYMENT_ATTEMPT",
                "支付提交",
                "warn",
                "支付方式=" + request.getPaymentMethod() + "；请求状态=渠道提交中",
                resolvedChannelCode);
        if (opsConfigSnapshotVersion != null) {
            paymentMapper.updateRouteOpsConfigSnapshotVersion(routeNo, opsConfigSnapshotVersion);
            paymentMapper.updateAttemptOpsConfigSnapshotVersion(attemptNo, opsConfigSnapshotVersion);
        }
        return null;
    }

    private PrepayOrderDTO completeSubmit(
            PaymentSubmitRequestDTO request,
            String paymentOrderId,
            String resolvedChannelCode,
            String orderNo,
            String attemptNo,
            PaymentChannelSubmitResultDTO submitResult) {
        paymentMapper.updatePaymentMethodAndChannel(
                paymentOrderId,
                request.getPaymentMethod(),
                resolvedChannelCode,
                submitResult.getChannelTransactionNo());
        paymentMapper.updatePaymentReturnCodeSnapshot(
                paymentOrderId,
                submitResult.getChannelReturnCode(),
                submitResult.getStandardizedCode(),
                submitResult.getStandardizedMessage(),
                submitResult.getHandlingSuggestion(),
                submitResult.getRetryable(),
                submitResult.getManualInterventionRequired(),
                submitResult.getMappingVersion(),
                submitResult.getReturnCodeMappingMatched());
        paymentMapper.updatePaymentAttemptSubmitResult(
                attemptNo,
                submitResult.getResponsePayload(),
                submitResult.getAttemptStatus(),
                submitResult.getAttemptStatusType());
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"channel\":\"" + escapeJson(resolvedChannelCode)
                + "\",\"paymentMethod\":\"" + escapeJson(request.getPaymentMethod()) + "\"}";
        paymentMapper.insertEvent(
                eventNo,
                "PAYMENT_SUBMIT",
                paymentOrderId,
                orderNo,
                eventPayload);
        recordEventProcessLog(eventNo, "PAYMENT_SUBMIT", paymentOrderId, orderNo);
        String notifyNo = buildBusinessNo("NTF");
        paymentMapper.insertNotifyLog(
                notifyNo,
                paymentOrderId,
                resolvedChannelCode,
                "SUBMIT",
                "{\"method\":\"" + escapeJson(request.getPaymentMethod()) + "\"}",
                submitResult.getResponsePayload(),
                "待回调",
                "warn");
        recordNotifyProcessLog(
                notifyNo,
                paymentOrderId,
                orderNo,
                attemptNo,
                resolvedChannelCode,
                "SUBMIT",
                "待回调",
                "warn");
        return paymentMapper.findPrepay(request.getPrepayOrderNo());
    }

    private void markSubmitResultUnknown(
            String paymentOrderId,
            String attemptNo,
            PaymentChannelSubmitResultDTO submitResult) {
        String responsePayload = submitResult == null ? null : submitResult.getResponsePayload();
        paymentMapper.updatePaymentAttemptSubmitResult(
                attemptNo, responsePayload, "结果未知，待查单", "danger");
        paymentMapper.updatePrepayStatusByPaymentOrderId(paymentOrderId, "结果待确认", "danger");
        if (submitResult != null && StringUtils.hasText(submitResult.getChannelTransactionNo())) {
            paymentMapper.updatePaymentStatus(
                    paymentOrderId,
                    "WAIT_CALLBACK",
                    "danger",
                    submitResult.getChannelTransactionNo());
        }
    }

    private <T> T executeSubmitTransaction(Supplier<T> action) {
        if (submitTransactionTemplate == null) {
            return action.get();
        }
        return submitTransactionTemplate.execute(status -> action.get());
    }

    /**
     * 风控命中后先在支付核心域留痕，再返回收银台当前态，避免继续进入渠道提交流程。
     */
    private PrepayOrderDTO handleRiskDecision(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            PaymentDetailDTO currentDetail,
            String resolvedChannelCode,
            PaymentRiskDecisionResultDTO riskDecision) {
        paymentMapper.updatePaymentMethodAndChannel(
                currentDetail.getPaymentOrderId(),
                request.getPaymentMethod(),
                resolvedChannelCode,
                currentDetail.getChannelTransactionNo());
        String eventType;
        String paymentStatus;
        String prepayStatus;
        String statusType;
        if ("REVIEW".equalsIgnoreCase(riskDecision.getDecision())) {
            eventType = "PAYMENT_RISK_REVIEW";
            paymentStatus = "RISK_REVIEW";
            prepayStatus = "待风控复核";
            statusType = "warn";
        } else if ("REJECT".equalsIgnoreCase(riskDecision.getDecision())) {
            eventType = "PAYMENT_RISK_REJECTED";
            paymentStatus = "RISK_REJECTED";
            prepayStatus = "风控拒绝";
            statusType = "danger";
        } else {
            eventType = "PAYMENT_RISK_BLOCKED";
            paymentStatus = "RISK_BLOCKED";
            prepayStatus = "风控拦截";
            statusType = "danger";
        }
        paymentMapper.updatePaymentStatus(
                currentDetail.getPaymentOrderId(),
                paymentStatus,
                statusType,
                currentDetail.getChannelTransactionNo());
        paymentMapper.updatePrepayStatusByPaymentOrderId(
                currentDetail.getPaymentOrderId(),
                prepayStatus,
                statusType);
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = buildRiskDecisionPayload(riskDecision, resolvedChannelCode);
        paymentMapper.insertEvent(
                eventNo,
                eventType,
                currentDetail.getPaymentOrderId(),
                currentDetail.getOrderNo(),
                eventPayload);
        recordEventProcessLog(
                eventNo,
                eventType,
                currentDetail.getPaymentOrderId(),
                currentDetail.getOrderNo());
        return paymentMapper.findPrepay(currentPrepay.getPrepayOrderNo());
    }

    @Transactional
    @Override
    public PaymentDetailDTO callback(String channel, PaymentCallbackRequestDTO request) {
        if (!StringUtils.hasText(channel)
                || request == null
                || !StringUtils.hasText(request.getPaymentOrderId())
                || !StringUtils.hasText(request.getTradeStatus())
                || !StringUtils.hasText(request.getChannelTransactionNo())) {
            throw new IllegalArgumentException(
                    "channel, paymentOrderId, tradeStatus and channelTransactionNo are required");
        }
        String normalizedChannel = channel.trim().toLowerCase(Locale.ROOT);
        validateCallbackField(normalizedChannel, CALLBACK_CHANNEL_MAX_LENGTH, "channel");
        validateCallbackField(request.getPaymentOrderId(), CALLBACK_PAYMENT_ORDER_ID_MAX_LENGTH, "paymentOrderId");
        validateCallbackField(request.getTradeStatus(), CALLBACK_TRADE_STATUS_MAX_LENGTH, "tradeStatus");
        validateCallbackField(
                request.getChannelTransactionNo(),
                CALLBACK_CHANNEL_TRANSACTION_NO_MAX_LENGTH,
                "channelTransactionNo");
        paymentCallbackSignatureService.verify(normalizedChannel, request);
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if (!StringUtils.hasText(detail.getChannel())
                || !normalizedChannel.equalsIgnoreCase(detail.getChannel().trim())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CALLBACK_CHANNEL_MISMATCH,
                    "callback channel does not match payment channel");
        }
        if (StringUtils.hasText(detail.getChannelTransactionNo())
                && !detail.getChannelTransactionNo().equals(request.getChannelTransactionNo())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH,
                    "callback transaction does not match payment transaction");
        }
        boolean paySuccess = "SUCCESS".equalsIgnoreCase(request.getTradeStatus());
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())
                || ("CLOSED".equalsIgnoreCase(detail.getStatus()) && !paySuccess)) {
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_ALREADY_FINALIZED");
            return outwardDetail(detail);
        }
        if (paySuccess && paymentMapper.markPaymentSuccessFromChannelResult(
                request.getPaymentOrderId(), request.getChannelTransactionNo()) == 0) {
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_ALREADY_FINALIZED");
            return outwardDetail(paymentMapper.findDetail(request.getPaymentOrderId()));
        }
        // 回调必须落日志、改状态、写事件，三件事一起做才方便对账和排障。
        String notifyNo = buildBusinessNo("NTF");
        paymentMapper.insertNotifyLog(
                notifyNo,
                request.getPaymentOrderId(),
                normalizedChannel,
                request.getTradeStatus(),
                "{\"tradeStatus\":\"" + escapeJson(request.getTradeStatus()) + "\"}",
                "{\"code\":\"SUCCESS\"}",
                "已收口",
                "success"
        );
        recordNotifyProcessLog(
                notifyNo,
                request.getPaymentOrderId(),
                detail.getOrderNo(),
                null,
                normalizedChannel,
                request.getTradeStatus(),
                "已收口",
                "success");
        if (!paySuccess) {
            paymentMapper.updatePaymentStatus(
                    request.getPaymentOrderId(),
                    "WAIT_CALLBACK",
                    "warn",
                    request.getChannelTransactionNo()
            );
        }
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
            BigDecimal settledAmount = latestOrderAmount == null
                    ? parseAmount(detail.getAmount())
                    : latestOrderAmount;
            if (latestOrderAmount != null) {
                paymentMapper.updateOrderAfterPayment(detail.getOrderNo(), latestOrderAmount, "待履约", "info");
            }
            paymentMapper.updateBillAfterPayment(detail.getOrderNo(), settledAmount, "已结清", "success");
        }
        String eventNo = buildBusinessNo("EVT");
        String eventType = paySuccess ? "PAYMENT_SUCCESS" : "PAYMENT_PENDING";
        String eventPayload = "{\"channel\":\"" + escapeJson(normalizedChannel) + "\"}";
        paymentMapper.insertEvent(
                eventNo,
                eventType,
                request.getPaymentOrderId(),
                detail.getOrderNo(),
                eventPayload);
        recordEventProcessLog(eventNo, eventType, request.getPaymentOrderId(), detail.getOrderNo());
        if (paySuccess) {
            paymentEventDispatchService.publishPaymentSuccess(eventNo, request.getPaymentOrderId());
        }
        if (paySuccess) {
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_SUCCESS");
        }
        return outwardDetail(paymentMapper.findDetail(request.getPaymentOrderId()));
    }

    private void validateCallbackField(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must not exceed " + maxLength);
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + " must not contain control characters");
            }
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public PaymentDetailDTO query(PaymentQueryRequestDTO request) {
        if (request == null) {
            throw invalidPaymentRequest("支付查单请求不能为空");
        }
        request.setPaymentOrderId(normalizeRequiredRequestField(
                request.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        paymentChannelQueryRateLimitService.acquire(detail.getChannel(), detail.getPaymentOrderId());
        PaymentChannelQueryResultDTO queryResult;
        try {
            queryResult = PaymentChannelQueryResultValidator.validateAndNormalize(
                    paymentChannelQueryService.query(detail));
        } catch (BusinessException exception) {
            recordChannelQueryFailure(detail, exception);
            throw exception;
        }
        applyReturnCodeStandardization(detail, queryResult);
        paymentMapper.updatePaymentReturnCodeSnapshot(
                detail.getPaymentOrderId(),
                queryResult.getChannelReturnCode(),
                queryResult.getStandardizedCode(),
                queryResult.getStandardizedMessage(),
                queryResult.getHandlingSuggestion(),
                queryResult.getRetryable(),
                queryResult.getManualInterventionRequired(),
                queryResult.getMappingVersion(),
                queryResult.getReturnCodeMappingMatched());
        String channelTransactionNo = StringUtils.hasText(queryResult.getChannelTransactionNo())
                ? queryResult.getChannelTransactionNo().trim()
                : detail.getChannelTransactionNo();
        detail.setChannelTransactionNo(channelTransactionNo);
        detail.setQuerySource(queryResult.getQuerySource());
        recordChannelQuery(detail, queryResult);
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())
                || !"SUCCESS".equalsIgnoreCase(queryResult.getTradeStatus())) {
            return outwardDetail(detail);
        }
        if (!StringUtils.hasText(channelTransactionNo)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "channel transaction number is required to settle a successful query");
        }
        int settledRows = paymentMapper.markPaymentSuccessFromChannelResult(
                detail.getPaymentOrderId(), channelTransactionNo);
        if (settledRows == 0) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
            if (latestDetail == null) {
                throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
            }
            latestDetail.setQuerySource(queryResult.getQuerySource());
            applyReturnCodeStandardization(latestDetail, queryResult);
            return outwardDetail(latestDetail);
        }
        paymentMapper.updatePaymentAttemptStatusByPaymentOrderId(
                detail.getPaymentOrderId(), "成功", "success");
        paymentMapper.updatePrepayStatusByPaymentOrderId(
                detail.getPaymentOrderId(), "支付成功", "success");
        settleOrderAndBill(detail);
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"channel\":\"" + detail.getChannel()
                + "\",\"source\":\"ACTIVE_QUERY\"}";
        paymentMapper.insertEvent(
                eventNo,
                "PAYMENT_SUCCESS",
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                eventPayload);
        recordEventProcessLog(
                eventNo, "PAYMENT_SUCCESS", detail.getPaymentOrderId(), detail.getOrderNo());
        paymentEventDispatchService.publishPaymentSuccess(eventNo, detail.getPaymentOrderId());
        paymentMapper.releaseSubmitConcurrencyToken(detail.getPaymentOrderId(), "ACTIVE_QUERY_SUCCESS");
        PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
        if (latestDetail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        latestDetail.setQuerySource(queryResult.getQuerySource());
        applyReturnCodeStandardization(latestDetail, queryResult);
        return outwardDetail(latestDetail);
    }

    private void recordChannelQuery(
            PaymentDetailDTO detail,
            PaymentChannelQueryResultDTO queryResult) {
        String notifyNo = buildBusinessNo("QRY");
        String responsePayload = buildChannelQueryResponsePayload(queryResult);
        String statusType = "SUCCESS".equalsIgnoreCase(queryResult.getTradeStatus()) ? "success" : "info";
        paymentMapper.insertNotifyLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getChannel(),
                queryResult.getTradeStatus(),
                "{\"querySource\":\"" + escapeJson(queryResult.getQuerySource()) + "\"}",
                responsePayload,
                "已查单",
                statusType);
        recordNotifyProcessLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                null,
                detail.getChannel(),
                queryResult.getTradeStatus(),
                "已查单",
                statusType);
    }

    private String buildChannelQueryResponsePayload(PaymentChannelQueryResultDTO queryResult) {
        if (!StringUtils.hasText(queryResult.getChannelReturnCode())) {
            return "{\"code\":\"SUCCESS\"}";
        }
        return new StringJoiner(",", "{", "}")
                .add("\"code\":\"SUCCESS\"")
                .add("\"channelReturnCode\":\"" + escapeJson(queryResult.getChannelReturnCode()) + "\"")
                .add("\"standardizedCode\":\"" + escapeJson(queryResult.getStandardizedCode()) + "\"")
                .add("\"standardizedMessage\":\"" + escapeJson(queryResult.getStandardizedMessage()) + "\"")
                .add("\"handlingSuggestion\":\"" + escapeJson(queryResult.getHandlingSuggestion()) + "\"")
                .add("\"retryable\":\"" + escapeJson(queryResult.getRetryable()) + "\"")
                .add("\"manualInterventionRequired\":\""
                        + escapeJson(queryResult.getManualInterventionRequired()) + "\"")
                .add("\"mappingVersion\":\"" + escapeJson(queryResult.getMappingVersion()) + "\"")
                .add("\"returnCodeMappingMatched\":"
                        + Boolean.TRUE.equals(queryResult.getReturnCodeMappingMatched()))
                .toString();
    }

    private void applyReturnCodeStandardization(
            PaymentDetailDTO detail, PaymentChannelQueryResultDTO queryResult) {
        detail.setChannelReturnCode(queryResult.getChannelReturnCode());
        detail.setStandardizedCode(queryResult.getStandardizedCode());
        detail.setStandardizedMessage(queryResult.getStandardizedMessage());
        detail.setHandlingSuggestion(queryResult.getHandlingSuggestion());
        detail.setRetryable(queryResult.getRetryable());
        detail.setManualInterventionRequired(queryResult.getManualInterventionRequired());
        detail.setReturnCodeMappingVersion(queryResult.getMappingVersion());
        detail.setReturnCodeMappingMatched(queryResult.getReturnCodeMappingMatched());
    }

    private void recordChannelQueryFailure(PaymentDetailDTO detail, BusinessException exception) {
        String notifyNo = buildBusinessNo("QRY");
        String responsePayload = "{\"code\":\"" + escapeJson(exception.getCode())
                + "\",\"message\":\"channel query failed\"}";
        paymentMapper.insertNotifyLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getChannel(),
                "QUERY_FAILED",
                "{\"querySource\":\"CHANNEL_QUERY\"}",
                responsePayload,
                "查单失败",
                "danger");
        recordNotifyProcessLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                null,
                detail.getChannel(),
                "QUERY_FAILED",
                "查单失败",
                "danger");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private void settleOrderAndBill(PaymentDetailDTO detail) {
        BigDecimal latestOrderAmount = paymentMapper.findOrderAmount(detail.getOrderNo());
        BigDecimal settledAmount = latestOrderAmount == null
                ? parseAmount(detail.getAmount())
                : latestOrderAmount;
        if (latestOrderAmount != null) {
            paymentMapper.updateOrderAfterPayment(
                    detail.getOrderNo(), latestOrderAmount, "待履约", "info");
        }
        paymentMapper.updateBillAfterPayment(
                detail.getOrderNo(), settledAmount, "已结清", "success");
    }

    @Transactional
    @Override
    public PaymentDetailDTO close(PaymentCloseRequestDTO request) {
        if (request == null) {
            throw invalidPaymentRequest("支付关闭请求不能为空");
        }
        request.setPaymentOrderId(normalizeRequiredRequestField(
                request.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        PaymentDetailDTO detail = paymentMapper.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CLOSE_ALREADY_FINALIZED");
            return outwardDetail(detail);
        }
        if ("CLOSED".equalsIgnoreCase(detail.getStatus())) {
            if (detail.getChannelTransactionNo() == null && detail.getLatestAttemptStatus() == null) {
                paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CLOSE_ALREADY_FINALIZED");
            }
            return outwardDetail(detail);
        }
        if (paymentMapper.markUnsubmittedPaymentClosed(request.getPaymentOrderId()) == 0) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(request.getPaymentOrderId());
            if (latestDetail != null && ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    || "CLOSED".equalsIgnoreCase(latestDetail.getStatus()))) {
                return outwardDetail(latestDetail);
            }
            throw new BusinessException(
                    ErrorCode.PAYMENT_CLOSE_CHANNEL_CONFIRMATION_REQUIRED,
                    "支付已提交渠道或结果尚未确认，请先主动查单");
        }
        // 关闭支付单时同步记录事件，后续可以从事件轨迹里看到是谁、什么时候关闭的。
        paymentMapper.updatePrepayStatusByPaymentOrderId(request.getPaymentOrderId(), "已关闭", "danger");
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"close\":\"manual\"}";
        paymentMapper.insertEvent(
                eventNo, "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo(), eventPayload);
        recordEventProcessLog(
                eventNo, "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo());
        paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "MANUAL_CLOSE");
        return outwardDetail(paymentMapper.findDetail(request.getPaymentOrderId()));
    }

    private void recordNotifyProcessLog(
            String notifyNo,
            String paymentOrderId,
            String orderNo,
            String requestNo,
            String channelCode,
            String notifyType,
            String notifyStatus,
            String statusType) {
        recordProcessLog(
                notifyNo,
                paymentOrderId,
                orderNo,
                requestNo,
                "PAYMENT_NOTIFY",
                "渠道回调",
                statusType,
                "回调类型=" + notifyType
                        + "；处理状态=" + notifyStatus,
                channelCode);
    }

    private void recordEventProcessLog(
            String eventNo,
            String eventType,
            String paymentOrderId,
            String orderNo) {
        String statusType = "PAYMENT_CLOSED".equals(eventType) ? "warn" : "info";
        recordProcessLog(
                eventNo,
                paymentOrderId,
                orderNo,
                null,
                "PAYMENT_EVENT",
                "业务事件",
                statusType,
                "事件类型=" + eventType,
                "payment-core");
    }

    private void recordProcessLog(
            String sourceNo,
            String paymentOrderId,
            String orderNo,
            String requestNo,
            String sourceType,
            String processStage,
            String statusType,
            String message,
            String source) {
        PaymentProcessLogEntity processLog = new PaymentProcessLogEntity();
        processLog.setLogNo(sourceNo);
        processLog.setPaymentOrderId(paymentOrderId);
        processLog.setOrderNo(orderNo);
        processLog.setRequestNo(requestNo);
        processLog.setSourceType(sourceType);
        processLog.setSourceNo(sourceNo);
        processLog.setProcessStage(processStage);
        processLog.setLogLevel(toLogLevel(statusType));
        processLog.setLogLevelType(statusType);
        processLog.setMessage(message);
        processLog.setSource(source);
        paymentMapper.insertPaymentProcessLog(processLog);
    }

    private String toLogLevel(String statusType) {
        if ("danger".equalsIgnoreCase(statusType)) {
            return "ERROR";
        }
        if ("warn".equalsIgnoreCase(statusType)) {
            return "WARN";
        }
        return "INFO";
    }

    static String buildBusinessNo(String prefix) {
        return prefix
                + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void normalizeAndValidatePrepayRequest(PrepayRequestDTO request) {
        if (request == null) {
            throw invalidPaymentRequest("预付单创建请求不能为空");
        }
        request.setOrderNo(normalizeRequiredRequestField(
                request.getOrderNo(), "订单号", BUSINESS_ORDER_NO_MAX_LENGTH));
        request.setPayScene(normalizeRequiredRequestField(
                request.getPayScene(), "支付场景", PREPAY_PAY_SCENE_MAX_LENGTH));
        request.setCustomerName(normalizeOptionalRequestField(
                request.getCustomerName(), "客户名称", PREPAY_CUSTOMER_NAME_MAX_LENGTH));
        request.setCashierTitle(normalizeOptionalRequestField(
                request.getCashierTitle(), "收银台标题", PREPAY_CASHIER_TITLE_MAX_LENGTH));
        if (request.getAmount() != null) {
            validatePrepayAmount(request.getAmount());
        }
    }

    private void validatePrepayAmount(BigDecimal amount) {
        BigDecimal normalizedAmount = amount.stripTrailingZeros();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalidPaymentRequest("预付金额必须大于 0");
        }
        if (normalizedAmount.scale() > MONEY_MAX_SCALE || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw invalidPaymentRequest("预付金额最多支持 16 位整数和 2 位小数");
        }
    }

    private void validateActivePrepayRequest(PrepayOrderDTO activePrepay, PrepayRequestDTO request) {
        String requestedCashierTitle = StringUtils.hasText(request.getCashierTitle())
                ? request.getCashierTitle()
                : "家政服务收银台";
        boolean conflict = !request.getPayScene().equals(activePrepay.getPayScene())
                || (request.getCustomerName() != null
                    && !request.getCustomerName().equals(activePrepay.getCustomerName()))
                || (request.getAmount() != null
                    && request.getAmount().compareTo(parseAmount(activePrepay.getAmount())) != 0)
                || !requestedCashierTitle.equals(activePrepay.getCashierTitle());
        if (conflict) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                    "同一订单已有参数不一致的有效预付单");
        }
    }

    private String normalizeRequiredRequestField(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalRequestField(value, fieldName, maxLength);
        if (normalized == null) {
            throw invalidPaymentRequest(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalRequestField(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidPaymentRequest(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw invalidPaymentRequest(fieldName + "不能包含控制字符");
            }
        }
        return normalized;
    }

    private BusinessException invalidPaymentRequest(String message) {
        return new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, message);
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

    private void validateSubmitIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                    "支付幂等键不能为空");
        }
        if (idempotencyKey.length() > SUBMIT_IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                    "支付幂等键长度不能超过 128 个字符");
        }
        for (int index = 0; index < idempotencyKey.length(); index++) {
            if (Character.isISOControl(idempotencyKey.charAt(index))) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                        "支付幂等键不能包含控制字符");
            }
        }
    }

    /**
     * 在任何数据库读取或外部协作前收口客户端可控字段，避免非法值进入路由、风控和限流维度。
     */
    private void normalizeAndValidateSubmitRequest(PaymentSubmitRequestDTO request) {
        if (request == null) {
            throw invalidSubmitRequest("支付提交请求不能为空");
        }
        request.setPrepayOrderNo(normalizeRequiredSubmitField(
                request.getPrepayOrderNo(), "预付单号", SUBMIT_PREPAY_ORDER_NO_MAX_LENGTH));
        request.setPaymentMethod(normalizeOptionalSubmitField(
                request.getPaymentMethod(), "支付方式", SUBMIT_PAYMENT_METHOD_MAX_LENGTH));
        request.setChannelCode(normalizeOptionalSubmitField(
                request.getChannelCode(), "渠道编码", SUBMIT_CHANNEL_CODE_MAX_LENGTH));
        request.setSourceAppId(normalizeOptionalSubmitField(
                request.getSourceAppId(), "来源应用标识", SUBMIT_SOURCE_APP_ID_MAX_LENGTH));
        request.setMerchantNo(normalizeOptionalSubmitField(
                request.getMerchantNo(), "商户号", SUBMIT_MERCHANT_NO_MAX_LENGTH));
        request.setAccessToken(normalizeOptionalSubmitField(
                request.getAccessToken(), "访问令牌", SUBMIT_ACCESS_TOKEN_MAX_LENGTH));
        request.setTerminal(normalizeSubmitTerminal(request.getTerminal()));
        String clientIp = normalizeOptionalSubmitField(
                request.getClientIp(), "客户端 IP", SUBMIT_CLIENT_IP_MAX_LENGTH);
        request.setClientIp(clientIp == null ? "UNKNOWN" : clientIp);
        request.setClientDeviceId(normalizeOptionalSubmitField(
                request.getClientDeviceId(), "客户端设备号", SUBMIT_CLIENT_DEVICE_ID_MAX_LENGTH));
        request.setPayerPhone(normalizeOptionalSubmitField(
                request.getPayerPhone(), "付款手机号", SUBMIT_PAYER_PHONE_MAX_LENGTH));
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            String idempotencyKey = request.getIdempotencyKey().trim();
            validateSubmitIdempotencyKey(idempotencyKey);
            request.setIdempotencyKey(idempotencyKey);
        }
    }

    private void normalizeAndValidateResolvedSubmitFields(PaymentSubmitRequestDTO request) {
        request.setPaymentMethod(normalizeRequiredSubmitField(
                request.getPaymentMethod(), "支付方式", SUBMIT_PAYMENT_METHOD_MAX_LENGTH));
        request.setChannelCode(normalizeOptionalSubmitField(
                request.getChannelCode(), "渠道编码", SUBMIT_CHANNEL_CODE_MAX_LENGTH));
    }

    private String normalizeSubmitTerminal(String terminal) {
        String normalized = StringUtils.hasText(terminal)
                ? terminal.trim().toUpperCase(Locale.ROOT)
                : "UNKNOWN";
        if ("APP".equals(normalized) || "APP_WEB".equals(normalized)) {
            return "APP";
        }
        if ("H5".equals(normalized) || "H5_WEB".equals(normalized) || "MOBILE_WEB".equals(normalized)) {
            return "H5";
        }
        if ("PC".equals(normalized) || "PC_WEB".equals(normalized)) {
            return "PC";
        }
        if ("UNKNOWN".equals(normalized)) {
            return normalized;
        }
        throw invalidSubmitRequest("终端标识仅支持 APP、H5、PC 或 UNKNOWN");
    }

    private String normalizeRequiredSubmitField(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalSubmitField(value, fieldName, maxLength);
        if (normalized == null) {
            throw invalidSubmitRequest(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalSubmitField(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        validateRequiredSubmitField(normalized, fieldName, maxLength);
        return normalized;
    }

    private void validateRequiredSubmitField(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalidSubmitRequest(fieldName + "不能为空");
        }
        if (value.length() > maxLength) {
            throw invalidSubmitRequest(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidSubmitRequest(fieldName + "不能包含控制字符");
            }
        }
    }

    private BusinessException invalidSubmitRequest(String message) {
        return new BusinessException(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, message);
    }

    /**
     * 来源应用优先使用显式透传值，未透传时回退到默认收银台应用。
     */
    private String resolveSourceAppId(PaymentSubmitRequestDTO request) {
        if (StringUtils.hasText(request.getSourceAppId())) {
            return request.getSourceAppId().trim();
        }
        return "default-app";
    }

    /**
     * 提交并发令牌用于限制同一预付单在短时间内被不同终端或不同请求同时打入支付链路。
     */
    private void occupySubmitConcurrencyToken(String prepayOrderNo,
                                              String paymentOrderId,
                                              String sourceAppId,
                                              String terminal,
                                              String clientIp,
                                              String idempotencyKey) {
        PaymentSubmitConcurrencyTokenDTO currentToken = paymentMapper.findSubmitConcurrencyToken(prepayOrderNo, sourceAppId);
        if (currentToken == null) {
            paymentMapper.insertSubmitConcurrencyToken(
                    prepayOrderNo,
                    paymentOrderId,
                    sourceAppId,
                    idempotencyKey,
                    terminal,
                    clientIp,
                    SUBMIT_CONCURRENCY_TOKEN_EXPIRE_SECONDS
            );
            return;
        }
        if (idempotencyKey.equals(currentToken.getHolderIdempotencyKey())) {
            return;
        }
        int affectedRows = paymentMapper.occupySubmitConcurrencyToken(
                prepayOrderNo,
                paymentOrderId,
                sourceAppId,
                idempotencyKey,
                terminal,
                clientIp,
                SUBMIT_CONCURRENCY_TOKEN_EXPIRE_SECONDS
        );
        if (affectedRows > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_CONCURRENCY_BLOCKED, "当前预付单已存在并发支付提交，请稍后刷新收银台状态");
    }

    /**
     * 正式版支付提交流程需要经过来源应用控制策略校验，避免无权限应用、异常自检状态和瞬时洪峰直接进入渠道。
     */
    private void validateSubmitControlPolicy(
            String sourceAppId,
            String merchantNo,
            String accessToken,
            String paymentMethod,
            String resolvedChannelCode,
            String terminal,
            String clientIp,
            boolean existingAttempt) {
        PaymentControlPolicyDTO controlPolicy = paymentMapper.findActiveControlPolicyBySourceAppId(sourceAppId);
        if (controlPolicy == null) {
            return;
        }
        if (!containsConfiguredValue(controlPolicy.getAllowedPaymentMethods(), paymentMethod)) {
            throw new BusinessException(ErrorCode.PAYMENT_SOURCE_APP_NOT_ALLOWED, "当前来源应用未开通该支付方式");
        }
        if (!containsConfiguredValue(controlPolicy.getAllowedChannelCodes(), resolvedChannelCode)) {
            throw new BusinessException(ErrorCode.PAYMENT_SOURCE_APP_NOT_ALLOWED, "当前来源应用未开通该支付渠道");
        }
        if (!containsConfiguredValue(controlPolicy.getAllowedMerchantNos(), merchantNo)) {
            throw new BusinessException(ErrorCode.PAYMENT_MERCHANT_NOT_ALLOWED, "当前来源应用未开通该商户号");
        }
        if ("开启".equals(controlPolicy.getTokenAuthRequired())
                && !matchesAccessToken(controlPolicy.getAccessTokenValue(), accessToken)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, "当前请求的访问令牌无效或已失效");
        }
        if ("开启".equals(controlPolicy.getStrictMode())
                && !"PASS".equalsIgnoreCase(controlPolicy.getSelfCheckStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_SELF_CHECK_BLOCKED, controlPolicy.getSelfCheckMessage());
        }
        if (!existingAttempt) {
            paymentSubmitRateLimitService.acquire(
                    sourceAppId,
                    paymentMethod,
                    terminal,
                    clientIp,
                    positiveLimit(controlPolicy.getInterfaceMinuteSubmitLimit()),
                    positiveLimit(controlPolicy.getMinuteSubmitLimit()));
        }
    }

    private int positiveLimit(Integer limit) {
        return limit == null || limit.intValue() <= 0 ? 0 : limit.intValue();
    }

    /**
     * 优先使用运营配置域的有效快照补齐默认支付方式和默认渠道，避免前端漏传时只能依赖硬编码兜底。
     */
    private Long applyOpsConfigDefaults(PaymentSubmitRequestDTO request,
                                        PrepayOrderDTO currentPrepay,
                                        String terminal) {
        boolean paymentMethodMissing = !StringUtils.hasText(request.getPaymentMethod());
        boolean channelCodeMissing = !StringUtils.hasText(request.getChannelCode());
        if (!paymentMethodMissing && !channelCodeMissing) {
            return null;
        }
        PaymentOpsConfigSnapshotDTO snapshot = paymentOpsConfigService.loadEffectiveSnapshot(
                currentPrepay.getPayScene(),
                "PAY_CONSUME",
                terminal);
        if (snapshot == null) {
            if (paymentMethodMissing) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID,
                        "当前支付场景缺少有效运营配置，无法补齐支付方式或渠道");
            }
            return null;
        }
        if (paymentMethodMissing && StringUtils.hasText(snapshot.getDefaultPayMethod())) {
            request.setPaymentMethod(snapshot.getDefaultPayMethod().trim());
        }
        if (channelCodeMissing && StringUtils.hasText(snapshot.getPrimaryChannelProfileCode())) {
            request.setChannelCode(resolveOpsChannelCode(snapshot.getPrimaryChannelProfileCode()));
        }
        if (paymentMethodMissing && !StringUtils.hasText(request.getPaymentMethod())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID,
                    "当前支付场景未配置默认支付方式，无法提交支付");
        }
        return snapshot.getSnapshotVersion();
    }

    private String resolveOpsChannelCode(String profileCode) {
        String normalizedProfileCode = profileCode == null ? "" : profileCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedProfileCode.contains("WX_H5")) {
            return "wx_h5";
        }
        if (normalizedProfileCode.contains("ALI")) {
            return "alipay_h5";
        }
        if (normalizedProfileCode.contains("BANK")) {
            return "offline_bank";
        }
        return profileCode;
    }

    private boolean containsConfiguredValue(String configuredValues, String actualValue) {
        if (!StringUtils.hasText(configuredValues) || !StringUtils.hasText(actualValue)) {
            return false;
        }
        String normalizedActualValue = actualValue.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(configuredValues.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedActualValue::equals);
    }

    private boolean matchesAccessToken(String configuredAccessToken, String actualAccessToken) {
        if (!StringUtils.hasText(configuredAccessToken) || !StringUtils.hasText(actualAccessToken)) {
            return false;
        }
        return configuredAccessToken.trim().equals(actualAccessToken.trim());
    }

    /**
     * 提交支付前统一收口路由上下文，避免路由规则读取到的口径在不同入口下不一致。
     */
    private PaymentRouteContextDTO buildRouteContext(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String terminal) {
        PaymentRouteContextDTO routeContext = new PaymentRouteContextDTO();
        routeContext.setRoutingKey(currentPrepay.getPaymentOrderId());
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
            String sourceAppId,
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
        submitRequest.setSourceAppId(sourceAppId);
        submitRequest.setRequestedChannelCode(request.getChannelCode());
        submitRequest.setResolvedChannelCode(resolvedChannelCode);
        submitRequest.setTerminal(terminal);
        submitRequest.setClientIp(clientIp);
        submitRequest.setIdempotencyKey(idempotencyKey);
        return submitRequest;
    }

    /**
     * 提交支付前先请求独立风控中心，确保黑名单、限额和人工复核链路先于渠道出站生效。
     */
    private PaymentRiskDecisionRequestDTO buildRiskDecisionRequest(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String paymentOrderId,
            String resolvedChannelCode,
            String terminal,
            String clientIp) {
        PaymentRiskDecisionRequestDTO decisionRequest = new PaymentRiskDecisionRequestDTO();
        decisionRequest.setBusinessNo(paymentOrderId);
        decisionRequest.setSourceSystem("payment-core");
        decisionRequest.setSceneCode("PAY_CONSUME");
        decisionRequest.setPayScene(currentPrepay.getPayScene());
        decisionRequest.setPaymentMethod(request.getPaymentMethod());
        decisionRequest.setChannelCode(resolvedChannelCode);
        decisionRequest.setMerchantNo(request.getMerchantNo());
        decisionRequest.setTerminal(terminal);
        decisionRequest.setClientIp(clientIp);
        decisionRequest.setClientDeviceId(request.getClientDeviceId());
        decisionRequest.setPayerPhone(request.getPayerPhone());
        decisionRequest.setAmount(parseAmount(currentPrepay.getAmount()));
        return decisionRequest;
    }

    /**
     * 统一请求留痕口径，便于在支付请求管理页直接复盘一次发起请求。
     */
    private String buildSubmitRequestPayload(
            PaymentSubmitRequestDTO request,
            String sourceAppId,
            String terminal,
            String clientIp,
            String idempotencyKey,
            String resolvedChannelCode) {
        return new StringJoiner(",", "{", "}")
                .add("\"method\":\"" + escapeJson(request.getPaymentMethod()) + "\"")
                .add("\"channelCode\":\"" + escapeJson(request.getChannelCode()) + "\"")
                .add("\"sourceAppId\":\"" + escapeJson(sourceAppId) + "\"")
                .add("\"resolvedChannelCode\":\"" + escapeJson(resolvedChannelCode) + "\"")
                .add("\"terminal\":\"" + escapeJson(terminal) + "\"")
                .add("\"clientIp\":\"" + escapeJson(clientIp) + "\"")
                .add("\"clientDeviceId\":\"" + escapeJson(defaultText(request.getClientDeviceId())) + "\"")
                .add("\"payerPhone\":\"" + escapeJson(defaultText(request.getPayerPhone())) + "\"")
                .add("\"idempotencyKey\":\"" + escapeJson(idempotencyKey) + "\"")
                .toString();
    }

    private String buildRiskDecisionPayload(PaymentRiskDecisionResultDTO riskDecision, String resolvedChannelCode) {
        return new StringJoiner(",", "{", "}")
                .add("\"decision\":\"" + escapeJson(defaultText(riskDecision.getDecision())) + "\"")
                .add("\"decisionType\":\"" + escapeJson(defaultText(riskDecision.getDecisionType())) + "\"")
                .add("\"hitCode\":\"" + escapeJson(defaultText(riskDecision.getHitCode())) + "\"")
                .add("\"riskTag\":\"" + escapeJson(defaultText(riskDecision.getRiskTag())) + "\"")
                .add("\"reviewNo\":\"" + escapeJson(defaultText(riskDecision.getReviewNo())) + "\"")
                .add("\"eventNo\":\"" + escapeJson(defaultText(riskDecision.getEventNo())) + "\"")
                .add("\"channelCode\":\"" + escapeJson(defaultText(resolvedChannelCode)) + "\"")
                .add("\"message\":\"" + escapeJson(defaultText(riskDecision.getMessage())) + "\"")
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

    private List<PaymentListItemDTO> maskPaymentItems(List<PaymentListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedPaymentItemCopy).collect(Collectors.toList());
    }

    private PaymentListItemDTO maskedPaymentItemCopy(PaymentListItemDTO item) {
        if (item == null) {
            return null;
        }
        PaymentListItemDTO copy = new PaymentListItemDTO();
        BeanUtils.copyProperties(item, copy);
        copy.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return copy;
    }

    private PaymentDetailDTO outwardDetail(PaymentDetailDTO detail) {
        return maskedDetailCopy(enrichDetail(detail));
    }

    private PaymentDetailDTO maskedDetailCopy(PaymentDetailDTO detail) {
        if (detail == null) {
            return null;
        }
        PaymentDetailDTO copy = new PaymentDetailDTO();
        BeanUtils.copyProperties(detail, copy);
        copy.setCustomerName(sensitiveDataMasker.maskName(detail.getCustomerName()));
        copy.setLatestClientIp(sensitiveDataMasker.maskClientIp(detail.getLatestClientIp()));
        copy.setLatestIdempotencyKey(sensitiveDataMasker.maskIdempotencyKey(detail.getLatestIdempotencyKey()));
        copy.setLatestRequestPayload(sensitiveDataMasker.maskPayload(detail.getLatestRequestPayload()));
        copy.setLatestResponsePayload(sensitiveDataMasker.maskPayload(detail.getLatestResponsePayload()));
        return copy;
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

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }
}
