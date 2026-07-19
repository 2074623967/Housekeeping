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
import java.util.List;

public interface PaymentService {

    List<PaymentListItemDTO> list();

    PaymentDetailDTO detail(String paymentOrderId);

    PrepayOrderDTO prepay(PrepayRequestDTO request);

    CashierPageDTO cashier(String prepayOrderNo);

    PrepayOrderDTO submit(PaymentSubmitRequestDTO request);

    PaymentDetailDTO callback(String channel, PaymentCallbackRequestDTO request);

    PaymentDetailDTO query(PaymentQueryRequestDTO request);

    PaymentDetailDTO close(PaymentCloseRequestDTO request);
}
