package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import java.util.Collections;
import com.abc123.hsp.service.PaymentConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付配置中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfigControllerTest {

    @Mock
    private PaymentConfigService paymentConfigService;

    @Test
    void shouldReturnOverview() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        overview.setChannels(java.util.Collections.emptyList());
        when(paymentConfigService.overview()).thenReturn(overview);

        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        assertEquals(0, controller.overview().getData().getChannels().size());
        verify(paymentConfigService).overview();
    }

    @Test
    void shouldExportGovernanceSnapshot() {
        when(paymentConfigService.exportGovernanceSnapshotCsv("CHANNELS")).thenReturn("config-csv");

        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        String body = new String(controller.export("CHANNELS").getBody(), java.nio.charset.StandardCharsets.UTF_8);

        verify(paymentConfigService).exportGovernanceSnapshotCsv("CHANNELS");
        assertTrue(body.contains("config-csv"));
    }

    @Test
    void shouldReviewAndRollbackProtocolVersion() {
        when(paymentConfigService.listProtocolReviews())
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        when(paymentConfigService.submitProtocolReview(any(PaymentProtocolReviewSubmitRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        when(paymentConfigService.approveProtocolReview(
                any(String.class), any(PaymentProtocolReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        when(paymentConfigService.rejectProtocolReview(
                any(String.class), any(PaymentProtocolReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        when(paymentConfigService.submitProtocolStatusReview(any(PaymentProtocolStatusReviewRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        when(paymentConfigService.listProtocolVersions("PROTO_HOME_SIGN"))
                .thenReturn(Collections.singletonList(new PaymentProtocolVersionDTO()));
        when(paymentConfigService.submitProtocolRollbackReview(
                any(String.class), any(PaymentProtocolRollbackRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentProtocolReviewDTO()));
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentProtocolReviewSubmitRequestDTO submitRequest = new PaymentProtocolReviewSubmitRequestDTO();
        PaymentProtocolReviewActionRequestDTO actionRequest = new PaymentProtocolReviewActionRequestDTO();
        PaymentProtocolStatusReviewRequestDTO statusRequest = new PaymentProtocolStatusReviewRequestDTO();
        PaymentProtocolRollbackRequestDTO request = new PaymentProtocolRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));

        assertEquals(1, controller.listProtocolReviews().getData().size());
        assertEquals(1, controller.submitProtocolReview(submitRequest).getData().size());
        assertEquals(1, controller.approveProtocolReview("PRV-001", actionRequest).getData().size());
        assertEquals(1, controller.rejectProtocolReview("PRV-001", actionRequest).getData().size());
        assertEquals(1, controller.submitProtocolStatusReview(statusRequest).getData().size());
        assertEquals(1, controller.listProtocolVersions("PROTO_HOME_SIGN").getData().size());
        assertEquals(1, controller.submitProtocolRollbackReview("PROTO_HOME_SIGN", request).getData().size());
        verify(paymentConfigService).listProtocolReviews();
        verify(paymentConfigService).submitProtocolReview(submitRequest);
        verify(paymentConfigService).approveProtocolReview("PRV-001", actionRequest);
        verify(paymentConfigService).rejectProtocolReview("PRV-001", actionRequest);
        verify(paymentConfigService).submitProtocolStatusReview(statusRequest);
        verify(paymentConfigService).listProtocolVersions("PROTO_HOME_SIGN");
        verify(paymentConfigService).submitProtocolRollbackReview("PROTO_HOME_SIGN", request);
    }

    @Test
    void shouldValidateAndReviewRouteRule() {
        PaymentRouteRuleValidationDTO validation = new PaymentRouteRuleValidationDTO();
        validation.setValid(Boolean.TRUE);
        when(paymentConfigService.validateRouteRule(any(PaymentRouteRuleValidationRequestDTO.class)))
                .thenReturn(validation);
        when(paymentConfigService.listRouteRuleConflicts())
                .thenReturn(Collections.singletonList(new PaymentRouteRuleConflictDTO()));
        when(paymentConfigService.listRouteRuleReviews())
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        when(paymentConfigService.submitRouteRuleReview(any(PaymentRouteRuleReviewSubmitRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        when(paymentConfigService.approveRouteRuleReview(
                any(String.class), any(PaymentRouteRuleReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        when(paymentConfigService.rejectRouteRuleReview(
                any(String.class), any(PaymentRouteRuleReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        when(paymentConfigService.listRouteRuleVersions("RULE_HOME_APP"))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleVersionDTO()));
        when(paymentConfigService.submitRouteRuleRollbackReview(
                any(String.class), any(PaymentRouteRuleRollbackRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        when(paymentConfigService.submitRouteRuleStatusReview(any(PaymentRouteRuleStatusReviewRequestDTO.class)))
                .thenReturn(Collections.singletonList(new PaymentRouteRuleReviewDTO()));
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentRouteRuleValidationRequestDTO validationRequest = new PaymentRouteRuleValidationRequestDTO();
        validationRequest.setMatchExpression("terminal=APP");
        PaymentRouteRuleRollbackRequestDTO rollbackRequest = new PaymentRouteRuleRollbackRequestDTO();
        rollbackRequest.setVersionNo(Integer.valueOf(1));
        PaymentRouteRuleStatusReviewRequestDTO statusRequest = new PaymentRouteRuleStatusReviewRequestDTO();
        statusRequest.setRuleCode("RULE_HOME_APP");

        assertTrue(controller.validateRouteRule(validationRequest).getData().getValid());
        assertEquals(1, controller.listRouteRuleConflicts().getData().size());
        PaymentRouteRuleReviewSubmitRequestDTO reviewSubmit = new PaymentRouteRuleReviewSubmitRequestDTO();
        PaymentRouteRuleReviewActionRequestDTO reviewAction = new PaymentRouteRuleReviewActionRequestDTO();
        assertEquals(1, controller.listRouteRuleReviews().getData().size());
        assertEquals(1, controller.submitRouteRuleReview(reviewSubmit).getData().size());
        assertEquals(1, controller.approveRouteRuleReview("RRV-001", reviewAction).getData().size());
        assertEquals(1, controller.rejectRouteRuleReview("RRV-001", reviewAction).getData().size());
        assertEquals(1, controller.listRouteRuleVersions("RULE_HOME_APP").getData().size());
        assertEquals(1, controller.submitRouteRuleRollbackReview("RULE_HOME_APP", rollbackRequest).getData().size());
        assertEquals(1, controller.submitRouteRuleStatusReview(statusRequest).getData().size());
        verify(paymentConfigService).validateRouteRule(validationRequest);
        verify(paymentConfigService).listRouteRuleConflicts();
        verify(paymentConfigService).listRouteRuleReviews();
        verify(paymentConfigService).submitRouteRuleReview(reviewSubmit);
        verify(paymentConfigService).approveRouteRuleReview("RRV-001", reviewAction);
        verify(paymentConfigService).rejectRouteRuleReview("RRV-001", reviewAction);
        verify(paymentConfigService).listRouteRuleVersions("RULE_HOME_APP");
        verify(paymentConfigService).submitRouteRuleRollbackReview("RULE_HOME_APP", rollbackRequest);
        verify(paymentConfigService).submitRouteRuleStatusReview(statusRequest);
    }

    @Test
    void shouldCreateIssueDutyRoster() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.createIssueDutyRoster(any(PaymentIssueDutyRosterUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode("ROSTER_CALLBACK_P1");

        assertEquals(overview, controller.createIssueDutyRoster(request).getData());
        verify(paymentConfigService).createIssueDutyRoster(request);
    }

    @Test
    void shouldUpdateIssueDutyRoster() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.updateIssueDutyRoster(any(String.class), any(PaymentIssueDutyRosterUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setReceiver("支付后端值班");

        assertEquals(overview, controller.updateIssueDutyRoster("ROSTER_CALLBACK_P1", request).getData());
        verify(paymentConfigService).updateIssueDutyRoster("ROSTER_CALLBACK_P1", request);
    }

    @Test
    void shouldToggleGovernanceConfigs() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.toggleChannel(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleReturnCodeMapping(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleGateway(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleControlPolicy(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleAlertProvider(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleIssueDutyRoster(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("WX_H5");
        request.setEnabled(Boolean.TRUE);
        assertEquals(overview, controller.toggleChannel(request).getData());
        assertEquals(overview, controller.toggleReturnCodeMapping(request).getData());
        assertEquals(overview, controller.toggleGateway(request).getData());
        assertEquals(overview, controller.toggleControlPolicy(request).getData());
        assertEquals(overview, controller.toggleAlertProvider(request).getData());
        assertEquals(overview, controller.toggleIssueDutyRoster(request).getData());
        verify(paymentConfigService).toggleChannel(request);
        verify(paymentConfigService).toggleReturnCodeMapping(request);
        verify(paymentConfigService).toggleGateway(request);
        verify(paymentConfigService).toggleControlPolicy(request);
        verify(paymentConfigService).toggleAlertProvider(request);
        verify(paymentConfigService).toggleIssueDutyRoster(request);
    }

    @Test
    void shouldRunControlPolicySelfCheck() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.runControlPolicySelfCheck(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("APP_HOME_SERVICE");

        assertEquals(overview, controller.runControlPolicySelfCheck(request).getData());
        verify(paymentConfigService).runControlPolicySelfCheck(request);
    }
}
