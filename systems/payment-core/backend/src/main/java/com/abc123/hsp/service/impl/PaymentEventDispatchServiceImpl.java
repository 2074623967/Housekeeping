package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 支付事件下游投递实现。
 */
@Service
public class PaymentEventDispatchServiceImpl implements PaymentEventDispatchService {

    private static final String PAYMENT_SUCCESS_EVENT_TYPE = "PAYMENT_SUCCESS";

    private final PaymentMapper paymentMapper;
    private final PaymentEventMapper paymentEventMapper;
    private final RestTemplate restTemplate;
    private final String clearingUrl;
    private final String accountingUrl;
    private final String accountingPaymentSuccessAccountNo;

    @Autowired
    public PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            @Value("${payment.downstream.clearing.payment-success-url:http://127.0.0.1:18120/api/clearing/events/payments/success}")
            String clearingUrl,
            @Value("${payment.downstream.accounting.payment-success-url:http://127.0.0.1:18110/api/accounting/events/payments/success}")
            String accountingUrl,
            @Value("${payment.downstream.accounting.payment-success-account-no:ACT10003}")
            String accountingPaymentSuccessAccountNo) {
        this(paymentMapper,
                paymentEventMapper,
                clearingUrl,
                accountingUrl,
                accountingPaymentSuccessAccountNo,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(3000));
    }

    PaymentEventDispatchServiceImpl(
            PaymentMapper paymentMapper,
            PaymentEventMapper paymentEventMapper,
            String clearingUrl,
            String accountingUrl,
            String accountingPaymentSuccessAccountNo,
            RestTemplate restTemplate) {
        this.paymentMapper = paymentMapper;
        this.paymentEventMapper = paymentEventMapper;
        this.clearingUrl = clearingUrl;
        this.accountingUrl = accountingUrl;
        this.accountingPaymentSuccessAccountNo = accountingPaymentSuccessAccountNo;
        this.restTemplate = restTemplate;
    }

    @Override
    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
        dispatchPaymentSuccess(eventNo, paymentOrderId);
    }

    @Override
    public boolean republish(String eventNo) {
        PaymentEventListItemDTO event = paymentEventMapper.findByEventNo(eventNo);
        if (event == null || !PAYMENT_SUCCESS_EVENT_TYPE.equals(event.getEventType())) {
            return false;
        }
        return dispatchPaymentSuccess(eventNo, event.getPaymentOrderId());
    }

    private boolean dispatchPaymentSuccess(String eventNo, String paymentOrderId) {
        PaymentDetailDTO detail = paymentMapper.findDetail(paymentOrderId);
        if (detail == null) {
            paymentEventMapper.markPublishFailed(eventNo);
            return false;
        }
        String workerName = paymentMapper.findWorkerNameByOrderNo(detail.getOrderNo());
        BigDecimal amount = parseAmount(detail.getAmount());
        try {
            postForSuccess(clearingUrl, buildClearingPayload(detail, workerName, amount));
            postForSuccess(accountingUrl, buildAccountingPayload(detail, amount));
            paymentEventMapper.markPublishSuccess(eventNo);
            return true;
        } catch (RuntimeException exception) {
            paymentEventMapper.markPublishFailed(eventNo);
            return false;
        }
    }

    private void postForSuccess(String url, Object payload) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        ResponseEntity<String> response = restTemplate.postForEntity(url.trim(), payload, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("下游系统响应非 2xx");
        }
    }

    private PaymentSuccessClearingPayload buildClearingPayload(PaymentDetailDTO detail, String workerName, BigDecimal amount) {
        return new PaymentSuccessClearingPayload(
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                LocalDate.now().toString(),
                detail.getCustomerName(),
                "家政平台",
                StringUtils.hasText(workerName) ? workerName : "待分配服务者",
                amount);
    }

    private PaymentSuccessAccountingPayload buildAccountingPayload(PaymentDetailDTO detail, BigDecimal amount) {
        return new PaymentSuccessAccountingPayload(
                accountingPaymentSuccessAccountNo,
                detail.getPaymentOrderId(),
                detail.getOrderNo(),
                detail.getCustomerName(),
                amount);
    }

    private BigDecimal parseAmount(String amountText) {
        String normalized = amountText == null ? "0" : amountText.replace("¥", "").replace(",", "").trim();
        return new BigDecimal(normalized);
    }

    /**
     * 清分事件载荷。
     */
    private static class PaymentSuccessClearingPayload {

        private final String paymentOrderId;
        private final String orderNo;
        private final String batchDate;
        private final String customerName;
        private final String merchantName;
        private final String workerName;
        private final BigDecimal amount;

        PaymentSuccessClearingPayload(String paymentOrderId,
                                      String orderNo,
                                      String batchDate,
                                      String customerName,
                                      String merchantName,
                                      String workerName,
                                      BigDecimal amount) {
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.batchDate = batchDate;
            this.customerName = customerName;
            this.merchantName = merchantName;
            this.workerName = workerName;
            this.amount = amount;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getBatchDate() {
            return batchDate;
        }

        public String getCustomerName() {
            return customerName;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public String getWorkerName() {
            return workerName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /**
     * 账务事件载荷。
     */
    private static class PaymentSuccessAccountingPayload {

        private final String accountNo;
        private final String paymentOrderId;
        private final String orderNo;
        private final String customerName;
        private final BigDecimal amount;

        PaymentSuccessAccountingPayload(String accountNo,
                                        String paymentOrderId,
                                        String orderNo,
                                        String customerName,
                                        BigDecimal amount) {
            this.accountNo = accountNo;
            this.paymentOrderId = paymentOrderId;
            this.orderNo = orderNo;
            this.customerName = customerName;
            this.amount = amount;
        }

        public String getAccountNo() {
            return accountNo;
        }

        public String getPaymentOrderId() {
            return paymentOrderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getCustomerName() {
            return customerName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
