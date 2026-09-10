package com.abc123.hsp.service;

import com.abc123.hsp.dto.RefundQueryTaskResultDTO;

/**
 * 处理中退款主动查单任务服务。
 */
public interface RefundQueryTaskService {

    RefundQueryTaskResultDTO queryPendingRefunds(String triggeredBy);

    RefundQueryTaskResultDTO queryDueRefunds(String triggeredBy);

    int countPendingRefunds();

    int countDueRefunds();
}
