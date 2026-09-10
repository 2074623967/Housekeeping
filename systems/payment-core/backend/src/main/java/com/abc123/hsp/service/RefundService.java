package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;

public interface RefundService {

    /**
     * 查询退款单分页列表。
     */
    PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query);

    /**
     * 导出退款单列表。
     */
    String exportCsv(RefundQueryDTO query);

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
     * 校验渠道退款回调并收口退款状态。
     */
    RefundListItemDTO callback(String channel, RefundCallbackRequestDTO request);

    /**
     * 主动查询渠道退款状态并在结果明确时收口。
     */
    RefundListItemDTO query(RefundStatusQueryRequestDTO request);

    /**
     * 将失败退款单重新提交处理。
     */
    RefundListItemDTO retry(RefundActionRequestDTO request);

    /**
     * 由补偿任务重试失败退款，保留任务触发人并重新提交渠道。
     */
    void retryForTask(String refundOrderId, String triggeredBy);
}
