package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentConfigBusinessKeyValidationTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldRejectInvalidVersionAndUpdatePathKeysBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        String oversizedKey = String.join("", Collections.nCopies(65, "K"));

        assertAll(
                () -> assertRejected("渠道编码不能包含控制字符",
                        () -> service.listChannelVersions("wx\npay")),
                () -> assertRejected("路由规则编码不能超过64个字符",
                        () -> service.listRouteRuleVersions(oversizedKey)),
                () -> assertRejected("协议编码不能为空",
                        () -> service.listProtocolVersions("   ")),
                () -> assertRejected("支付网关编码不能包含控制字符",
                        () -> service.listGatewayVersions("GW\t01")),
                () -> assertRejected("来源应用标识不能超过64个字符",
                        () -> service.listControlPolicyVersions(oversizedKey)),
                () -> assertRejected("路由规则编码不能包含控制字符",
                        () -> service.updateRouteRule("RULE\n01", null)),
                () -> assertRejected("值班路由编码不能包含控制字符",
                        () -> service.updateIssueDutyRoster("ROSTER\n01", null)),
                () -> assertRejected("供应商配置编码不能包含控制字符",
                        () -> service.updateAlertProvider("PROVIDER\n01", null)));

        verifyNoInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectInvalidReviewRequestNumbersBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        String invalidRequestNo = "REVIEW\nFORGED";

        assertAll(
                () -> assertReviewRequestRejected(() -> service.approveChannelReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveRouteRuleReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveProtocolReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveIssueDutyRosterReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveReturnCodeReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveReturnCodeImportReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveGatewayReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveControlPolicyReview(invalidRequestNo, null)),
                () -> assertReviewRequestRejected(() -> service.approveAlertProviderReview(invalidRequestNo, null)));

        verifyNoInteractions(paymentConfigMapper);
    }

    private void assertReviewRequestRejected(Executable executable) {
        assertRejected("复核申请编号不能包含控制字符", executable);
    }

    private void assertRejected(String expectedMessage, Executable executable) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        assertEquals(expectedMessage, exception.getMessage());
    }
}
