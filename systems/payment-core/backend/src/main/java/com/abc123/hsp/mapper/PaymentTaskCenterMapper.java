package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import java.util.List;

/**
 * 支付任务中心 Mapper。
 */
public interface PaymentTaskCenterMapper {

    /**
     * 查询任务中心汇总指标。
     */
    PaymentTaskCenterOverviewDTO findOverviewSummary();

    /**
     * 查询最近任务执行日志。
     */
    List<PaymentTaskRunLogItemDTO> findRecentTaskRuns();

    /**
     * 新增任务执行日志。
     */
    int insertTaskRunLog(PaymentTaskRunLogEntity entity);
}
