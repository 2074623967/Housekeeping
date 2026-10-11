package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentAttemptIdempotencyDTO;
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
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final int BUSINESS_ORDER_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int PREPAY_ORDER_NO_MAX_LENGTH = 64;
    private static final int PREPAY_PAY_SCENE_MAX_LENGTH = 32;
    private static final int PREPAY_CUSTOMER_NAME_MAX_LENGTH = 128;
    private static final int PREPAY_CASHIER_TITLE_MAX_LENGTH = 128;
    private static final int PREPAY_REMARK_MAX_LENGTH = 255;
    private static final int PAYMENT_LIST_PAYMENT_METHOD_MAX_LENGTH = 32;
    private static final int PAYMENT_LIST_CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int PAYMENT_LIST_STATUS_MAX_LENGTH = 32;
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final DateTimeFormatter PAYMENT_FACT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

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
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final String CUSTOMER_SELF_SERVICE_OPERATOR = "customer-self-service";

    private static final int SUBMIT_CONCURRENCY_TOKEN_EXPIRE_SECONDS = 120;
    private static final String SUBMIT_TRANSACTION_FAILURE_CODE = "PAYMENT_SUBMIT_TRANSACTION_FAILED";

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
                    public boolean republish(String eventNo, String operator) {
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
        query = normalizeQuery(query);
        return new PageResultDTO<>(
                maskPaymentItems(paymentMapper.findAll(query)),
                paymentMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(PaymentListQueryDTO query) {
        query = normalizeQuery(query);
        List<PaymentListItemDTO> items = maskPaymentItems(
                CsvExportRowLimiter.requireWithinLimit(paymentMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("支付单号,业务链路,订单号,支付场景,客户名称,支付金额,支付方式,支付渠道,渠道交易号,支付状态,支付状态样式,最近尝试状态,最近尝试状态样式,最近尝试完成时间,创建时间,成功事实异常类型,预付归属异常类型\n");
        for (PaymentListItemDTO item : items) {
            builder.append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getBusinessChainId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getPayScene())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getAmount())).append(',')
                    .append(csvCell(item.getPaymentMethod())).append(',')
                    .append(csvCell(item.getChannel())).append(',')
                    .append(csvCell(item.getChannelTransactionNo())).append(',')
                    .append(csvCell(item.getStatus())).append(',')
                    .append(csvCell(item.getStatusType())).append(',')
                    .append(csvCell(item.getLatestAttemptStatus())).append(',')
                    .append(csvCell(item.getLatestAttemptStatusType())).append(',')
                    .append(csvCell(item.getLatestAttemptCompletedAt())).append(',')
                    .append(csvCell(item.getCreatedAt())).append(',')
                    .append(csvCell(item.getSuccessFactIssue())).append(',')
                    .append(csvCell(item.getPrepayBindingIssue())).append('\n');
        }
        return builder.toString();
    }

    @Override
    public PaymentDetailDTO detail(String paymentOrderId) {
        String normalizedPaymentOrderId = normalizeRequiredRequestField(
                paymentOrderId, "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH);
        PaymentDetailDTO detail = requireRequestedPaymentDetail(
                paymentMapper.findDetail(normalizedPaymentOrderId), normalizedPaymentOrderId);
        requireNonSuccessfulPaymentPrepayBinding(detail);
        if ("WAIT_CALLBACK".equalsIgnoreCase(detail.getStatus())) {
            requireWaitingChannelSubmissionFact(normalizedPaymentOrderId, detail);
        } else if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
            validatePaymentSuccessFacts(detail);
        }
        return outwardDetail(detail);
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
        BigDecimal orderAmount = paymentMapper.findOrderAmount(request.getOrderNo());
        BigDecimal paidAmount = paymentMapper.findPaidAmount(request.getOrderNo());
        boolean virtualSource = orderAmount == null && paidAmount == null;
        if (!virtualSource && (orderAmount == null || paidAmount == null)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                    "订单金额数据不完整");
        }
        BigDecimal remainAmount = null;
        String customerName = null;
        if (virtualSource) {
            validateVirtualPrepayRequest(request);
        } else {
            if (orderAmount.compareTo(BigDecimal.ZERO) <= 0
                    || paidAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw prepayRequestConflict("订单当前金额无效，无法创建预付单");
            }
            remainAmount = orderAmount.subtract(paidAmount);
            if (remainAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_ORDER_ALREADY_SETTLED,
                        "订单已结清或已超额支付，无需再次创建支付单");
            }
            customerName = paymentMapper.findCustomerNameByOrderNo(request.getOrderNo());
            if (!StringUtils.hasText(customerName)) {
                throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "订单客户数据不完整");
            }
            validateStandardPrepayRequest(request, customerName, remainAmount);
        }
        PrepayOrderDTO activePrepay = paymentMapper.findLatestActivePrepayByOrderNo(request.getOrderNo());
        if (activePrepay != null) {
            validatePrepayAmountBinding(activePrepay);
            if (!Integer.valueOf(1).equals(activePrepay.getActivePrepayCandidateCount())) {
                throw new IllegalStateException("active prepay orders for the same order are ambiguous");
            }
            String expectedSourceKind = virtualSource ? "VIRTUAL" : "STANDARD_ORDER";
            if (!expectedSourceKind.equals(activePrepay.getSourceKind())) {
                throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "预付单订单来源与当前来源不一致");
            }
            validateActivePrepayRequest(activePrepay, request);
            if (!virtualSource) {
                validateActivePrepayOrderFacts(activePrepay, customerName, remainAmount);
            }
            if ("WAIT_CALLBACK".equalsIgnoreCase(activePrepay.getPaymentStatus())) {
                requireWaitingChannelSubmissionFact(activePrepay.getPaymentOrderId(),
                        paymentMapper.findSubmissionFact(activePrepay.getPaymentOrderId()));
            }
            // 同一订单在收银台有效期内重复拉起时，直接复用已有预付单，避免支付单和预付单被拆成多份。
            PrepayOrderDTO reusablePrepay = withDefaultPrepayPaymentStatus(activePrepay);
            if ("PREPAY_CREATED".equalsIgnoreCase(reusablePrepay.getPaymentStatus())) {
                reusablePrepay.setCashierStatus("待支付");
                reusablePrepay.setCashierStatusType("warn");
            } else if ("RISK_REVIEW".equalsIgnoreCase(reusablePrepay.getPaymentStatus())) {
                reusablePrepay.setCashierStatus("待风控复核");
                reusablePrepay.setCashierStatusType("warn");
            } else if ("PAYING".equalsIgnoreCase(reusablePrepay.getPaymentStatus())) {
                reusablePrepay.setCashierStatus("支付中");
                reusablePrepay.setCashierStatusType("info");
            } else if ("WAIT_CALLBACK".equalsIgnoreCase(reusablePrepay.getPaymentStatus())
                    && !("待回调".equals(reusablePrepay.getCashierStatus())
                    && "warn".equals(reusablePrepay.getCashierStatusType()))
                    && !("结果待确认".equals(reusablePrepay.getCashierStatus())
                    && "danger".equals(reusablePrepay.getCashierStatusType()))
                    && !("支付中".equals(reusablePrepay.getCashierStatus())
                    && "info".equals(reusablePrepay.getCashierStatusType()))) {
                reusablePrepay.setCashierStatus("待回调");
                reusablePrepay.setCashierStatusType("warn");
            }
            return reusablePrepay;
        }
        if (virtualSource) {
            return createVirtualPrepay(request);
        }
        String billNo = paymentMapper.findBillNoByOrderNo(request.getOrderNo());
        if (billNo == null) {
            billNo = buildBusinessNo("BILL");
            String billStatus = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "待支付";
            String billStatusType = remainAmount.compareTo(BigDecimal.ZERO) <= 0 ? "success" : "warn";
            if (paymentMapper.insertBill(
                    billNo, request.getOrderNo(), customerName, orderAmount, paidAmount,
                    billStatus, billStatusType) != 1) {
                throw new IllegalStateException("payment bill was not created atomically");
            }
        }
        // 每次拉起收银台都生成独立支付单，避免多个预付单复用同一支付单造成状态串单。
        String paymentOrderId = buildBusinessNo("PAY");
        if (paymentMapper.insertPaymentOrder(
                paymentOrderId,
                request.getOrderNo(),
                customerName,
                remainAmount,
                request.getPayScene()
        ) != 1) {
            throw new IllegalStateException("payment order was not created atomically");
        }
        String prepayOrderNo = buildBusinessNo("PRE");
        if (paymentMapper.insertPrepayOrder(
                prepayOrderNo,
                billNo,
                request.getOrderNo(),
                customerName,
                remainAmount,
                request.getPayScene(),
                resolveCashierTitle(request),
                paymentOrderId,
                "STANDARD_ORDER"
        ) != 1) {
            throw new IllegalStateException("payment prepay order was not created atomically");
        }
        recordPrepayRemark(request, paymentOrderId, request.getOrderNo(), prepayOrderNo);
        return requirePrepayResponse(paymentMapper.findPrepay(prepayOrderNo));
    }

    /**
     * 钱包充值、转账补款等非标准订单场景在当前阶段通过虚拟单方式接入统一收银台。
     */
    private PrepayOrderDTO createVirtualPrepay(PrepayRequestDTO request) {
        validateVirtualPrepayRequest(request);
        String customerName = request.getCustomerName().trim();
        String cashierTitle = StringUtils.hasText(request.getCashierTitle())
                ? request.getCashierTitle().trim()
                : "家政服务收银台";
        String billNo = buildBusinessNo("BILL");
        if (paymentMapper.insertBill(
                billNo,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                BigDecimal.ZERO,
                "待支付",
                "warn") != 1) {
            throw new IllegalStateException("payment bill was not created atomically");
        }
        String paymentOrderId = buildBusinessNo("PAY");
        if (paymentMapper.insertPaymentOrder(
                paymentOrderId,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                request.getPayScene()
        ) != 1) {
            throw new IllegalStateException("payment order was not created atomically");
        }
        String prepayOrderNo = buildBusinessNo("PRE");
        if (paymentMapper.insertPrepayOrder(
                prepayOrderNo,
                billNo,
                request.getOrderNo(),
                customerName,
                request.getAmount(),
                request.getPayScene(),
                cashierTitle,
                paymentOrderId,
                "VIRTUAL"
        ) != 1) {
            throw new IllegalStateException("payment prepay order was not created atomically");
        }
        recordPrepayRemark(request, paymentOrderId, request.getOrderNo(), prepayOrderNo);
        return requirePrepayResponse(paymentMapper.findPrepay(prepayOrderNo));
    }

    private PrepayOrderDTO withDefaultPrepayPaymentStatus(PrepayOrderDTO prepay) {
        if (prepay != null && !StringUtils.hasText(prepay.getPaymentStatus())) {
            prepay.setPaymentStatus("PREPAY_CREATED");
        }
        return prepay;
    }

    private PrepayOrderDTO requirePrepayResponse(PrepayOrderDTO prepay) {
        if (prepay == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "预付单已写入但读取失败");
        }
        validatePrepayAmountBinding(prepay);
        return withDefaultPrepayPaymentStatus(prepay);
    }

    private void validatePrepayAmountBinding(PrepayOrderDTO prepay) {
        if (!Boolean.TRUE.equals(prepay.getPrepayAmountBindingValid())) {
            throw new IllegalStateException("prepay and payment order amounts are inconsistent");
        }
    }

    private void validateNoOtherActivePrepay(PrepayOrderDTO currentPrepay) {
        PrepayOrderDTO activePrepay = paymentMapper.findLatestActivePrepayByOrderNo(
                currentPrepay.getOrderNo());
        if (activePrepay != null
                && (!Integer.valueOf(1).equals(activePrepay.getActivePrepayCandidateCount())
                || !currentPrepay.getPrepayOrderNo().equals(activePrepay.getPrepayOrderNo()))) {
            throw new IllegalStateException("active prepay orders for the same order are ambiguous");
        }
    }

    private void validateCurrentOrderSourceForSubmit(PrepayOrderDTO currentPrepay) {
        String orderNo = currentPrepay.getOrderNo();
        BigDecimal orderAmount = paymentMapper.findOrderAmount(orderNo);
        BigDecimal paidAmount = paymentMapper.findPaidAmount(orderNo);
        if (orderAmount == null && paidAmount == null) {
            if ("VIRTUAL".equals(currentPrepay.getSourceKind())) {
                return;
            }
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "预付单原订单来源已不存在或无法确认");
        }
        if (orderAmount == null || paidAmount == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "订单金额数据不完整");
        }
        if (!"STANDARD_ORDER".equals(currentPrepay.getSourceKind())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "预付单订单来源与当前来源不一致");
        }
        String customerName = paymentMapper.findCustomerNameByOrderNo(orderNo);
        if (!StringUtils.hasText(customerName)) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, "订单客户数据不完整");
        }
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0 || paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw prepayRequestConflict("订单当前金额无效，无法提交原预付单");
        }
        BigDecimal remainAmount = orderAmount.subtract(paidAmount);
        if (remainAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_ALREADY_SETTLED, "订单已结清或已超额支付");
        }
        validateActivePrepayOrderFacts(currentPrepay, customerName, remainAmount);
    }

    private void lockPrepayOrderAndValidateSubmit(PrepayOrderDTO currentPrepay) {
        String orderNo = currentPrepay.getOrderNo();
        if (!StringUtils.hasText(orderNo)) {
            throw new IllegalStateException("payment order number is missing");
        }
        paymentMapper.initPrepayCreationLock(orderNo);
        if (!orderNo.equals(paymentMapper.lockPrepayCreation(orderNo))) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付提交并发锁获取失败");
        }
        validateNoOtherActivePrepay(currentPrepay);
        if (paymentMapper.isPrepayExpired(currentPrepay.getPrepayOrderNo())) {
            throw new BusinessException(ErrorCode.PAYMENT_PREPAY_EXPIRED, "预付单已过期，请重新发起支付");
        }
        validateCurrentOrderSourceForSubmit(currentPrepay);
    }

    private PrepayOrderDTO requireCurrentSubmitPrepay(PrepayOrderDTO expectedPrepay) {
        PrepayOrderDTO latestPrepay = paymentMapper.findPrepay(expectedPrepay.getPrepayOrderNo());
        if (latestPrepay == null
                || !expectedPrepay.getPrepayOrderNo().equals(latestPrepay.getPrepayOrderNo())
                || !expectedPrepay.getPaymentOrderId().equals(latestPrepay.getPaymentOrderId())) {
            throw new IllegalStateException("prepay and payment order amounts are inconsistent");
        }
        validatePrepayAmountBinding(latestPrepay);
        latestPrepay.setPaymentMethod(expectedPrepay.getPaymentMethod());
        return latestPrepay;
    }

    private PrepayOrderDTO validateConcurrentSubmitResult(
            PrepayOrderDTO latestPrepay, PaymentDetailDTO latestDetail, String paymentOrderId,
            String paymentMethod, String resolvedChannelCode) {
        if (isUnacceptedChannelSubmit(latestPrepay, latestDetail)) {
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, "支付提交处理中，请稍后刷新结果");
        }
        if ("SUCCESS".equalsIgnoreCase(latestPrepay.getPaymentStatus())
                && (latestDetail == null || !"SUCCESS".equalsIgnoreCase(latestDetail.getStatus()))) {
            throw new IllegalStateException("payment success facts are inconsistent");
        }
        boolean pending = latestDetail != null && "WAIT_CALLBACK".equalsIgnoreCase(latestDetail.getStatus());
        boolean success = latestDetail != null && "SUCCESS".equalsIgnoreCase(latestDetail.getStatus());
        if (!pending && !success
                && ("PAYING".equalsIgnoreCase(latestPrepay.getPaymentStatus())
                    || "支付中".equals(latestPrepay.getCashierStatus())
                    || (latestDetail != null && "PAYING".equalsIgnoreCase(latestDetail.getStatus())))) {
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, "支付提交处理中，请稍后刷新结果");
        }
        if (latestDetail == null) {
            return latestPrepay;
        }
        if (pending || success) {
            if (!paymentOrderId.equals(latestDetail.getPaymentOrderId())) {
                throw new IllegalStateException("payment success facts are inconsistent");
            }
            if (!StringUtils.hasText(latestDetail.getChannel())) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                        "payment has no bound channel submission fact");
            }
            if (!resolvedChannelCode.equals(latestDetail.getChannel())) {
                throw invalidSubmitRequest("支付渠道与已受理支付单不一致");
            }
            if (StringUtils.hasText(latestDetail.getPaymentMethod())
                    && !paymentMethod.equalsIgnoreCase(latestDetail.getPaymentMethod())) {
                throw invalidSubmitRequest("支付方式与已受理支付单不一致");
            }
            if (pending) {
                requireChannelSubmissionFact(latestDetail);
                if (!"结果待确认".equals(latestPrepay.getCashierStatus())
                        && !"支付中".equals(latestPrepay.getCashierStatus())) {
                    latestPrepay.setCashierStatus("待回调");
                    latestPrepay.setCashierStatusType("warn");
                }
            } else {
                validatePaymentSuccessFacts(latestDetail);
                latestPrepay.setCashierStatus("支付成功");
                latestPrepay.setCashierStatusType("success");
            }
            latestPrepay.setPaymentStatus(latestDetail.getStatus());
            attachPaymentChannelFacts(latestPrepay, latestDetail);
            if (!StringUtils.hasText(latestDetail.getPaymentMethod())) {
                latestPrepay.setPaymentMethod(paymentMethod);
            }
        }
        return latestPrepay;
    }

    private boolean isUnacceptedChannelSubmit(PrepayOrderDTO prepay, PaymentDetailDTO detail) {
        return detail != null && "WAIT_CALLBACK".equalsIgnoreCase(detail.getStatus())
                && "支付中".equals(prepay.getCashierStatus())
                && "渠道提交中".equals(detail.getLatestAttemptStatus())
                && !StringUtils.hasText(detail.getChannelTransactionNo())
                && detail.getLatestAttemptCompletedAt() == null;
    }

    private void recordPrepayRemark(
            PrepayRequestDTO request, String paymentOrderId, String orderNo, String prepayOrderNo) {
        if (!StringUtils.hasText(request.getRemark())) {
            return;
        }
        recordProcessLog(
                prepayOrderNo,
                paymentOrderId,
                orderNo,
                null,
                "PREPAY_ORDER",
                "预付单创建",
                "info",
                "业务说明=" + request.getRemark(),
                request.getPayScene());
    }

    @Override
    public CashierPageDTO cashier(String prepayOrderNo) {
        return cashier(prepayOrderNo, null);
    }

    @Override
    public CashierPageDTO cashier(String prepayOrderNo, String terminal) {
        String normalizedPrepayOrderNo = normalizeRequiredRequestField(
                prepayOrderNo, "预付单号", PREPAY_ORDER_NO_MAX_LENGTH);
        PrepayOrderDTO prepayOrder = paymentMapper.findPrepay(normalizedPrepayOrderNo);
        if (prepayOrder == null || !StringUtils.hasText(prepayOrder.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.PREPAY_ORDER_NOT_FOUND, "预付单不存在或未绑定支付单");
        }
        validatePrepayAmountBinding(prepayOrder);
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
        cashierPage.setPaymentStatus(StringUtils.hasText(prepayOrder.getPaymentStatus())
                ? prepayOrder.getPaymentStatus()
                : "PREPAY_CREATED");
        applyCashierPaymentStatus(cashierPage);
        PaymentDetailDTO submissionFact = paymentMapper.findSubmissionFact(prepayOrder.getPaymentOrderId());
        if (submissionFact != null) {
            cashierPage.setPaymentStatus(submissionFact.getStatus());
            cashierPage.setLatestAttemptStatus(submissionFact.getLatestAttemptStatus());
            cashierPage.setLatestAttemptStatusType(submissionFact.getLatestAttemptStatusType());
            cashierPage.setLatestAttemptCompletedAt(submissionFact.getLatestAttemptCompletedAt());
            if ("RISK_REVIEW".equals(submissionFact.getStatus())) {
                cashierPage.setReviewPaymentMethod(submissionFact.getPaymentMethod());
                cashierPage.setReviewChannelCode(submissionFact.getChannel());
                cashierPage.setChannelTransactionNo(submissionFact.getChannelTransactionNo());
            }
            if (isUnacceptedChannelSubmit(prepayOrder, submissionFact)) {
                cashierPage.setPaymentStatus("PAYING");
            }
            applyCashierPaymentStatus(cashierPage);
        }
        if ("WAIT_CALLBACK".equalsIgnoreCase(cashierPage.getPaymentStatus())) {
            requireWaitingChannelSubmissionFact(prepayOrder.getPaymentOrderId(), submissionFact);
            cashierPage.setChannel(submissionFact.getChannel());
            cashierPage.setPaymentMethod(submissionFact.getPaymentMethod());
            cashierPage.setChannelTransactionNo(submissionFact.getChannelTransactionNo());
        }
        if ("SUCCESS".equalsIgnoreCase(cashierPage.getPaymentStatus())) {
            PaymentDetailDTO successDetail = paymentMapper.findDetail(prepayOrder.getPaymentOrderId());
            if (successDetail == null
                    || !prepayOrder.getPaymentOrderId().equals(successDetail.getPaymentOrderId())) {
                throw new IllegalStateException("payment success facts are inconsistent");
            }
            validatePaymentSuccessFacts(successDetail);
            cashierPage.setChannel(successDetail.getChannel());
            cashierPage.setPaymentMethod(successDetail.getPaymentMethod());
            cashierPage.setChannelTransactionNo(successDetail.getChannelTransactionNo());
            cashierPage.setLatestAttemptStatus(successDetail.getLatestAttemptStatus());
            cashierPage.setLatestAttemptStatusType(successDetail.getLatestAttemptStatusType());
            cashierPage.setLatestAttemptCompletedAt(successDetail.getLatestAttemptCompletedAt());
        }
        if ("PREPAY_CREATED".equalsIgnoreCase(cashierPage.getPaymentStatus())
                && paymentMapper.isPrepayExpired(normalizedPrepayOrderNo)) {
            cashierPage.setStatus("已失效");
            cashierPage.setStatusType("danger");
        }
        cashierPage.setExpiresAt(prepayOrder.getExpiresAt());
        cashierPage.setChannels(resolveCashierChannels(terminal));
        return cashierPage;
    }

    private void applyCashierPaymentStatus(CashierPageDTO cashierPage) {
        String paymentStatus = cashierPage.getPaymentStatus();
        if (!StringUtils.hasText(paymentStatus)) {
            return;
        }
        switch (paymentStatus.trim().toUpperCase(Locale.ROOT)) {
            case "PREPAY_CREATED":
                cashierPage.setStatus("待支付");
                cashierPage.setStatusType("warn");
                break;
            case "WAIT_CALLBACK":
                cashierPage.setStatus("待回调");
                cashierPage.setStatusType("warn");
                break;
            case "RISK_REVIEW":
                cashierPage.setStatus("待风控复核");
                cashierPage.setStatusType("warn");
                break;
            case "RISK_BLOCKED":
                cashierPage.setStatus("风控拦截");
                cashierPage.setStatusType("danger");
                break;
            case "RISK_REJECTED":
                cashierPage.setStatus("风控拒绝");
                cashierPage.setStatusType("danger");
                break;
            case "PAYING":
                cashierPage.setStatus("支付中");
                cashierPage.setStatusType("info");
                break;
            case "SUCCESS":
                cashierPage.setStatus("支付成功");
                cashierPage.setStatusType("success");
                break;
            case "CLOSED":
                cashierPage.setStatus("已关闭");
                cashierPage.setStatusType("danger");
                break;
            case "FAIL":
                cashierPage.setStatus("结果待确认");
                cashierPage.setStatusType("danger");
                break;
            default:
                break;
        }
    }

    private List<String> resolveCashierChannels(String terminal) {
        if (!StringUtils.hasText(terminal)) {
            return Arrays.asList("微信支付", "支付宝", "银行卡");
        }
        String normalizedTerminal = terminal.trim().toUpperCase(Locale.ROOT);
        String sourceAppId = resolveSourceAppIdForTerminal(normalizedTerminal);
        PaymentControlPolicyDTO controlPolicy = sourceAppId == null
                ? null
                : paymentMapper.findActiveControlPolicyBySourceAppId(sourceAppId);
        if (controlPolicy != null) {
            if (!"开启".equals(controlPolicy.getStrictMode())
                    && !"关闭".equals(controlPolicy.getStrictMode())) {
                return Collections.emptyList();
            }
            if ("开启".equals(controlPolicy.getStrictMode())
                    && !"PASS".equals(controlPolicy.getSelfCheckStatus())) {
                return Collections.emptyList();
            }
            List<String> configuredChannels = new ArrayList<String>();
            for (String paymentMethod : Arrays.asList("微信支付", "支付宝", "银行卡")) {
                if (containsConfiguredValue(controlPolicy.getAllowedPaymentMethods(), paymentMethod)) {
                    configuredChannels.add(paymentMethod);
                }
            }
            return configuredChannels;
        }
        if ("PC_WEB".equals(normalizedTerminal)) {
            return Arrays.asList("支付宝", "银行卡");
        }
        if ("APP_WEB".equals(normalizedTerminal)
                || "H5_WEB".equals(normalizedTerminal)
                || "MINI_PROGRAM".equals(normalizedTerminal)) {
            return Arrays.asList("微信支付", "支付宝");
        }
        return Arrays.asList("微信支付", "支付宝", "银行卡");
    }

    private String resolveSourceAppIdForTerminal(String terminal) {
        if ("PC_WEB".equals(terminal)) {
            return "housekeeping-pc-web";
        }
        if ("H5_WEB".equals(terminal)) {
            return "housekeeping-h5-web";
        }
        if ("MINI_PROGRAM".equals(terminal)) {
            return "housekeeping-mini-program";
        }
        if ("APP_WEB".equals(terminal)) {
            return "housekeeping-app-web";
        }
        return null;
    }

    @Override
    public PrepayOrderDTO submit(PaymentSubmitRequestDTO request) {
        normalizeAndValidateSubmitRequest(request);
        PrepayOrderDTO currentPrepay = paymentMapper.findPrepay(request.getPrepayOrderNo());
        if (currentPrepay == null || !StringUtils.hasText(currentPrepay.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.PREPAY_ORDER_NOT_FOUND, "预付单不存在或未绑定支付单");
        }
        validatePrepayAmountBinding(currentPrepay);
        String paymentOrderId = currentPrepay.getPaymentOrderId();
        PaymentDetailDTO currentDetail = paymentMapper.findDetail(paymentOrderId);
        if (currentDetail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if (parseAmount(currentPrepay.getAmount()).compareTo(parseAmount(currentDetail.getAmount())) != 0) {
            throw new IllegalStateException("prepay and payment order amounts are inconsistent");
        }
        if ("PREPAY_CREATED".equalsIgnoreCase(currentDetail.getStatus())
                && paymentMapper.isPrepayExpired(request.getPrepayOrderNo())) {
            throw new BusinessException(ErrorCode.PAYMENT_PREPAY_EXPIRED, "预付单已过期，请重新发起支付");
        }
        if (!"SUCCESS".equalsIgnoreCase(currentDetail.getStatus())
                && !"CLOSED".equalsIgnoreCase(currentDetail.getStatus())) {
            validateNoOtherActivePrepay(currentPrepay);
            validateCurrentOrderSourceForSubmit(currentPrepay);
        }
        boolean reuseCurrentStatus = "WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())
                || "SUCCESS".equalsIgnoreCase(currentDetail.getStatus())
                || "CLOSED".equalsIgnoreCase(currentDetail.getStatus())
                || "RISK_BLOCKED".equalsIgnoreCase(currentDetail.getStatus())
                || "RISK_REJECTED".equalsIgnoreCase(currentDetail.getStatus());
        String terminal = request.getTerminal();
        String clientIp = request.getClientIp();
        String sourceAppId = resolveSourceAppId(request);
        boolean reuseSubmittedChannel = reuseCurrentStatus
                && ("WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())
                || "SUCCESS".equalsIgnoreCase(currentDetail.getStatus()))
                && StringUtils.hasText(currentDetail.getChannel())
                && (!StringUtils.hasText(request.getChannelCode())
                || request.getChannelCode().equalsIgnoreCase(currentDetail.getChannel()));
        if (reuseSubmittedChannel) {
            if (StringUtils.hasText(request.getPaymentMethod())
                    && StringUtils.hasText(currentDetail.getPaymentMethod())
                    && !request.getPaymentMethod().equalsIgnoreCase(currentDetail.getPaymentMethod())) {
                throw invalidSubmitRequest("支付方式与已受理支付单不一致");
            }
            if (!StringUtils.hasText(request.getPaymentMethod())
                    && StringUtils.hasText(currentDetail.getPaymentMethod())) {
                request.setPaymentMethod(currentDetail.getPaymentMethod());
            }
            if (!StringUtils.hasText(request.getChannelCode())) {
                request.setChannelCode(currentDetail.getChannel());
            }
        }
        Long opsConfigSnapshotVersion = applyOpsConfigDefaults(request, currentPrepay, terminal);
        normalizeAndValidateResolvedSubmitFields(request);
        currentPrepay.setPaymentMethod(request.getPaymentMethod());
        PaymentRouteDecisionDTO routeDecision = reuseSubmittedChannel ? null
                : paymentChannelRoutingService.resolve(buildRouteContext(request, currentPrepay, terminal));
        String resolvedChannelCode = reuseSubmittedChannel
                ? currentDetail.getChannel() : routeDecision.getChannelCode();
        validateRequiredSubmitField(
                resolvedChannelCode,
                "支付路由渠道",
                SUBMIT_CHANNEL_CODE_MAX_LENGTH);
        String idempotencyKey = buildIdempotencyKey(request, currentPrepay, resolvedChannelCode);
        validateSubmitIdempotencyKey(idempotencyKey);
        String requestFingerprint = submitRequestFingerprint(
                request, currentPrepay, paymentOrderId, sourceAppId, resolvedChannelCode, terminal);
        boolean existingAttempt = paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey);
        if (existingAttempt) {
            validateSubmitIdempotencyOwnership(
                    idempotencyKey, request.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
        }
        validateSubmitControlPolicy(
                sourceAppId,
                request.getMerchantNo(),
                request.getAccessToken(),
                request.getPaymentMethod(),
                resolvedChannelCode,
                terminal,
                clientIp,
                existingAttempt);
        if (isUnacceptedChannelSubmit(currentPrepay, currentDetail)) {
            validateSubmitIdempotencyOwnership(
                    idempotencyKey, request.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, "支付提交处理中，请稍后刷新结果");
        }
        if (("WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())
                || "SUCCESS".equalsIgnoreCase(currentDetail.getStatus()))
                && StringUtils.hasText(currentDetail.getChannel())
                && !currentDetail.getChannel().equalsIgnoreCase(resolvedChannelCode)) {
            throw invalidSubmitRequest("支付渠道与已受理支付单不一致");
        }
        if ("SUCCESS".equalsIgnoreCase(currentPrepay.getPaymentStatus())
                && !"SUCCESS".equalsIgnoreCase(currentDetail.getStatus())) {
            throw new IllegalStateException("payment success facts are inconsistent");
        }
        if ("WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())
                && !StringUtils.hasText(currentDetail.getChannel())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                    "payment has no bound channel submission fact");
        }
        if ("WAIT_CALLBACK".equalsIgnoreCase(currentDetail.getStatus())) {
            requireChannelSubmissionFact(currentDetail);
        }
        if (reuseCurrentStatus) {
            // 重复提交只复用当前支付状态，但必须先完成来源应用和访问令牌校验。
            if ("SUCCESS".equalsIgnoreCase(currentDetail.getStatus())) {
                if (!paymentOrderId.equals(currentDetail.getPaymentOrderId())) {
                    throw new IllegalStateException("payment success facts are inconsistent");
                }
                validatePaymentSuccessFacts(currentDetail);
            }
            PrepayOrderDTO response = requireCurrentSubmitPrepay(currentPrepay);
            attachPaymentChannelFacts(response, currentDetail);
            return response;
        }
        PaymentRiskDecisionResultDTO riskDecision = paymentRiskControlService.evaluateSubmitRisk(
                buildRiskDecisionRequest(
                        request,
                        currentPrepay,
                        paymentOrderId,
                        sourceAppId,
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
            validateSubmitIdempotencyOwnership(
                    idempotencyKey, request.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
            return validateConcurrentSubmitResult(
                    requireCurrentSubmitPrepay(currentPrepay), paymentMapper.findDetail(paymentOrderId),
                    paymentOrderId, request.getPaymentMethod(), resolvedChannelCode);
        }
        paymentChannelSubmitService.ensureAdapterAvailable(resolvedChannelCode);
        String orderNo = paymentMapper.findOrderNoByPrepayOrderNo(request.getPrepayOrderNo());
        String routeNo = buildBusinessNo("RTR");
        String attemptNo = buildBusinessNo("ATT");
        String requestPayload = buildSubmitRequestPayload(
                request, sourceAppId, terminal, clientIp, idempotencyKey, resolvedChannelCode);
        PrepayOrderDTO concurrentResult;
        try {
            concurrentResult = executeSubmitTransaction(() -> prepareSubmit(
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
                    requestFingerprint,
                    opsConfigSnapshotVersion));
        } catch (SanitizedSubmitTransactionException exception) {
            if (exception.duplicateKey) {
                try {
                    if (paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
                        validateSubmitIdempotencyOwnership(
                                idempotencyKey, request.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
                    }
                } catch (BusinessException conflict) {
                    throw conflict;
                } catch (RuntimeException lookupFailure) {
                    throw exception;
                }
            }
            throw exception;
        }
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
            validatePaymentSubmitAcceptanceResult(submitResult);
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
                    markSubmitResultUnknown(
                            paymentOrderId, request.getPaymentMethod(),
                            resolvedChannelCode, attemptNo, uncertainResult);
                    return null;
                });
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            throw exception;
        }
    }

    private void validatePaymentSubmitAcceptanceResult(PaymentChannelSubmitResultDTO submitResult) {
        if (submitResult == null) {
            throw BusinessException.sanitized(
                    ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                    "payment channel submit result is invalid");
        }
        String attemptStatus = submitResult.getAttemptStatus();
        if (attemptStatus == null) {
            return;
        }
        if (isTerminalAttemptStatus(attemptStatus)) {
            throw BusinessException.sanitized(
                    ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                    "payment channel submit returned a terminal attempt status; query before retrying");
        }
    }

    private boolean isTerminalAttemptStatus(String attemptStatus) {
        if (!StringUtils.hasText(attemptStatus)) {
            return false;
        }
        String normalizedStatus = attemptStatus.trim();
        return "成功".equals(normalizedStatus)
                || "支付成功".equals(normalizedStatus)
                || "失败".equals(normalizedStatus)
                || "支付失败".equals(normalizedStatus)
                || "SUCCESS".equalsIgnoreCase(normalizedStatus)
                || "FAIL".equalsIgnoreCase(normalizedStatus)
                || "FAILED".equalsIgnoreCase(normalizedStatus);
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
            String requestFingerprint,
            Long opsConfigSnapshotVersion) {
        if (paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
            validateSubmitIdempotencyOwnership(
                    idempotencyKey, currentPrepay.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
            return validateConcurrentSubmitResult(
                    requireCurrentSubmitPrepay(currentPrepay), paymentMapper.findDetail(paymentOrderId),
                    paymentOrderId, request.getPaymentMethod(), routeDecision.getChannelCode());
        }
        lockPrepayOrderAndValidateSubmit(currentPrepay);
        occupySubmitConcurrencyToken(
                currentPrepay.getPrepayOrderNo(),
                paymentOrderId,
                sourceAppId,
                terminal,
                clientIp,
                idempotencyKey);
        int occupiedRows = paymentMapper.updatePrepayToPaying(request.getPrepayOrderNo());
        if (occupiedRows < 0 || occupiedRows > 1) {
            throw new IllegalStateException("payment prepay status update affected multiple rows");
        }
        if (occupiedRows != 1) {
            PrepayOrderDTO latestPrepay = requireCurrentSubmitPrepay(currentPrepay);
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(paymentOrderId);
            if (paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)) {
                validateSubmitIdempotencyOwnership(
                        idempotencyKey, currentPrepay.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
            }
            validateConcurrentSubmitResult(latestPrepay, latestDetail, paymentOrderId,
                    request.getPaymentMethod(), routeDecision.getChannelCode());
            validateSubmitIdempotencyOwnership(
                    idempotencyKey, currentPrepay.getPrepayOrderNo(), paymentOrderId, requestFingerprint);
            if (latestDetail != null && ("WAIT_CALLBACK".equalsIgnoreCase(latestDetail.getStatus())
                    || "SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    || "CLOSED".equalsIgnoreCase(latestDetail.getStatus()))) {
                return latestPrepay;
            }
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, "支付提交处理中，请稍后刷新结果");
        }
        String resolvedChannelCode = routeDecision.getChannelCode();
        if (paymentMapper.updatePaymentMethodAndChannel(
                paymentOrderId, request.getPrepayOrderNo(), request.getPaymentMethod(), resolvedChannelCode, null) != 1) {
            throw new IllegalStateException("payment method and channel binding was not applied atomically");
        }
        if (paymentMapper.insertPaymentAttempt(
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
                "warn",
                requestFingerprint) != 1) {
            throw new IllegalStateException("payment attempt was not created atomically");
        }
        if (paymentMapper.insertRouteRecord(
                routeNo,
                attemptNo,
                paymentOrderId,
                resolvedChannelCode,
                routeDecision.getRouteRule(),
                routeDecision.getRouteResult()) != 1) {
            throw new IllegalStateException("payment route record was not created atomically");
        }
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
            if (paymentMapper.updateRouteOpsConfigSnapshotVersion(routeNo, opsConfigSnapshotVersion) != 1
                    || paymentMapper.updateAttemptOpsConfigSnapshotVersion(
                            attemptNo, opsConfigSnapshotVersion) != 1) {
                throw new IllegalStateException("payment ops config snapshot was not applied atomically");
            }
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
        int boundRows = paymentMapper.bindPaymentChannelSubmitResult(
                paymentOrderId,
                attemptNo,
                request.getPaymentMethod(),
                resolvedChannelCode,
                submitResult.getChannelTransactionNo());
        if (boundRows > 1) {
            throw new IllegalStateException("payment channel submit result binding affected multiple rows");
        }
        if (boundRows != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(paymentOrderId);
            validatePaymentSubmitResultBinding(
                    request.getPaymentMethod(), resolvedChannelCode,
                    submitResult.getChannelTransactionNo(), latestDetail);
            if (isFinalPaymentState(latestDetail)) {
                requireLatestSubmitAttempt(paymentOrderId, attemptNo);
                if ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                    validatePaymentSuccessFacts(latestDetail);
                    updatePaymentAttemptSubmitResult(
                            attemptNo,
                            submitResult.getResponsePayload(),
                            submitResult.getAttemptStatus(),
                            submitResult.getAttemptStatusType());
                }
                recordPaymentAttemptGateway(attemptNo, submitResult);
                return attachNativePaymentParams(
                        requirePrepayResponse(paymentMapper.findPrepay(request.getPrepayOrderNo())),
                        resolvedChannelCode,
                        request.getPaymentMethod(),
                        submitResult);
            }
            throw new IllegalStateException(
                    "payment channel submit result binding was not applied atomically");
        }
        if (paymentMapper.updatePaymentReturnCodeSnapshot(
                paymentOrderId,
                submitResult.getChannelReturnCode(),
                submitResult.getStandardizedCode(),
                submitResult.getStandardizedMessage(),
                submitResult.getHandlingSuggestion(),
                submitResult.getRetryable(),
                submitResult.getManualInterventionRequired(),
                submitResult.getMappingVersion(),
                submitResult.getReturnCodeMappingMatched()) != 1) {
            throw new IllegalStateException("payment return code snapshot was not applied atomically");
        }
        updatePaymentAttemptSubmitResult(
                attemptNo,
                submitResult.getResponsePayload(),
                submitResult.getAttemptStatus(),
                submitResult.getAttemptStatusType());
        recordPaymentAttemptGateway(attemptNo, submitResult);
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"channel\":\"" + escapeJson(resolvedChannelCode)
                + "\",\"paymentMethod\":\"" + escapeJson(request.getPaymentMethod()) + "\"}";
        insertEvent(
                eventNo,
                "PAYMENT_SUBMIT",
                paymentOrderId,
                orderNo,
                eventPayload);
        recordEventProcessLog(eventNo, "PAYMENT_SUBMIT", paymentOrderId, orderNo);
        String notifyNo = buildBusinessNo("NTF");
        insertNotifyLog(
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
        return attachNativePaymentParams(
                requirePrepayResponse(paymentMapper.findPrepay(request.getPrepayOrderNo())),
                resolvedChannelCode,
                request.getPaymentMethod(),
                submitResult);
    }

    private PrepayOrderDTO attachNativePaymentParams(
            PrepayOrderDTO prepayOrder,
            String resolvedChannelCode,
            String paymentMethod,
            PaymentChannelSubmitResultDTO submitResult) {
        prepayOrder.setChannel(resolvedChannelCode);
        prepayOrder.setPaymentMethod(paymentMethod);
        if (submitResult != null) {
            prepayOrder.setNativePaymentParams(submitResult.getNativePaymentParams());
            prepayOrder.setChannelTransactionNo(submitResult.getChannelTransactionNo());
            prepayOrder.setLatestAttemptStatus(submitResult.getAttemptStatus());
            prepayOrder.setLatestAttemptStatusType(submitResult.getAttemptStatusType());
            prepayOrder.setLatestAttemptCompletedAt(null);
        }
        return prepayOrder;
    }

    private void attachPaymentChannelFacts(PrepayOrderDTO prepayOrder, PaymentDetailDTO detail) {
        if (detail == null) {
            return;
        }
        prepayOrder.setPaymentMethod(detail.getPaymentMethod());
        prepayOrder.setChannel(detail.getChannel());
        prepayOrder.setChannelTransactionNo(detail.getChannelTransactionNo());
        prepayOrder.setLatestAttemptStatus(detail.getLatestAttemptStatus());
        prepayOrder.setLatestAttemptStatusType(detail.getLatestAttemptStatusType());
        prepayOrder.setLatestAttemptCompletedAt(detail.getLatestAttemptCompletedAt());
        prepayOrder.setSuccessAt(detail.getSuccessAt());
    }

    private void markSubmitResultUnknown(
            String paymentOrderId,
            String paymentMethod,
            String resolvedChannelCode,
            String attemptNo,
            PaymentChannelSubmitResultDTO submitResult) {
        String responsePayload = submitResult == null ? null : submitResult.getResponsePayload();
        String channelTransactionNo = submitResult == null
                ? null : submitResult.getChannelTransactionNo();
        if (paymentMapper.markPaymentSubmitResultUnknown(
                paymentOrderId, attemptNo, paymentMethod, channelTransactionNo) != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(paymentOrderId);
            if (latestDetail == null) {
                throw new IllegalStateException(
                        "payment submit result recovery target does not exist");
            }
            if (submitResult != null) {
                validatePaymentSubmitResultBinding(
                        paymentMethod, resolvedChannelCode, channelTransactionNo, latestDetail);
            }
            if (isFinalPaymentState(latestDetail)) {
                requireLatestSubmitAttempt(paymentOrderId, attemptNo);
                if (submitResult != null && "SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                    validatePaymentSuccessFacts(latestDetail);
                    updatePaymentAttemptSubmitResult(
                            attemptNo,
                            responsePayload,
                            submitResult.getAttemptStatus(),
                            submitResult.getAttemptStatusType());
                }
                recordPaymentAttemptGateway(attemptNo, submitResult);
                return;
            }
            throw new IllegalStateException(
                    "payment submit result recovery was not applied atomically");
        }
        updatePaymentAttemptSubmitResult(
                attemptNo, responsePayload, "结果未知，待查单", "danger");
        recordPaymentAttemptGateway(attemptNo, submitResult);
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                paymentOrderId, "结果待确认", "danger") != 1) {
            throw new IllegalStateException(
                    "payment prepay unknown status was not applied atomically");
        }
    }

    private void updatePaymentAttemptSubmitResult(
            String attemptNo,
            String responsePayload,
            String attemptStatus,
            String attemptStatusType) {
        if (paymentMapper.updatePaymentAttemptSubmitResult(
                attemptNo, responsePayload, attemptStatus, attemptStatusType) != 1) {
            throw new IllegalStateException(
                    "payment attempt submit result was not applied atomically");
        }
    }

    private void recordPaymentAttemptGateway(
            String attemptNo, PaymentChannelSubmitResultDTO submitResult) {
        if (submitResult != null && submitResult.getGatewayCode() != null
                && paymentMapper.recordPaymentAttemptGateway(
                        attemptNo, submitResult.getGatewayCode()) != 1) {
            throw new IllegalStateException("payment attempt gateway conflicted or was not recorded atomically");
        }
    }

    private <T> T executeSubmitTransaction(Supplier<T> action) {
        if (submitTransactionTemplate == null) {
            return executeSubmitAction(action);
        }
        return submitTransactionTemplate.execute(status -> executeSubmitAction(action));
    }

    private <T> T executeSubmitAction(Supplier<T> action) {
        try {
            return action.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception instanceof SanitizedSubmitTransactionException
                    ? exception
                    : new SanitizedSubmitTransactionException(exception);
        }
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
        lockPrepayOrderAndValidateSubmit(currentPrepay);
        if (paymentMapper.updatePaymentMethodAndChannel(
                currentDetail.getPaymentOrderId(),
                currentPrepay.getPrepayOrderNo(),
                request.getPaymentMethod(),
                resolvedChannelCode,
                currentDetail.getChannelTransactionNo()) != 1) {
            throw new IllegalStateException("risk payment method and channel binding was not applied atomically");
        }
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
        if (paymentMapper.updatePaymentRiskStatus(
                currentDetail.getPaymentOrderId(),
                paymentStatus,
                statusType) != 1) {
            throw new IllegalStateException("risk payment status was not applied atomically");
        }
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                currentDetail.getPaymentOrderId(),
                prepayStatus,
                statusType) != 1) {
            throw new IllegalStateException("risk prepay status was not applied atomically");
        }
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = buildRiskDecisionPayload(riskDecision, resolvedChannelCode);
        insertEvent(
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
        return requirePrepayResponse(paymentMapper.findPrepay(currentPrepay.getPrepayOrderNo()));
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
        validateCallbackControlCharacters(channel, "channel");
        String normalizedChannel = channel.trim().toLowerCase(Locale.ROOT);
        validateCallbackField(normalizedChannel, CALLBACK_CHANNEL_MAX_LENGTH, "channel");
        validateCallbackField(request.getPaymentOrderId(), CALLBACK_PAYMENT_ORDER_ID_MAX_LENGTH, "paymentOrderId");
        validateCallbackField(request.getTradeStatus(), CALLBACK_TRADE_STATUS_MAX_LENGTH, "tradeStatus");
        if (!isSupportedCallbackTradeStatus(request.getTradeStatus())) {
            throw new IllegalArgumentException("tradeStatus must be SUCCESS, PROCESSING, WAIT_CALLBACK or PAYING");
        }
        validateCallbackField(
                request.getChannelTransactionNo(),
                CALLBACK_CHANNEL_TRANSACTION_NO_MAX_LENGTH,
                "channelTransactionNo");
        paymentCallbackSignatureService.verify(normalizedChannel, request);
        request.setPaymentOrderId(request.getPaymentOrderId().trim());
        request.setTradeStatus(request.getTradeStatus().trim());
        request.setChannelTransactionNo(request.getChannelTransactionNo().trim());
        PaymentDetailDTO detail = requireRequestedPaymentDetail(
                paymentMapper.findDetail(request.getPaymentOrderId()), request.getPaymentOrderId());
        requireNonSuccessfulPaymentPrepayBinding(detail);
        validatePaymentCallbackBinding(normalizedChannel, request.getChannelTransactionNo(), detail);
        boolean paySuccess = "SUCCESS".equalsIgnoreCase(request.getTradeStatus());
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())
                || ("CLOSED".equalsIgnoreCase(detail.getStatus()) && !paySuccess)) {
            if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
                validatePaymentSuccessFacts(detail);
            }
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_ALREADY_FINALIZED");
            return outwardDetail(detail);
        }
        requireChannelSubmissionFact(detail);
        if (paySuccess) {
            detail = lockAndReloadPaymentForSettlement(detail);
            validatePaymentCallbackBinding(normalizedChannel, request.getChannelTransactionNo(), detail);
            requireChannelSubmissionFact(detail);
            parseAmount(detail.getAmount());
            if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
                validatePaymentSuccessFacts(detail);
                paymentMapper.releaseSubmitConcurrencyToken(
                        request.getPaymentOrderId(), "CALLBACK_ALREADY_FINALIZED");
                return outwardDetail(detail);
            }
        }
        int callbackSuccessRows = paySuccess
                ? paymentMapper.markPaymentSuccessFromChannelResult(
                        request.getPaymentOrderId(), request.getChannelTransactionNo())
                : 0;
        if (callbackSuccessRows > 1) {
            throw new IllegalStateException("payment callback success update affected multiple rows");
        }
        if (paySuccess && callbackSuccessRows != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(request.getPaymentOrderId());
            latestDetail = requirePaymentDetailResponse(latestDetail);
            if (!"SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    && (!"CLOSED".equalsIgnoreCase(latestDetail.getStatus())
                    || !Boolean.TRUE.equals(latestDetail.getPrepayAmountBindingValid()))) {
                throw new IllegalStateException("payment success confirmation was not applied atomically");
            }
            validatePaymentCallbackBinding(
                    normalizedChannel, request.getChannelTransactionNo(), latestDetail);
            if ("CLOSED".equalsIgnoreCase(latestDetail.getStatus())) {
                throw new IllegalStateException("payment callback success could not recover closed payment");
            }
            if ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                validatePaymentSuccessFacts(latestDetail);
            }
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_ALREADY_FINALIZED");
            return outwardDetail(latestDetail);
        }
        int callbackPendingRows = !paySuccess
                ? paymentMapper.markPaymentPendingFromCallback(
                        request.getPaymentOrderId(), request.getChannelTransactionNo())
                : 0;
        if (callbackPendingRows > 1) {
            throw new IllegalStateException("payment callback pending update affected multiple rows");
        }
        if (!paySuccess && callbackPendingRows != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(request.getPaymentOrderId());
            requireNonSuccessfulPaymentPrepayBinding(latestDetail);
            validatePaymentCallbackBinding(
                    normalizedChannel, request.getChannelTransactionNo(), latestDetail);
            return outwardDetail(latestDetail);
        }
        // 回调必须落日志、改状态、写事件，三件事一起做才方便对账和排障。
        String notifyNo = buildBusinessNo("NTF");
        insertNotifyLog(
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
        if (paySuccess) {
            finalizeLatestPaymentAttemptSuccess(request.getPaymentOrderId());
        } else {
            if (paymentMapper.updateLatestPaymentAttemptStatus(
                    request.getPaymentOrderId(), "待回调", "warn") != 1) {
                throw new IllegalStateException(
                        "payment attempt pending update was not applied atomically");
            }
        }
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                request.getPaymentOrderId(),
                paySuccess ? "支付成功" : "待回调",
                paySuccess ? "success" : "warn") != 1) {
            throw new IllegalStateException(
                    "payment callback prepay status was not applied atomically");
        }
        if (paySuccess) {
            settleOrderAndBill(detail);
        }
        String eventNo = buildBusinessNo("EVT");
        String eventType = paySuccess ? "PAYMENT_SUCCESS" : "PAYMENT_PENDING";
        String eventPayload = "{\"channel\":\"" + escapeJson(normalizedChannel) + "\""
                + (paySuccess ? ",\"payScene\":\"" + escapeJson(detail.getPayScene()) + "\"" : "")
                + "}";
        insertEvent(
                eventNo,
                eventType,
                request.getPaymentOrderId(),
                detail.getOrderNo(),
                eventPayload);
        recordEventProcessLog(eventNo, eventType, request.getPaymentOrderId(), detail.getOrderNo());
        if (paySuccess) {
            dispatchPaymentSuccessAfterCommit(eventNo, request.getPaymentOrderId());
        }
        if (paySuccess) {
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CALLBACK_SUCCESS");
        }
        return outwardDetail(requirePaymentDetailResponse(
                paymentMapper.findDetail(request.getPaymentOrderId())));
    }

    private void validatePaymentCallbackBinding(
            String normalizedChannel, String channelTransactionNo, PaymentDetailDTO detail) {
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
                && !detail.getChannelTransactionNo().equals(channelTransactionNo)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH,
                    "callback transaction does not match payment transaction");
        }
    }

    private void validatePaymentSubmitResultBinding(
            String paymentMethod, String channelCode, String channelTransactionNo, PaymentDetailDTO detail) {
        if (detail == null) {
            throw new IllegalStateException("payment submit result target does not exist");
        }
        if (!StringUtils.hasText(detail.getChannel())
                || !detail.getChannel().equals(channelCode)
                || (StringUtils.hasText(detail.getPaymentMethod())
                && !detail.getPaymentMethod().equals(paymentMethod))
                || (StringUtils.hasText(detail.getChannelTransactionNo())
                && !detail.getChannelTransactionNo().equals(channelTransactionNo))) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                    "payment channel submit result conflicts with payment binding");
        }
    }

    private boolean isFinalPaymentState(PaymentDetailDTO detail) {
        return "SUCCESS".equalsIgnoreCase(detail.getStatus())
                || "CLOSED".equalsIgnoreCase(detail.getStatus());
    }

    private void requireLatestSubmitAttempt(String paymentOrderId, String attemptNo) {
        if (!paymentMapper.isLatestPaymentAttemptForOrder(paymentOrderId, attemptNo)) {
            throw new IllegalStateException("payment submit result belongs to a superseded attempt");
        }
    }

    private void validateCallbackField(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must not exceed " + maxLength);
        }
        validateCallbackControlCharacters(value, fieldName);
    }

    private void validateCallbackControlCharacters(String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + " must not contain control characters");
            }
        }
    }

    private boolean isSupportedCallbackTradeStatus(String tradeStatus) {
        String normalizedTradeStatus = tradeStatus == null ? null : tradeStatus.trim();
        return "SUCCESS".equalsIgnoreCase(normalizedTradeStatus)
                || "PROCESSING".equalsIgnoreCase(normalizedTradeStatus)
                || "WAIT_CALLBACK".equalsIgnoreCase(normalizedTradeStatus)
                || "PAYING".equalsIgnoreCase(normalizedTradeStatus);
    }

    @Transactional(noRollbackFor = BusinessException.class, isolation = Isolation.READ_COMMITTED)
    @Override
    public PaymentDetailDTO query(PaymentQueryRequestDTO request) {
        return queryInternal(request, CUSTOMER_SELF_SERVICE_OPERATOR);
    }

    @Transactional(noRollbackFor = BusinessException.class, isolation = Isolation.READ_COMMITTED)
    @Override
    public PaymentDetailDTO query(PaymentQueryRequestDTO request, String operator) {
        return queryInternal(request, normalizeOperationOperator(operator));
    }

    private PaymentDetailDTO queryInternal(PaymentQueryRequestDTO request, String operator) {
        if (request == null) {
            throw invalidPaymentRequest("支付查单请求不能为空");
        }
        request.setPaymentOrderId(normalizeRequiredRequestField(
                request.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        PaymentDetailDTO detail = requireRequestedPaymentDetail(
                paymentMapper.findDetail(request.getPaymentOrderId()), request.getPaymentOrderId());
        requireNonSuccessfulPaymentPrepayBinding(detail);
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
            validatePaymentSuccessFacts(detail);
            return outwardDetail(detail);
        }
        if ("CLOSED".equalsIgnoreCase(detail.getStatus())
                && !StringUtils.hasText(detail.getChannelTransactionNo())
                && !StringUtils.hasText(detail.getLatestAttemptStatus())) {
            return outwardDetail(detail);
        }
        requireChannelSubmissionFact(detail);
        try {
            paymentChannelQueryRateLimitService.acquire(detail.getChannel(), detail.getPaymentOrderId());
        } catch (BusinessException exception) {
            recordChannelQueryFailure(detail, exception, operator);
            throw exception;
        }
        PaymentChannelQueryResultDTO queryResult = queryChannel(detail, operator);
        boolean requiresSettlement = "SUCCESS".equalsIgnoreCase(queryResult.getTradeStatus())
                && !"SUCCESS".equalsIgnoreCase(detail.getStatus());
        if (requiresSettlement) {
            detail = lockAndReloadPaymentForSettlement(detail);
            requireChannelSubmissionFact(detail);
        }
        String queriedChannelTransactionNo = queryResult.getChannelTransactionNo();
        if (StringUtils.hasText(detail.getChannelTransactionNo())
                && StringUtils.hasText(queriedChannelTransactionNo)
                && !detail.getChannelTransactionNo().equals(queriedChannelTransactionNo)) {
            BusinessException queryFailure = new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "channel query transaction does not match payment transaction");
            recordChannelQueryFailure(detail, queryFailure, operator);
            throw queryFailure;
        }
        String channelTransactionNo = StringUtils.hasText(queriedChannelTransactionNo)
                ? queriedChannelTransactionNo
                : detail.getChannelTransactionNo();
        if ("SUCCESS".equalsIgnoreCase(queryResult.getTradeStatus())
                && !"SUCCESS".equalsIgnoreCase(detail.getStatus())
                && !StringUtils.hasText(channelTransactionNo)) {
            BusinessException queryFailure = new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "channel transaction number is required to settle a successful query");
            recordChannelQueryFailure(detail, queryFailure, operator);
            throw queryFailure;
        }
        applyReturnCodeStandardization(detail, queryResult);
        int snapshotRows = paymentMapper.updatePaymentReturnCodeSnapshot(
                detail.getPaymentOrderId(),
                queryResult.getChannelReturnCode(),
                queryResult.getStandardizedCode(),
                queryResult.getStandardizedMessage(),
                queryResult.getHandlingSuggestion(),
                queryResult.getRetryable(),
                queryResult.getManualInterventionRequired(),
                queryResult.getMappingVersion(),
                queryResult.getReturnCodeMappingMatched());
        if (snapshotRows == 0) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
            if (latestDetail != null && "SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                if (!detail.getChannel().equals(latestDetail.getChannel())
                        || StringUtils.hasText(queryResult.getChannelTransactionNo())
                        && !queryResult.getChannelTransactionNo().equals(latestDetail.getChannelTransactionNo())) {
                    BusinessException queryFailure = new BusinessException(
                            ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                            "channel query transaction does not match payment transaction");
                    recordChannelQueryFailure(latestDetail, queryFailure, operator);
                    throw queryFailure;
                }
                validatePaymentSuccessFacts(latestDetail);
                latestDetail.setQuerySource(queryResult.getQuerySource());
                recordChannelQuery(latestDetail, queryResult, operator);
                return outwardDetail(latestDetail);
            }
        }
        if (snapshotRows != 1) {
            throw new IllegalStateException("payment query return code snapshot was not applied atomically");
        }
        detail.setChannelTransactionNo(channelTransactionNo);
        detail.setQuerySource(queryResult.getQuerySource());
        recordChannelQuery(detail, queryResult, operator);
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())
                || !"SUCCESS".equalsIgnoreCase(queryResult.getTradeStatus())) {
            if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
                validatePaymentSuccessFacts(detail);
            }
            return outwardDetail(detail);
        }
        int settledRows = paymentMapper.markPaymentSuccessFromChannelResult(
                detail.getPaymentOrderId(), channelTransactionNo);
        if (settledRows > 1) {
            throw new IllegalStateException("payment query success update affected multiple rows");
        }
        if (settledRows != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
            if (latestDetail == null) {
                throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
            }
            if (!"SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    && (!"CLOSED".equalsIgnoreCase(latestDetail.getStatus())
                    || !Boolean.TRUE.equals(latestDetail.getPrepayAmountBindingValid()))) {
                throw new IllegalStateException("payment success confirmation was not applied atomically");
            }
            if ("CLOSED".equalsIgnoreCase(latestDetail.getStatus())) {
                BusinessException queryFailure = new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                        "channel reports success but payment remains closed; manual reconciliation required");
                recordChannelQueryFailure(latestDetail, queryFailure, operator);
                throw queryFailure;
            }
            if ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                if (!detail.getChannel().equals(latestDetail.getChannel())
                        || !channelTransactionNo.equals(latestDetail.getChannelTransactionNo())) {
                    BusinessException queryFailure = new BusinessException(
                            ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                            "channel query transaction does not match payment transaction");
                    recordChannelQueryFailure(latestDetail, queryFailure, operator);
                    throw queryFailure;
                }
                validatePaymentSuccessFacts(latestDetail);
            }
            latestDetail.setQuerySource(queryResult.getQuerySource());
            applyReturnCodeStandardization(latestDetail, queryResult);
            return outwardDetail(latestDetail);
        }
        parseAmount(detail.getAmount());
        finalizeLatestPaymentAttemptSuccess(detail.getPaymentOrderId());
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                detail.getPaymentOrderId(), "支付成功", "success") != 1) {
            throw new IllegalStateException(
                    "payment query prepay status was not applied atomically");
        }
        settleOrderAndBill(detail);
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"channel\":\"" + escapeJson(detail.getChannel())
                + "\",\"payScene\":\"" + escapeJson(detail.getPayScene())
                + "\",\"source\":\"ACTIVE_QUERY\",\"initiatedBy\":\""
                + escapeJson(operator) + "\"}";
        insertEvent(
                eventNo,
                "PAYMENT_SUCCESS",
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                eventPayload);
        recordEventProcessLog(
                eventNo, "PAYMENT_SUCCESS", detail.getPaymentOrderId(), detail.getOrderNo());
        dispatchPaymentSuccessAfterCommit(eventNo, detail.getPaymentOrderId());
        paymentMapper.releaseSubmitConcurrencyToken(detail.getPaymentOrderId(), "ACTIVE_QUERY_SUCCESS");
        PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
        if (latestDetail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        latestDetail.setQuerySource(queryResult.getQuerySource());
        applyReturnCodeStandardization(latestDetail, queryResult);
        return outwardDetail(latestDetail);
    }

    private void finalizeLatestPaymentAttemptSuccess(String paymentOrderId) {
        if (paymentMapper.finalizeLatestPaymentAttemptSuccess(paymentOrderId) != 1) {
            throw new IllegalStateException("payment attempt finalization was not applied atomically");
        }
    }

    private void validatePaymentSuccessFacts(PaymentDetailDTO detail) {
        if (!"SUCCESS".equalsIgnoreCase(detail.getStatus())
                || !Boolean.TRUE.equals(detail.getPrepayAmountBindingValid())
                || !"success".equalsIgnoreCase(detail.getStatusType())
                || !"成功".equals(detail.getLatestAttemptStatus())
                || !"success".equalsIgnoreCase(detail.getLatestAttemptStatusType())
                || !StringUtils.hasText(detail.getLatestAttemptCompletedAt())
                || !StringUtils.hasText(detail.getSuccessAt())
                || !StringUtils.hasText(detail.getCreatedAt())
                || !StringUtils.hasText(detail.getChannel())
                || !StringUtils.hasText(detail.getChannelTransactionNo())
                || !Boolean.TRUE.equals(detail.getPaymentFactTimelineValid())
                || !"STANDARD_ORDER".equals(detail.getSourceKind())
                        && !"VIRTUAL".equals(detail.getSourceKind())) {
            throw new IllegalStateException("payment success facts are inconsistent");
        }
        try {
            BigDecimal currentOrderAmount = paymentMapper.findOrderAmount(detail.getOrderNo());
            if ("STANDARD_ORDER".equals(detail.getSourceKind())) {
                if (currentOrderAmount == null || paymentMapper.findPaidAmount(detail.getOrderNo()) == null) {
                    throw new IllegalStateException("payment success facts are inconsistent");
                }
            } else if (currentOrderAmount != null
                    || paymentMapper.findPaidAmount(detail.getOrderNo()) != null) {
                throw new IllegalStateException("payment success facts are inconsistent");
            }
            parseAmount(detail.getAmount());
            LocalDateTime attemptCompletedAt = LocalDateTime.parse(
                    detail.getLatestAttemptCompletedAt().trim(), PAYMENT_FACT_TIME_FORMATTER);
            LocalDateTime successAt = LocalDateTime.parse(
                    detail.getSuccessAt().trim(), PAYMENT_FACT_TIME_FORMATTER);
            LocalDateTime createdAt = LocalDateTime.parse(
                    detail.getCreatedAt().trim(), PAYMENT_FACT_TIME_FORMATTER);
            if (createdAt.isAfter(attemptCompletedAt) || createdAt.isAfter(successAt)
                    || attemptCompletedAt.isAfter(successAt)) {
                throw new IllegalStateException("payment success facts are inconsistent");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("payment success facts are inconsistent", exception);
        }
    }

    private void dispatchPaymentSuccessAfterCommit(String eventNo, String paymentOrderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tryDispatchPaymentSuccess(eventNo, paymentOrderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryDispatchPaymentSuccess(eventNo, paymentOrderId);
            }
        });
    }

    private void tryDispatchPaymentSuccess(String eventNo, String paymentOrderId) {
        try {
            paymentEventDispatchService.publishPaymentSuccess(eventNo, paymentOrderId);
        } catch (RuntimeException exception) {
            LOGGER.warn("支付成功事件即时派发失败 eventNo={}, exceptionType={}",
                    eventNo, exception.getClass().getName());
        }
    }

    private void requireWaitingChannelSubmissionFact(String paymentOrderId, PaymentDetailDTO detail) {
        if (detail == null
                || !paymentOrderId.equals(detail.getPaymentOrderId())
                || !"WAIT_CALLBACK".equalsIgnoreCase(detail.getStatus())
                || !StringUtils.hasText(detail.getChannel())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                    "payment has no bound channel submission fact");
        }
        requireChannelSubmissionFact(detail);
    }

    private void requireChannelSubmissionFact(PaymentDetailDTO detail) {
        if (!Boolean.TRUE.equals(detail.getPaymentSubmissionChannelValid())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                    "payment attempt channel does not match payment channel");
        }
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
            return;
        }
        if ((!"WAIT_CALLBACK".equalsIgnoreCase(detail.getStatus())
                && !"CLOSED".equalsIgnoreCase(detail.getStatus()))
                || !StringUtils.hasText(detail.getLatestAttemptStatus())
                || isTerminalAttemptStatus(detail.getLatestAttemptStatus())
                || detail.getLatestAttemptCompletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                    "payment has no eligible channel submission fact");
        }
    }

    private PaymentChannelQueryResultDTO queryChannel(PaymentDetailDTO detail, String operator) {
        try {
            return PaymentChannelQueryResultValidator.validateAndNormalize(
                    paymentChannelQueryService.query(detail));
        } catch (BusinessException exception) {
            recordChannelQueryFailure(detail, exception, operator);
            throw exception;
        } catch (RuntimeException exception) {
            BusinessException queryFailure = BusinessException.sanitized(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "payment channel query failed unexpectedly");
            recordChannelQueryFailure(detail, queryFailure, operator);
            throw queryFailure;
        }
    }

    private PaymentDetailDTO lockAndReloadPaymentForSettlement(PaymentDetailDTO detail) {
        String orderNo = detail.getOrderNo();
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付单缺少订单号，无法完成成功收口");
        }
        paymentMapper.initPrepayCreationLock(orderNo);
        String lockedOrderNo = paymentMapper.lockPrepayCreation(orderNo);
        if (!orderNo.equals(lockedOrderNo)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付成功收口并发锁获取失败");
        }
        PaymentDetailDTO latestDetail = paymentMapper.findDetail(detail.getPaymentOrderId());
        if (latestDetail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        if (!orderNo.equals(latestDetail.getOrderNo())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付单关联订单在成功收口前发生变化");
        }
        parseAmount(latestDetail.getAmount());
        if (!Boolean.TRUE.equals(latestDetail.getPrepayAmountBindingValid())) {
            throw new IllegalStateException("prepay and payment order amounts are inconsistent");
        }
        return latestDetail;
    }

    private void recordChannelQuery(
            PaymentDetailDTO detail,
            PaymentChannelQueryResultDTO queryResult,
            String operator) {
        String notifyNo = buildBusinessNo("QRY");
        String responsePayload = buildChannelQueryResponsePayload(queryResult);
        String statusType = resolveChannelQueryStatusType(queryResult.getTradeStatus());
        insertNotifyLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getChannel(),
                queryResult.getTradeStatus(),
                "{\"querySource\":\"" + escapeJson(queryResult.getQuerySource())
                        + "\",\"initiatedBy\":\"" + escapeJson(operator) + "\"}",
                responsePayload,
                "已查单",
                statusType);
        recordQueryProcessLog(notifyNo, detail, queryResult.getTradeStatus(), "已查单", statusType, operator);
    }

    private String resolveChannelQueryStatusType(String tradeStatus) {
        if ("SUCCESS".equalsIgnoreCase(tradeStatus)) {
            return "success";
        }
        if ("FAIL".equalsIgnoreCase(tradeStatus)
                || "CLOSED".equalsIgnoreCase(tradeStatus)
                || "RISK_BLOCKED".equalsIgnoreCase(tradeStatus)
                || "RISK_REJECTED".equalsIgnoreCase(tradeStatus)) {
            return "danger";
        }
        return "info";
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

    private void recordChannelQueryFailure(
            PaymentDetailDTO detail, BusinessException exception, String operator) {
        String notifyNo = buildBusinessNo("QRY");
        String responsePayload = "{\"code\":\"" + escapeJson(exception.getCode())
                + "\",\"message\":\"channel query failed\"}";
        insertNotifyLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getChannel(),
                "QUERY_FAILED",
                "{\"querySource\":\"CHANNEL_QUERY\",\"initiatedBy\":\""
                        + escapeJson(operator) + "\"}",
                responsePayload,
                "查单失败",
                "danger");
        recordQueryProcessLog(notifyNo, detail, "QUERY_FAILED", "查单失败", "danger", operator);
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
        if (!StringUtils.hasText(detail.getBillNo())) {
            throw new IllegalStateException("payment bill binding is missing");
        }
        BigDecimal latestOrderAmount = paymentMapper.findOrderAmount(detail.getOrderNo());
        BigDecimal settledAmount;
        BigDecimal expectedBillPaidAmount = BigDecimal.ZERO;
        if ("VIRTUAL".equals(detail.getSourceKind())) {
            if (latestOrderAmount != null || paymentMapper.findPaidAmount(detail.getOrderNo()) != null) {
                throw new IllegalStateException("payment settlement virtual source has changed");
            }
            settledAmount = parseAmount(detail.getAmount());
        } else if ("STANDARD_ORDER".equals(detail.getSourceKind())) {
            BigDecimal paidAmount = paymentMapper.findPaidAmount(detail.getOrderNo());
            String customerName = paymentMapper.findCustomerNameByOrderNo(detail.getOrderNo());
            if (latestOrderAmount == null || paidAmount == null || !StringUtils.hasText(customerName)) {
                throw new IllegalStateException("payment settlement standard order source is incomplete");
            }
            if (latestOrderAmount.compareTo(BigDecimal.ZERO) <= 0 || paidAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("payment settlement standard order amount is invalid");
            }
            BigDecimal remainAmount = latestOrderAmount.subtract(paidAmount);
            if (remainAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("payment settlement standard order is already settled");
            }
            if (!customerName.equals(detail.getCustomerName())
                    || remainAmount.compareTo(parseAmount(detail.getAmount())) != 0) {
                throw new IllegalStateException("payment settlement standard order facts have changed");
            }
            settledAmount = latestOrderAmount;
            expectedBillPaidAmount = paidAmount;
            if (paymentMapper.updateOrderAfterPayment(
                    detail.getOrderNo(), settledAmount, "待履约", "info", paidAmount, customerName) != 1) {
                throw new IllegalStateException("payment order settlement was not applied atomically");
            }
        } else {
            throw new IllegalStateException("payment settlement source kind is unknown");
        }
        if (paymentMapper.updateBillAfterPayment(
                detail.getBillNo(), settledAmount, "已结清", "success",
                detail.getOrderNo(), detail.getCustomerName(), expectedBillPaidAmount) != 1) {
            throw new IllegalStateException("payment bill settlement was not applied atomically");
        }
    }

    @Transactional
    @Override
    public PaymentDetailDTO close(PaymentCloseRequestDTO request) {
        return closeInternal(request, CUSTOMER_SELF_SERVICE_OPERATOR);
    }

    @Transactional
    @Override
    public PaymentDetailDTO close(PaymentCloseRequestDTO request, String operator) {
        return closeInternal(request, normalizeOperationOperator(operator));
    }

    private PaymentDetailDTO closeInternal(PaymentCloseRequestDTO request, String operator) {
        if (request == null) {
            throw invalidPaymentRequest("支付关闭请求不能为空");
        }
        request.setPaymentOrderId(normalizeRequiredRequestField(
                request.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        PaymentDetailDTO detail = requireRequestedPaymentDetail(
                paymentMapper.findDetail(request.getPaymentOrderId()), request.getPaymentOrderId());
        requireNonSuccessfulPaymentPrepayBinding(detail);
        if ("SUCCESS".equalsIgnoreCase(detail.getStatus())) {
            validatePaymentSuccessFacts(detail);
            paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CLOSE_ALREADY_FINALIZED");
            return outwardDetail(detail);
        }
        if ("CLOSED".equalsIgnoreCase(detail.getStatus())) {
            if (!StringUtils.hasText(detail.getChannelTransactionNo())
                    && !StringUtils.hasText(detail.getLatestAttemptStatus())) {
                paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "CLOSE_ALREADY_FINALIZED");
            }
            return outwardDetail(detail);
        }
        if (paymentMapper.markUnsubmittedPaymentClosed(request.getPaymentOrderId()) != 1) {
            PaymentDetailDTO latestDetail = paymentMapper.findDetail(request.getPaymentOrderId());
            requireNonSuccessfulPaymentPrepayBinding(latestDetail);
            if (latestDetail != null && ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())
                    || "CLOSED".equalsIgnoreCase(latestDetail.getStatus()))) {
                if ("SUCCESS".equalsIgnoreCase(latestDetail.getStatus())) {
                    validatePaymentSuccessFacts(latestDetail);
                }
                if ("CLOSED".equalsIgnoreCase(latestDetail.getStatus())
                        && !StringUtils.hasText(latestDetail.getChannelTransactionNo())
                        && !StringUtils.hasText(latestDetail.getLatestAttemptStatus())) {
                    paymentMapper.releaseSubmitConcurrencyToken(
                            request.getPaymentOrderId(), "CLOSE_ALREADY_FINALIZED");
                }
                return outwardDetail(latestDetail);
            }
            throw new BusinessException(
                    ErrorCode.PAYMENT_CLOSE_CHANNEL_CONFIRMATION_REQUIRED,
                    "支付已提交渠道或结果尚未确认，请先主动查单");
        }
        // 关闭支付单时同步记录事件，后续可以从事件轨迹里看到是谁、什么时候关闭的。
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                request.getPaymentOrderId(), "已关闭", "danger") != 1) {
            throw new IllegalStateException(
                    "payment close prepay status was not applied atomically");
        }
        String eventNo = buildBusinessNo("EVT");
        String eventPayload = "{\"close\":\"manual\",\"initiatedBy\":\""
                + escapeJson(operator) + "\"}";
        insertEvent(
                eventNo, "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo(), eventPayload);
        recordEventProcessLog(
                eventNo, "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo(), operator);
        paymentMapper.releaseSubmitConcurrencyToken(request.getPaymentOrderId(), "MANUAL_CLOSE");
        return outwardDetail(requirePaymentDetailResponse(
                paymentMapper.findDetail(request.getPaymentOrderId())));
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
        recordEventProcessLog(eventNo, eventType, paymentOrderId, orderNo, "payment-core");
    }

    private void recordEventProcessLog(
            String eventNo,
            String eventType,
            String paymentOrderId,
            String orderNo,
            String source) {
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
                source);
    }

    private void recordQueryProcessLog(
            String notifyNo,
            PaymentDetailDTO detail,
            String queryStatus,
            String statusLabel,
            String statusType,
            String operator) {
        recordProcessLog(
                notifyNo,
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                null,
                "PAYMENT_NOTIFY",
                "主动查单",
                statusType,
                "查单状态=" + queryStatus + "；处理状态=" + statusLabel,
                operator);
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
        if (paymentMapper.insertPaymentProcessLog(processLog) != 1) {
            throw new IllegalStateException("payment process log was not created atomically");
        }
    }

    private void insertNotifyLog(
            String notifyNo,
            String paymentOrderId,
            String channelCode,
            String notifyType,
            String notifyPayload,
            String notifyResult,
            String notifyStatus,
            String notifyStatusType) {
        if (paymentMapper.insertNotifyLog(
                notifyNo,
                paymentOrderId,
                channelCode,
                notifyType,
                notifyPayload,
                notifyResult,
                notifyStatus,
                notifyStatusType) != 1) {
            throw new IllegalStateException("payment notify log was not created atomically");
        }
    }

    private void insertEvent(
            String eventNo,
            String eventType,
            String paymentOrderId,
            String bizNo,
            String eventPayload) {
        if (paymentMapper.insertEvent(
                eventNo, eventType, paymentOrderId, bizNo, eventPayload) != 1) {
            throw new IllegalStateException("payment event was not created atomically");
        }
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
        request.setRemark(normalizeOptionalRequestField(
                request.getRemark(), "业务说明", PREPAY_REMARK_MAX_LENGTH));
        if (request.getAmount() != null) {
            validatePrepayAmount(request.getAmount());
        }
    }

    private void validatePrepayAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalidPaymentRequest("预付金额必须大于 0");
        }
        if (amount.scale() > MONEY_MAX_SCALE || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw invalidPaymentRequest("预付金额最多支持 16 位整数和 2 位小数");
        }
    }

    private void validateActivePrepayRequest(PrepayOrderDTO activePrepay, PrepayRequestDTO request) {
        boolean conflict = !request.getPayScene().equals(activePrepay.getPayScene())
                || (request.getCustomerName() != null
                    && !request.getCustomerName().equals(activePrepay.getCustomerName()))
                || (request.getAmount() != null
                    && request.getAmount().compareTo(parseAmount(activePrepay.getAmount())) != 0)
                || !resolveCashierTitle(request).equals(activePrepay.getCashierTitle());
        if (conflict) {
            throw prepayRequestConflict("同一订单已有参数不一致的有效预付单");
        }
    }

    private void validateStandardPrepayRequest(
            PrepayRequestDTO request, String customerName, BigDecimal remainAmount) {
        boolean conflict = (request.getCustomerName() != null
                    && !request.getCustomerName().equals(customerName))
                || (request.getAmount() != null
                    && request.getAmount().compareTo(remainAmount) != 0);
        if (conflict) {
            throw prepayRequestConflict("预付请求与订单当前客户或剩余应付不一致");
        }
    }

    private void validateActivePrepayOrderFacts(
            PrepayOrderDTO activePrepay, String customerName, BigDecimal remainAmount) {
        if (!customerName.equals(activePrepay.getCustomerName())
                || remainAmount.compareTo(parseAmount(activePrepay.getAmount())) != 0) {
            throw prepayRequestConflict("有效预付单与订单当前客户或剩余应付不一致");
        }
    }

    private void validateVirtualPrepayRequest(PrepayRequestDTO request) {
        if (!StringUtils.hasText(request.getCustomerName()) || request.getAmount() == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                    "虚拟支付场景缺少订单号、客户名称或金额");
        }
    }

    private String resolveCashierTitle(PrepayRequestDTO request) {
        return StringUtils.hasText(request.getCashierTitle())
                ? request.getCashierTitle()
                : "家政服务收银台";
    }

    private BusinessException prepayRequestConflict(String message) {
        return new BusinessException(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, message);
    }

    private String normalizeRequiredRequestField(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalRequestField(value, fieldName, maxLength);
        if (normalized == null) {
            throw invalidPaymentRequest(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalRequestField(String value, String fieldName, int maxLength) {
        if (value != null) {
            for (int index = 0; index < value.length(); index++) {
                if (Character.isISOControl(value.charAt(index))) {
                    throw invalidPaymentRequest(fieldName + "不能包含控制字符");
                }
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw invalidPaymentRequest(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        String normalized = value.trim();
        return normalized;
    }

    private BusinessException invalidPaymentRequest(String message) {
        return new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, message);
    }

    private String normalizeOperationOperator(String operator) {
        if (operator != null) {
            for (int index = 0; index < operator.length(); index++) {
                if (Character.isISOControl(operator.charAt(index))) {
                    throw invalidPaymentRequest("操作人不能包含控制字符");
                }
            }
        }
        return normalizeRequiredRequestField(operator, "操作人", OPERATOR_MAX_LENGTH);
    }

    private String buildIdempotencyKey(
            PaymentSubmitRequestDTO request,
            PrepayOrderDTO currentPrepay,
            String resolvedChannelCode) {
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            return request.getIdempotencyKey().trim();
        }
        String combined = currentPrepay.getPrepayOrderNo()
                + "|" + request.getPaymentMethod() + "|" + resolvedChannelCode;
        if (combined.length() <= SUBMIT_IDEMPOTENCY_KEY_MAX_LENGTH) {
            return combined;
        }
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (String value : Arrays.asList(
                currentPrepay.getPrepayOrderNo(), request.getPaymentMethod(), resolvedChannelCode)) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
            digest.update(bytes);
        }
        return "AUTO:" + hexDigest(digest.digest());
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
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(idempotencyKey)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                    "支付幂等键包含无效字符");
        }
    }

    private void validateSubmitIdempotencyOwnership(
            String idempotencyKey, String prepayOrderNo, String paymentOrderId,
            String requestFingerprint) {
        PaymentAttemptIdempotencyDTO existing = paymentMapper.findPaymentAttemptIdempotency(idempotencyKey);
        if (existing == null
                || !prepayOrderNo.equals(existing.getPrepayOrderNo())
                || !paymentOrderId.equals(existing.getPaymentOrderId())
                || !requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                    "支付幂等键已被其他支付单或不同提交内容使用");
        }
    }

    String submitRequestFingerprint(
            PaymentSubmitRequestDTO request, PrepayOrderDTO prepay, String paymentOrderId,
            String sourceAppId, String resolvedChannelCode, String terminal) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (String value : Arrays.asList(
                request.getPrepayOrderNo(), paymentOrderId, parseAmount(prepay.getAmount()).toPlainString(),
                request.getPaymentMethod(), resolvedChannelCode, sourceAppId, request.getMerchantNo(),
                request.getAccessToken(), terminal, request.getClientDeviceId(), request.getPayerPhone())) {
            int length = value == null ? -1 : value.length();
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
            if (value != null) {
                for (int index = 0; index < value.length(); index++) {
                    char character = value.charAt(index);
                    digest.update((byte) (character >>> 8));
                    digest.update((byte) character);
                }
            }
        }
        return hexDigest(digest.digest());
    }

    private String hexDigest(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
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
        validateSubmitIdempotencyKeyControlCharacters(request.getIdempotencyKey());
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            validateSubmitIdempotencyKey(request.getIdempotencyKey());
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
        validateSubmitControlCharacters(terminal, "终端标识");
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
        if ("MINI_PROGRAM".equals(normalized)
                || "WECHAT_MINI_PROGRAM".equals(normalized)
                || "WX_MINI_PROGRAM".equals(normalized)
                || "小程序".equals(normalized)) {
            return "MINI_PROGRAM";
        }
        if ("API".equals(normalized)) {
            return normalized;
        }
        if ("UNKNOWN".equals(normalized)) {
            return normalized;
        }
        throw invalidSubmitRequest("终端标识仅支持 APP、H5、PC、MINI_PROGRAM、API 或 UNKNOWN");
    }

    private String normalizeRequiredSubmitField(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalSubmitField(value, fieldName, maxLength);
        if (normalized == null) {
            throw invalidSubmitRequest(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalSubmitField(String value, String fieldName, int maxLength) {
        validateSubmitControlCharacters(value, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw invalidSubmitRequest(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        String normalized = value.trim();
        validateRequiredSubmitField(normalized, fieldName, maxLength);
        return normalized;
    }

    private void validateSubmitControlCharacters(String value, String fieldName) {
        if (value == null) {
            return;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidSubmitRequest(fieldName + "不能包含控制字符");
            }
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw invalidSubmitRequest(fieldName + "包含无效字符");
        }
    }

    private void validateSubmitIdempotencyKeyControlCharacters(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        for (int index = 0; index < idempotencyKey.length(); index++) {
            if (Character.isISOControl(idempotencyKey.charAt(index))) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                        "支付幂等键不能包含控制字符");
            }
        }
    }

    private void validateRequiredSubmitField(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalidSubmitRequest(fieldName + "不能为空");
        }
        if (value.length() > maxLength) {
            throw invalidSubmitRequest(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        validateSubmitControlCharacters(value, fieldName);
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
            if (paymentMapper.insertSubmitConcurrencyToken(
                    prepayOrderNo,
                    paymentOrderId,
                    sourceAppId,
                    idempotencyKey,
                    terminal,
                    clientIp,
                    SUBMIT_CONCURRENCY_TOKEN_EXPIRE_SECONDS) != 1) {
                throw new IllegalStateException("payment submit concurrency token was not created atomically");
            }
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
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows > 1) {
            throw new IllegalStateException("payment submit concurrency token update affected multiple rows");
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
        if (!"开启".equals(controlPolicy.getTokenAuthRequired())
                && !"关闭".equals(controlPolicy.getTokenAuthRequired())) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, "当前来源应用令牌鉴权配置异常");
        }
        if ("开启".equals(controlPolicy.getTokenAuthRequired())
                && !matchesAccessToken(controlPolicy.getAccessTokenValue(), accessToken)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, "当前请求的访问令牌无效或已失效");
        }
        if (!"开启".equals(controlPolicy.getStrictMode())
                && !"关闭".equals(controlPolicy.getStrictMode())) {
            throw new BusinessException(ErrorCode.PAYMENT_SUBMIT_SELF_CHECK_BLOCKED, "当前来源应用严格模式配置异常");
        }
        if ("开启".equals(controlPolicy.getStrictMode())
                && !"PASS".equals(controlPolicy.getSelfCheckStatus())) {
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
            String sourceAppId,
            String resolvedChannelCode,
            String terminal,
            String clientIp) {
        PaymentRiskDecisionRequestDTO decisionRequest = new PaymentRiskDecisionRequestDTO();
        decisionRequest.setBusinessNo(paymentOrderId);
        decisionRequest.setSourceSystem("payment-core");
        decisionRequest.setSourceAppId(sourceAppId);
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
            throw new IllegalArgumentException("支付金额不能为空");
        }
        String normalizedAmount = amountText
                .replace("¥", "")
                .replace(",", "")
                .trim();
        final BigDecimal amount;
        try {
            amount = new BigDecimal(normalizedAmount);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("支付金额格式非法", exception);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE
                || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new IllegalArgumentException("支付金额必须大于0且最多保留2位小数");
        }
        return amount;
    }

    private PaymentDetailDTO enrichDetail(PaymentDetailDTO detail) {
        if (detail == null) {
            return null;
        }
        if (StringUtils.hasText(detail.getPaymentOrderId())) {
            detail.setBusinessChainId("CHAIN-" + detail.getPaymentOrderId().trim());
        }
        detail.setRouteLogs(safeList(paymentMapper.findRouteLogs(detail.getPaymentOrderId())));
        detail.setNotifyLogs(safeList(paymentMapper.findNotifyLogs(detail.getPaymentOrderId())));
        detail.setEventLogs(safeList(paymentMapper.findEventItems(detail.getPaymentOrderId())));
        return detail;
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

    private List<PaymentListItemDTO> maskPaymentItems(List<PaymentListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .filter(item -> item != null)
                .map(this::maskedPaymentItemCopy)
                .collect(Collectors.toList());
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

    private PaymentDetailDTO requirePaymentDetailResponse(PaymentDetailDTO detail) {
        if (detail == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付单已更新但读取失败");
        }
        return detail;
    }

    private PaymentDetailDTO requireRequestedPaymentDetail(PaymentDetailDTO detail, String paymentOrderId) {
        if (detail == null || !paymentOrderId.equals(detail.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在");
        }
        return detail;
    }

    private void requireNonSuccessfulPaymentPrepayBinding(PaymentDetailDTO detail) {
        if (detail != null && !"SUCCESS".equalsIgnoreCase(detail.getStatus())
                && !Boolean.TRUE.equals(detail.getPrepayAmountBindingValid())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                    "支付单预付归属或金额不可确认");
        }
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
    private PaymentListQueryDTO normalizeQuery(PaymentListQueryDTO query) {
        if (query == null) {
            query = new PaymentListQueryDTO();
        }
        query.setPaymentOrderId(normalizeOptionalRequestField(
                query.getPaymentOrderId(), "支付单号", PAYMENT_ORDER_ID_MAX_LENGTH));
        query.setOrderNo(normalizeOptionalRequestField(
                query.getOrderNo(), "订单号", BUSINESS_ORDER_NO_MAX_LENGTH));
        query.setChannelCode(normalizeOptionalRequestField(
                query.getChannelCode(), "渠道编码", PAYMENT_LIST_CHANNEL_CODE_MAX_LENGTH));
        String paymentMethod = normalizeOptionalRequestField(
                query.getPaymentMethod(), "支付方式", PAYMENT_LIST_PAYMENT_METHOD_MAX_LENGTH);
        query.setPaymentMethod(paymentMethod == null ? "全部" : paymentMethod);
        String status = normalizeOptionalRequestField(
                query.getStatus(), "支付状态", PAYMENT_LIST_STATUS_MAX_LENGTH);
        query.setStatus(status == null ? "全部" : status);
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return query;
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SanitizedSubmitTransactionException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final boolean duplicateKey;

        private SanitizedSubmitTransactionException(RuntimeException exception) {
            super(SUBMIT_TRANSACTION_FAILURE_CODE + ":" + exceptionName(exception), null, false, false);
            this.duplicateKey = exception instanceof DuplicateKeyException;
        }

        private static String exceptionName(RuntimeException exception) {
            String simpleName = exception.getClass().getSimpleName();
            return simpleName.isEmpty() ? RuntimeException.class.getSimpleName() : simpleName;
        }
    }
}
