package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentRecordRowDTO;
import java.util.List;

/**
 * 收款记录 Service，承接支付收款查询，不承接钱包余额流水。
 */
public interface PaymentRecordService {

    /**
     * 查询收款记录。
     *
     * @param recordType 记录类型，ALL、WECHAT、BANK_CARD
     * @return 收款记录列表
     */
    List<PaymentRecordRowDTO> list(String recordType);
}
