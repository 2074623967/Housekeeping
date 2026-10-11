package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
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
        String oversizedKeyAfterTrim = String.join("", Collections.nCopies(64, "K")) + " ";

        assertAll(
                () -> assertRejected("渠道编码不能包含控制字符",
                        () -> service.listChannelVersions("wx\npay")),
                () -> assertRejected("路由规则编码不能超过64个字符",
                        () -> service.listRouteRuleVersions(oversizedKey)),
                () -> assertRejected("路由规则编码不能超过64个字符",
                        () -> service.listRouteRuleVersions(oversizedKeyAfterTrim)),
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

    @Test
    void shouldRejectInvalidCreateKeysBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentRouteRuleUpsertRequestDTO routeRequest = new PaymentRouteRuleUpsertRequestDTO();
        routeRequest.setRuleCode("RULE\n01");

        PaymentProtocolReviewSubmitRequestDTO protocolRequest = new PaymentProtocolReviewSubmitRequestDTO();
        protocolRequest.setRequester("protocol-maker");
        PaymentProtocolUpsertRequestDTO candidate = new PaymentProtocolUpsertRequestDTO();
        candidate.setProtocolCode(String.join("", Collections.nCopies(64, "P")) + " ");
        candidate.setChangeReason("新增测试协议");
        protocolRequest.setCandidate(candidate);

        assertAll(
                () -> assertRejected("路由规则编码不能包含控制字符",
                        () -> service.createRouteRule(routeRequest)),
                () -> assertRejected("协议编码不能超过64个字符",
                        () -> service.submitProtocolReview(protocolRequest)));

        verifyNoInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectInvalidRouteChannelKeysBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode("RULE_VALID");
        request.setRuleName("有效路由");
        request.setMatchScene("HOME_CLEAN");
        request.setMatchExpression("amount<=500");
        request.setTargetChannelCode("wx\n_h5");

        assertRejected("目标渠道不能包含控制字符", () -> service.createRouteRule(request));

        request.setTargetChannelCode(String.join("", Collections.nCopies(64, "W")) + " ");
        assertRejected("目标渠道不能超过64个字符", () -> service.createRouteRule(request));

        verifyNoInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectControlCharactersInRouteCandidateTextBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode("RULE_VALID");
        request.setRuleName("小额路由\n伪造");

        assertRejected("路由规则名称不能包含控制字符", () -> service.createRouteRule(request));
        verifyNoInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectInvalidControlPolicyCodeBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("cashier-app\n");

        assertRejected("配置编码不能包含控制字符", () -> service.runControlPolicySelfCheck(request));

        request.setConfigCode(String.join("", Collections.nCopies(64, "C")) + " ");
        assertRejected("配置编码不能超过64个字符", () -> service.runControlPolicySelfCheck(request));

        verifyNoInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectInvalidDutyRosterCodeBeforeMapperAccess() {
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode("DUTY\nP1");
        request.setRequester("maker-a");
        request.setChangeReason("新增值班路由");

        assertRejected("值班路由编码不能包含控制字符", () -> service.createIssueDutyRoster(request));

        request.setRosterCode(String.join("", Collections.nCopies(64, "D")) + " ");
        assertRejected("值班路由编码不能超过64个字符", () -> service.createIssueDutyRoster(request));

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
