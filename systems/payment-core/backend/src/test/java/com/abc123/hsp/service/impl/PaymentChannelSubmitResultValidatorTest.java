package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentChannelSubmitResultValidatorTest {

    @Test
    void shouldKeepValidTerminalAttemptStyle() {
        PaymentChannelSubmitResultDTO success = validate("成功", "SUCCESS");
        assertEquals("success", success.getAttemptStatusType());
        assertEquals("success", validate("支付成功", "SUCCESS").getAttemptStatusType());

        PaymentChannelSubmitResultDTO failure = validate("支付失败", "danger");
        assertEquals("danger", failure.getAttemptStatusType());
        assertEquals("success", validate(" sUcCeSs ", "SUCCESS").getAttemptStatusType());
        assertEquals("danger", validate(" fAiLeD ", "DANGER").getAttemptStatusType());
    }

    @Test
    void shouldRejectTerminalAttemptStyleMismatch() {
        for (String[] mismatch : new String[][] {
            {"成功", "danger"},
            {"支付成功", "danger"},
            {"失败", "success"},
            {"支付失败", "warn"},
            {" SUCCESS ", "danger"},
            {"success", "warn"},
            {"FAIL", "success"},
            {" failed ", "info"}
        }) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> validate(mismatch[0], mismatch[1]));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        }
    }

    @Test
    void shouldAllowNonTerminalAttemptStyleFromChannel() {
        PaymentChannelSubmitResultDTO result = validate("处理中", "INFO");
        assertEquals("info", result.getAttemptStatusType());
    }

    @Test
    void shouldValidateNativePaymentParameterFacts() {
        PaymentChannelSubmitResultDTO result = validate("处理中", "INFO");
        Map<String, String> params = new HashMap<>();
        params.put("timeStamp", "1710000000");
        params.put("nonceStr", "nonce");
        params.put("package", "prepay_id=wx-prepay");
        params.put("signType", "RSA");
        params.put("paySign", "signature");
        result.setNativePaymentParams(params);

        assertEquals(params, PaymentChannelSubmitResultValidator.validateAndNormalize(result, "MINI_PROGRAM")
                .getNativePaymentParams());
    }

    @Test
    void shouldRejectMalformedNativePaymentParameterFacts() {
        for (String[] invalid : new String[][] {
            {"not-a-timestamp", "prepay_id=wx-prepay", "RSA"},
            {"1710000000", "wx-prepay", "RSA"},
            {"1710000000", "prepay_id=wx-prepay", "MD5"}
        }) {
            PaymentChannelSubmitResultDTO result = validate("处理中", "INFO");
            Map<String, String> params = new HashMap<>();
            params.put("timeStamp", invalid[0]);
            params.put("nonceStr", "nonce");
            params.put("package", invalid[1]);
            params.put("signType", invalid[2]);
            params.put("paySign", "signature");
            result.setNativePaymentParams(params);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> PaymentChannelSubmitResultValidator.validateAndNormalize(result, "MINI_PROGRAM"));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        }
    }

    private static PaymentChannelSubmitResultDTO validate(String attemptStatus, String attemptStatusType) {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo("CHANNEL-001");
        result.setAttemptStatus(attemptStatus);
        result.setAttemptStatusType(attemptStatusType);
        result.setChannelReturnCode("USERPAYING");
        return PaymentChannelSubmitResultValidator.validateAndNormalize(result, null);
    }
}
