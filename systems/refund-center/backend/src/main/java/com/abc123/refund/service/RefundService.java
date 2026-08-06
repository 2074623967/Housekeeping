package com.abc123.refund.service;

import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;

/**
 * 退款中心业务服务。
 */
public interface RefundService {

    PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query);

    RefundDetailDTO detail(String refundOrderId);

    RefundOverviewDTO overview();

    PageResultDTO<RefundOutboxItemDTO> listOutbox(RefundOutboxQueryDTO query);

    RefundListItemDTO apply(RefundApplyRequestDTO request);

    RefundListItemDTO approve(RefundActionRequestDTO request);

    RefundListItemDTO submit(RefundActionRequestDTO request);

    RefundListItemDTO callback(RefundCallbackRequestDTO request);

    RefundListItemDTO retry(RefundActionRequestDTO request);

    RefundOutboxItemDTO dispatchOutbox(String eventId, RefundOutboxDispatchRequestDTO request);

    void projectPaymentSuccess(PaymentSuccessProjectionDTO request);
}
