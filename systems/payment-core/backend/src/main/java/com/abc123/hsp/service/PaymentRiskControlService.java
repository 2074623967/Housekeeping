package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;

/**
 * 支付核心到风控中心的准入联动服务。
 */
public interface PaymentRiskControlService {

    PaymentRiskDecisionResultDTO evaluateSubmitRisk(PaymentRiskDecisionRequestDTO request);
}
