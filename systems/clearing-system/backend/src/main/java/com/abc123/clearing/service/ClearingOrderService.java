package com.abc123.clearing.service;

import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.ClearingOrderDetailDTO;
import com.abc123.clearing.dto.PageResultDTO;

/**
 * 清分结果服务。
 */
public interface ClearingOrderService {

    PageResultDTO<ClearingOrderDTO> list(String batchNo, String orderNo, String paymentOrderId, String clearingStatus, int pageNo, int pageSize);

    byte[] exportList(String batchNo, String orderNo, String paymentOrderId, String clearingStatus);

    ClearingOrderDetailDTO detail(String clearingNo);

    byte[] exportDetail(String clearingNo);
}
