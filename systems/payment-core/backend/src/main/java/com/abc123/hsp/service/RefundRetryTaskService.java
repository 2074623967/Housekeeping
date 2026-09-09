package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundRetryResultDTO;

/**
 * 失败退款批次重试服务。
 */
public interface RefundRetryTaskService {

    /**
     * 逐笔重试失败退款并返回批次结果。
     */
    RefundRetryResultDTO retryFailedRefunds(String triggeredBy);
}
