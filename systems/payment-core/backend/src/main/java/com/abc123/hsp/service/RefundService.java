package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;

public interface RefundService {

    /**
     * 查询退款单分页列表。
     */
    PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query);

    /**
     * 查询退款详情。
     */
    RefundDetailDTO detail(String refundOrderId);

    /**
     * 基于成功支付单发起退款申请。
     */
    RefundListItemDTO apply(RefundApplyRequestDTO request);

    /**
     * 审核通过退款单，进入渠道处理态。
     */
    RefundListItemDTO approve(RefundActionRequestDTO request);

    /**
     * 模拟渠道退款成功回调。
     */
    RefundListItemDTO markSuccess(RefundActionRequestDTO request);

    /**
     * 模拟渠道退款失败回调。
     */
    RefundListItemDTO markFail(RefundActionRequestDTO request);

    /**
     * 将失败退款单重新提交处理。
     */
    RefundListItemDTO retry(RefundActionRequestDTO request);
}
