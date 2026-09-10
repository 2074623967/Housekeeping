package com.abc123.hsp.service;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;

/**
 * 退款渠道提交失败，区分请求未发出与外部结果未知。
 */
public class RefundChannelSubmitException extends BusinessException {

    private final boolean resultUnknown;

    public RefundChannelSubmitException(String message, boolean resultUnknown) {
        super(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, message);
        this.resultUnknown = resultUnknown;
    }

    public static RefundChannelSubmitException sanitized(String message, boolean resultUnknown) {
        return new RefundChannelSubmitException(message, resultUnknown, null, false, false);
    }

    private RefundChannelSubmitException(String message, boolean resultUnknown, Throwable cause,
                                         boolean enableSuppression, boolean writableStackTrace) {
        super(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, message, cause,
                enableSuppression, writableStackTrace);
        this.resultUnknown = resultUnknown;
    }

    public boolean isResultUnknown() {
        return resultUnknown;
    }
}
