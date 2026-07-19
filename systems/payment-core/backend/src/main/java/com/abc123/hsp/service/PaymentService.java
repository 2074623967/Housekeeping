package com.abc123.hsp.service;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.repository.PaymentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public List<PaymentListItemDTO> list() {
        return paymentRepository.findAll();
    }

    public PaymentDetailDTO detail(String paymentOrderId) {
        return paymentRepository.enrichDetail(paymentRepository.findDetail(paymentOrderId));
    }

    public PrepayOrderDTO prepay(PrepayRequestDTO request) {
        return paymentRepository.createPrepay(request.getOrderNo(), request.getPayScene());
    }

    public CashierPageDTO cashier(String prepayOrderNo) {
        return paymentRepository.findCashierPage(prepayOrderNo);
    }

    public PrepayOrderDTO submit(PaymentSubmitRequestDTO request) {
        String paymentOrderId = paymentRepository.findPaymentOrderIdByPrepayOrderNo(request.getPrepayOrderNo());
        if (!StringUtils.hasText(paymentOrderId)) {
            return null;
        }
        paymentRepository.ensurePaymentOrderByPrepay(request.getPrepayOrderNo(), request.getPaymentMethod(), request.getChannelCode());
        String routeNo = "RTR" + System.currentTimeMillis();
        paymentRepository.insertRouteRecord(routeNo, paymentOrderId, request.getChannelCode(), "默认渠道路由", request.getPaymentMethod());
        String attemptNo = "ATT" + System.currentTimeMillis();
        paymentRepository.insertEvent("EVT" + System.currentTimeMillis(), "PAYMENT_SUBMIT", paymentOrderId, paymentRepository.findOrderNoByPrepayOrderNo(request.getPrepayOrderNo()), request.getChannelCode());
        paymentRepository.insertNotifyLog("NTF" + System.currentTimeMillis(), paymentOrderId, request.getChannelCode(), "SUBMIT", "{'method':'" + request.getPaymentMethod() + "'}", "{'code':'SUCCESS'}", "待回调", "warn");
        return paymentRepository.findPrepay(request.getPrepayOrderNo());
    }

    public PaymentDetailDTO callback(String channel, PaymentCallbackRequestDTO request) {
        PaymentDetailDTO detail = paymentRepository.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            return null;
        }
        paymentRepository.insertNotifyLog(
                "NTF" + System.currentTimeMillis(),
                request.getPaymentOrderId(),
                channel,
                request.getTradeStatus(),
                "{'tradeStatus':'" + request.getTradeStatus() + "'}",
                "{'code':'SUCCESS'}",
                "已收口",
                "success"
        );
        paymentRepository.updatePaymentStatus(
                request.getPaymentOrderId(),
                "SUCCESS".equalsIgnoreCase(request.getTradeStatus()) ? "SUCCESS" : "WAIT_CALLBACK",
                "SUCCESS".equalsIgnoreCase(request.getTradeStatus()) ? "success" : "warn",
                request.getChannelTransactionNo()
        );
        paymentRepository.insertEvent(
                "EVT" + System.currentTimeMillis(),
                "SUCCESS".equalsIgnoreCase(request.getTradeStatus()) ? "PAYMENT_SUCCESS" : "PAYMENT_PENDING",
                request.getPaymentOrderId(),
                detail.getOrderNo(),
                "{'channel':'" + channel + "'}"
        );
        return paymentRepository.enrichDetail(paymentRepository.findDetail(request.getPaymentOrderId()));
    }

    public PaymentDetailDTO query(PaymentQueryRequestDTO request) {
        return paymentRepository.enrichDetail(paymentRepository.findDetail(request.getPaymentOrderId()));
    }

    public PaymentDetailDTO close(PaymentCloseRequestDTO request) {
        PaymentDetailDTO detail = paymentRepository.findDetail(request.getPaymentOrderId());
        if (detail == null) {
            return null;
        }
        paymentRepository.updatePaymentStatus(request.getPaymentOrderId(), "CLOSED", "danger", detail.getChannelTransactionNo());
        paymentRepository.insertEvent("EVT" + System.currentTimeMillis(), "PAYMENT_CLOSED", request.getPaymentOrderId(), detail.getOrderNo(), "{'close':'manual'}");
        return paymentRepository.enrichDetail(paymentRepository.findDetail(request.getPaymentOrderId()));
    }
}
