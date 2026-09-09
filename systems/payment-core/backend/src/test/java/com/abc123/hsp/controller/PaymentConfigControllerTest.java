package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewDTO;
import com.abc123.hsp.dto.PaymentAlertProviderStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentAlertProviderUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentChannelVersionDTO;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyVersionDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayVersionDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
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
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeStatusReviewRequestDTO;
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
    void shouldReviewChannelStatusChange() {
        PaymentChannelReviewDTO review = new PaymentChannelReviewDTO();
        when(paymentConfigService.listChannelReviews()).thenReturn(Collections.singletonList(review));
        when(paymentConfigService.submitChannelStatusReview(any(PaymentChannelStatusReviewRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.submitChannelReview(any(PaymentChannelReviewSubmitRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.approveChannelReview(
                any(String.class), any(PaymentChannelReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.rejectChannelReview(
                any(String.class), any(PaymentChannelReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.listChannelVersions("wx_h5"))
                .thenReturn(Collections.singletonList(new PaymentChannelVersionDTO()));
        when(paymentConfigService.submitChannelRollbackReview(
                any(String.class), any(PaymentChannelRollbackRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentChannelStatusReviewRequestDTO statusRequest = new PaymentChannelStatusReviewRequestDTO();
        PaymentChannelReviewSubmitRequestDTO submitRequest = new PaymentChannelReviewSubmitRequestDTO();
        PaymentChannelReviewActionRequestDTO actionRequest = new PaymentChannelReviewActionRequestDTO();
        PaymentChannelRollbackRequestDTO rollbackRequest = new PaymentChannelRollbackRequestDTO();

        assertEquals(1, controller.listChannelReviews().getData().size());
        assertEquals(1, controller.submitChannelStatusReview(statusRequest).getData().size());
        assertEquals(1, controller.submitChannelReview(submitRequest).getData().size());
        assertEquals(1, controller.approveChannelReview("CSR-001", actionRequest).getData().size());
        assertEquals(1, controller.rejectChannelReview("CSR-001", actionRequest).getData().size());
        assertEquals(1, controller.listChannelVersions("wx_h5").getData().size());
        assertEquals(1, controller.submitChannelRollbackReview("wx_h5", rollbackRequest).getData().size());
        verify(paymentConfigService).listChannelReviews();
        verify(paymentConfigService).submitChannelStatusReview(statusRequest);
        verify(paymentConfigService).submitChannelReview(submitRequest);
        verify(paymentConfigService).approveChannelReview("CSR-001", actionRequest);
        verify(paymentConfigService).rejectChannelReview("CSR-001", actionRequest);
        verify(paymentConfigService).listChannelVersions("wx_h5");
        verify(paymentConfigService).submitChannelRollbackReview("wx_h5", rollbackRequest);
    }

    @Test
    void shouldPreviewAndReviewReturnCodeImport() {
        PaymentReturnCodeImportResultDTO preview = new PaymentReturnCodeImportResultDTO();
        preview.setValid(Boolean.TRUE);
        PaymentReturnCodeImportReviewDTO review = new PaymentReturnCodeImportReviewDTO();
        when(paymentConfigService.previewReturnCodeImport(any(PaymentReturnCodeImportRequestDTO.class)))
                .thenReturn(preview);
        when(paymentConfigService.listReturnCodeImportReviews()).thenReturn(Collections.singletonList(review));
        when(paymentConfigService.submitReturnCodeImportReview(
                any(PaymentReturnCodeImportReviewSubmitRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.approveReturnCodeImportReview(
                any(String.class), any(PaymentReturnCodeImportReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.rejectReturnCodeImportReview(
                any(String.class), any(PaymentReturnCodeImportReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentReturnCodeImportRequestDTO request = new PaymentReturnCodeImportRequestDTO();
        PaymentReturnCodeImportReviewSubmitRequestDTO submitRequest =
                new PaymentReturnCodeImportReviewSubmitRequestDTO();
        PaymentReturnCodeImportReviewActionRequestDTO actionRequest =
                new PaymentReturnCodeImportReviewActionRequestDTO();

        assertTrue(controller.previewReturnCodeImport(request).getData().getValid());
        assertEquals(1, controller.listReturnCodeImportReviews().getData().size());
        assertEquals(1, controller.submitReturnCodeImportReview(submitRequest).getData().size());
        assertEquals(1, controller.approveReturnCodeImportReview("RCR-001", actionRequest).getData().size());
        assertEquals(1, controller.rejectReturnCodeImportReview("RCR-001", actionRequest).getData().size());
        verify(paymentConfigService).previewReturnCodeImport(request);
        verify(paymentConfigService).listReturnCodeImportReviews();
        verify(paymentConfigService).submitReturnCodeImportReview(submitRequest);
        verify(paymentConfigService).approveReturnCodeImportReview("RCR-001", actionRequest);
        verify(paymentConfigService).rejectReturnCodeImportReview("RCR-001", actionRequest);
    }

    @Test
    void shouldReviewReturnCodeStatusChange() {
        PaymentReturnCodeReviewDTO review = new PaymentReturnCodeReviewDTO();
        when(paymentConfigService.listReturnCodeReviews()).thenReturn(Collections.singletonList(review));
        when(paymentConfigService.submitReturnCodeStatusReview(any(PaymentReturnCodeStatusReviewRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.approveReturnCodeReview(
                any(String.class), any(PaymentReturnCodeReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        when(paymentConfigService.rejectReturnCodeReview(
                any(String.class), any(PaymentReturnCodeReviewActionRequestDTO.class)))
                .thenReturn(Collections.singletonList(review));
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentReturnCodeStatusReviewRequestDTO statusRequest = new PaymentReturnCodeStatusReviewRequestDTO();
        PaymentReturnCodeReviewActionRequestDTO actionRequest = new PaymentReturnCodeReviewActionRequestDTO();

        assertEquals(1, controller.listReturnCodeReviews().getData().size());
        assertEquals(1, controller.submitReturnCodeStatusReview(statusRequest).getData().size());
        assertEquals(1, controller.approveReturnCodeReview("RCS-001", actionRequest).getData().size());
        assertEquals(1, controller.rejectReturnCodeReview("RCS-001", actionRequest).getData().size());
        verify(paymentConfigService).listReturnCodeReviews();
        verify(paymentConfigService).submitReturnCodeStatusReview(statusRequest);
        verify(paymentConfigService).approveReturnCodeReview("RCS-001", actionRequest);
        verify(paymentConfigService).rejectReturnCodeReview("RCS-001", actionRequest);
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
        java.util.List<PaymentIssueDutyRosterReviewDTO> reviews =
                Collections.singletonList(new PaymentIssueDutyRosterReviewDTO());
        when(paymentConfigService.createIssueDutyRoster(any(PaymentIssueDutyRosterUpsertRequestDTO.class)))
                .thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode("ROSTER_CALLBACK_P1");

        assertEquals(reviews, controller.createIssueDutyRoster(request).getData());
        verify(paymentConfigService).createIssueDutyRoster(request);
    }

    @Test
    void shouldUpdateIssueDutyRoster() {
        java.util.List<PaymentIssueDutyRosterReviewDTO> reviews =
                Collections.singletonList(new PaymentIssueDutyRosterReviewDTO());
        when(paymentConfigService.updateIssueDutyRoster(
                any(String.class), any(PaymentIssueDutyRosterUpsertRequestDTO.class))).thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setReceiver("支付后端值班");

        assertEquals(reviews, controller.updateIssueDutyRoster("ROSTER_CALLBACK_P1", request).getData());
        verify(paymentConfigService).updateIssueDutyRoster("ROSTER_CALLBACK_P1", request);
    }

    @Test
    void shouldToggleOtherGovernanceConfigs() {
        java.util.List<PaymentAlertProviderReviewDTO> providerReviews =
                Collections.singletonList(new PaymentAlertProviderReviewDTO());
        java.util.List<PaymentIssueDutyRosterReviewDTO> reviews =
                Collections.singletonList(new PaymentIssueDutyRosterReviewDTO());
        when(paymentConfigService.toggleAlertProvider(any(PaymentAlertProviderStatusReviewRequestDTO.class)))
                .thenReturn(providerReviews);
        when(paymentConfigService.toggleIssueDutyRoster(any(PaymentIssueDutyRosterStatusReviewRequestDTO.class)))
                .thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentAlertProviderStatusReviewRequestDTO request = new PaymentAlertProviderStatusReviewRequestDTO();
        request.setProviderCode("ALERT_IM_WECOM");
        request.setEnabled(Boolean.TRUE);
        assertEquals(providerReviews, controller.toggleAlertProvider(request).getData());
        verify(paymentConfigService).toggleAlertProvider(request);

        PaymentIssueDutyRosterStatusReviewRequestDTO rosterRequest =
                new PaymentIssueDutyRosterStatusReviewRequestDTO();
        rosterRequest.setRosterCode("ROSTER_CALLBACK_P1");
        rosterRequest.setEnabled(Boolean.TRUE);
        assertEquals(reviews, controller.toggleIssueDutyRoster(rosterRequest).getData());
        verify(paymentConfigService).toggleIssueDutyRoster(rosterRequest);
    }

    @Test
    void shouldExposeAlertProviderReviewFlow() {
        java.util.List<PaymentAlertProviderReviewDTO> reviews =
                Collections.singletonList(new PaymentAlertProviderReviewDTO());
        when(paymentConfigService.createAlertProvider(any(PaymentAlertProviderUpsertRequestDTO.class)))
                .thenReturn(reviews);
        when(paymentConfigService.updateAlertProvider(
                any(String.class), any(PaymentAlertProviderUpsertRequestDTO.class))).thenReturn(reviews);
        when(paymentConfigService.listAlertProviderReviews()).thenReturn(reviews);
        when(paymentConfigService.approveAlertProviderReview(
                any(String.class), any(PaymentAlertProviderReviewActionRequestDTO.class))).thenReturn(reviews);
        when(paymentConfigService.rejectAlertProviderReview(
                any(String.class), any(PaymentAlertProviderReviewActionRequestDTO.class))).thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        PaymentAlertProviderUpsertRequestDTO candidate = new PaymentAlertProviderUpsertRequestDTO();
        PaymentAlertProviderReviewActionRequestDTO action = new PaymentAlertProviderReviewActionRequestDTO();

        assertEquals(reviews, controller.createAlertProvider(candidate).getData());
        assertEquals(reviews, controller.updateAlertProvider("ALERT_IM_WECOM", candidate).getData());
        assertEquals(reviews, controller.listAlertProviderReviews().getData());
        assertEquals(reviews, controller.approveAlertProviderReview("APR-001", action).getData());
        assertEquals(reviews, controller.rejectAlertProviderReview("APR-002", action).getData());
        verify(paymentConfigService).createAlertProvider(candidate);
        verify(paymentConfigService).updateAlertProvider("ALERT_IM_WECOM", candidate);
        verify(paymentConfigService).listAlertProviderReviews();
        verify(paymentConfigService).approveAlertProviderReview("APR-001", action);
        verify(paymentConfigService).rejectAlertProviderReview("APR-002", action);
    }

    @Test
    void shouldExposeIssueDutyRosterReviewFlow() {
        java.util.List<PaymentIssueDutyRosterReviewDTO> reviews =
                Collections.singletonList(new PaymentIssueDutyRosterReviewDTO());
        PaymentIssueDutyRosterReviewActionRequestDTO action =
                new PaymentIssueDutyRosterReviewActionRequestDTO();
        when(paymentConfigService.listIssueDutyRosterReviews()).thenReturn(reviews);
        when(paymentConfigService.approveIssueDutyRosterReview("DRR-001", action)).thenReturn(reviews);
        when(paymentConfigService.rejectIssueDutyRosterReview("DRR-001", action)).thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        assertEquals(reviews, controller.listIssueDutyRosterReviews().getData());
        assertEquals(reviews, controller.approveIssueDutyRosterReview("DRR-001", action).getData());
        assertEquals(reviews, controller.rejectIssueDutyRosterReview("DRR-001", action).getData());
        verify(paymentConfigService).listIssueDutyRosterReviews();
        verify(paymentConfigService).approveIssueDutyRosterReview("DRR-001", action);
        verify(paymentConfigService).rejectIssueDutyRosterReview("DRR-001", action);
    }

    @Test
    void shouldExposeControlPolicyReviewFlow() {
        PaymentControlPolicyStatusReviewRequestDTO statusRequest =
                new PaymentControlPolicyStatusReviewRequestDTO();
        PaymentControlPolicyReviewSubmitRequestDTO submitRequest =
                new PaymentControlPolicyReviewSubmitRequestDTO();
        PaymentControlPolicyReviewActionRequestDTO actionRequest =
                new PaymentControlPolicyReviewActionRequestDTO();
        PaymentControlPolicyRollbackRequestDTO rollbackRequest =
                new PaymentControlPolicyRollbackRequestDTO();
        java.util.List<PaymentControlPolicyReviewDTO> reviews =
                Collections.singletonList(new PaymentControlPolicyReviewDTO());
        when(paymentConfigService.submitControlPolicyStatusReview(statusRequest)).thenReturn(reviews);
        when(paymentConfigService.submitControlPolicyReview(submitRequest)).thenReturn(reviews);
        when(paymentConfigService.listControlPolicyReviews()).thenReturn(reviews);
        when(paymentConfigService.approveControlPolicyReview("CPR-001", actionRequest)).thenReturn(reviews);
        when(paymentConfigService.rejectControlPolicyReview("CPR-001", actionRequest)).thenReturn(reviews);
        when(paymentConfigService.listControlPolicyVersions("housekeeping-app-web"))
                .thenReturn(Collections.singletonList(new PaymentControlPolicyVersionDTO()));
        when(paymentConfigService.submitControlPolicyRollbackReview(
                "housekeeping-app-web", rollbackRequest)).thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        assertEquals(reviews, controller.submitControlPolicyStatusReview(statusRequest).getData());
        assertEquals(reviews, controller.submitControlPolicyReview(submitRequest).getData());
        assertEquals(reviews, controller.listControlPolicyReviews().getData());
        assertEquals(reviews, controller.approveControlPolicyReview("CPR-001", actionRequest).getData());
        assertEquals(reviews, controller.rejectControlPolicyReview("CPR-001", actionRequest).getData());
        assertEquals(1, controller.listControlPolicyVersions("housekeeping-app-web").getData().size());
        assertEquals(reviews, controller.submitControlPolicyRollbackReview(
                "housekeeping-app-web", rollbackRequest).getData());
        verify(paymentConfigService).submitControlPolicyStatusReview(statusRequest);
        verify(paymentConfigService).submitControlPolicyReview(submitRequest);
        verify(paymentConfigService).listControlPolicyReviews();
        verify(paymentConfigService).approveControlPolicyReview("CPR-001", actionRequest);
        verify(paymentConfigService).rejectControlPolicyReview("CPR-001", actionRequest);
        verify(paymentConfigService).listControlPolicyVersions("housekeeping-app-web");
        verify(paymentConfigService).submitControlPolicyRollbackReview(
                "housekeeping-app-web", rollbackRequest);
    }

    @Test
    void shouldExposeGatewayStatusReviewFlow() {
        PaymentGatewayStatusReviewRequestDTO submitRequest = new PaymentGatewayStatusReviewRequestDTO();
        PaymentGatewayReviewSubmitRequestDTO fullSubmitRequest = new PaymentGatewayReviewSubmitRequestDTO();
        PaymentGatewayReviewActionRequestDTO actionRequest = new PaymentGatewayReviewActionRequestDTO();
        java.util.List<PaymentGatewayReviewDTO> reviews =
                Collections.singletonList(new PaymentGatewayReviewDTO());
        when(paymentConfigService.submitGatewayStatusReview(submitRequest)).thenReturn(reviews);
        when(paymentConfigService.submitGatewayReview(fullSubmitRequest)).thenReturn(reviews);
        when(paymentConfigService.listGatewayReviews()).thenReturn(reviews);
        when(paymentConfigService.approveGatewayReview("GWR-001", actionRequest)).thenReturn(reviews);
        when(paymentConfigService.rejectGatewayReview("GWR-001", actionRequest)).thenReturn(reviews);
        when(paymentConfigService.listGatewayVersions("GATEWAY_WX_ACQ"))
                .thenReturn(Collections.singletonList(new PaymentGatewayVersionDTO()));
        PaymentGatewayRollbackRequestDTO rollbackRequest = new PaymentGatewayRollbackRequestDTO();
        rollbackRequest.setVersionNo(Integer.valueOf(1));
        when(paymentConfigService.submitGatewayRollbackReview("GATEWAY_WX_ACQ", rollbackRequest))
                .thenReturn(reviews);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        assertEquals(reviews, controller.submitGatewayStatusReview(submitRequest).getData());
        assertEquals(reviews, controller.submitGatewayReview(fullSubmitRequest).getData());
        assertEquals(reviews, controller.listGatewayReviews().getData());
        assertEquals(reviews, controller.approveGatewayReview("GWR-001", actionRequest).getData());
        assertEquals(reviews, controller.rejectGatewayReview("GWR-001", actionRequest).getData());
        assertEquals(1, controller.listGatewayVersions("GATEWAY_WX_ACQ").getData().size());
        assertEquals(reviews, controller.submitGatewayRollbackReview(
                "GATEWAY_WX_ACQ", rollbackRequest).getData());
        verify(paymentConfigService).submitGatewayStatusReview(submitRequest);
        verify(paymentConfigService).submitGatewayReview(fullSubmitRequest);
        verify(paymentConfigService).listGatewayReviews();
        verify(paymentConfigService).approveGatewayReview("GWR-001", actionRequest);
        verify(paymentConfigService).rejectGatewayReview("GWR-001", actionRequest);
        verify(paymentConfigService).listGatewayVersions("GATEWAY_WX_ACQ");
        verify(paymentConfigService).submitGatewayRollbackReview("GATEWAY_WX_ACQ", rollbackRequest);
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
