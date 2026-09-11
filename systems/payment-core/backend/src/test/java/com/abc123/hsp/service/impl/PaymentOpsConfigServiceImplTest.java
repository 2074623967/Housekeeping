package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.dto.PaymentOpsSystemControlDTO;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

class PaymentOpsConfigServiceImplTest {

    private static final String SNAPSHOT_URL = "http://ops-config/effective-snapshot"
            + "?businessCode=HOME_CLEAN&payType=PAY_CONSUME&terminalType=APP";

    @Test
    void shouldSkipHttpCallWhenOpsConfigIsDisabled() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        PaymentOpsConfigSnapshotDTO result = new PaymentOpsConfigServiceImpl(
                false, "http://ops-config/effective-snapshot", restTemplate)
                .loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP");

        assertNull(result);
        verify(restTemplate, never()).getForObject(any(String.class), any());
    }

    @Test
    void shouldRejectInvalidSnapshotRequestDimensionsBeforeHttpCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigServiceImpl service = service(restTemplate);

        assertNull(service.loadEffectiveSnapshot("HOME\nCLEAN", "PAY_CONSUME", "APP"));
        assertNull(service.loadEffectiveSnapshot(repeat("B", 65), "PAY_CONSUME", "APP"));
        assertNull(service.loadEffectiveSnapshot("HOME_CLEAN", "PAY\u0085CONSUME", "APP"));
        assertNull(service.loadEffectiveSnapshot("HOME_CLEAN", repeat("P", 65), "APP"));
        assertNull(service.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP\n"));
        assertNull(service.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", repeat("T", 65)));

        verify(restTemplate, never()).getForObject(any(String.class), any());
    }

    @Test
    void shouldNormalizeCompleteMatchingSnapshot() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigSnapshotDTO snapshot = snapshot();
        snapshot.setBusinessCode(" home_clean ");
        snapshot.setDefaultPayMethod(" 微信支付 ");
        snapshot.getEnabledSystemControls().get(0).setStatus(" enabled ");
        snapshot.getEnabledSystemControls().get(0).setStatusType(" SUCCESS ");
        stubResponse(restTemplate, " 0 ", snapshot);

        PaymentOpsConfigSnapshotDTO result = service(restTemplate)
                .loadEffectiveSnapshot(" HOME_CLEAN ", " PAY_CONSUME ", " APP ");

        assertEquals("home_clean", result.getBusinessCode());
        assertEquals("微信支付", result.getDefaultPayMethod());
        assertEquals("ENABLED", result.getEnabledSystemControls().get(0).getStatus());
        assertEquals("success", result.getEnabledSystemControls().get(0).getStatusType());
    }

    @Test
    void shouldRejectBusinessFailureEvenWhenItCarriesSnapshotData() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        stubResponse(restTemplate, "OPS-FAILED", snapshot());

        PaymentOpsConfigSnapshotDTO result = service(restTemplate)
                .loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP");

        assertNull(result);
    }

    @Test
    void shouldRejectSnapshotFromAnotherScene() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigSnapshotDTO snapshot = snapshot();
        snapshot.setBusinessCode("HOME_REPAIR");
        stubResponse(restTemplate, "0", snapshot);

        PaymentOpsConfigSnapshotDTO result = service(restTemplate)
                .loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP");

        assertNull(result);
    }

    @Test
    void shouldRejectInvalidVersionOrIncompleteSnapshot() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigSnapshotDTO invalidVersion = snapshot();
        invalidVersion.setSnapshotVersion(-1L);
        stubResponse(restTemplate, "0", invalidVersion);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));

        PaymentOpsConfigSnapshotDTO incomplete = snapshot();
        incomplete.setBackupChannelProfileCode(" ");
        stubResponse(restTemplate, "0", incomplete);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));
    }

    @Test
    void shouldRejectPollutedOversizedOrAmbiguousControls() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigSnapshotDTO polluted = snapshot();
        polluted.getEnabledSystemControls().get(0).setControlValue("5000\n");
        stubResponse(restTemplate, "0", polluted);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));

        PaymentOpsConfigSnapshotDTO oversized = snapshot();
        oversized.getEnabledSystemControls().get(0).setControlName(repeat("A", 129));
        stubResponse(restTemplate, "0", oversized);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));

        PaymentOpsConfigSnapshotDTO duplicated = snapshot();
        duplicated.setEnabledSystemControls(Arrays.asList(control(), control()));
        stubResponse(restTemplate, "0", duplicated);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));
    }

    @Test
    void shouldRejectDisabledOrUnknownStyleControl() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentOpsConfigSnapshotDTO disabled = snapshot();
        disabled.getEnabledSystemControls().get(0).setStatus("DISABLED");
        stubResponse(restTemplate, "0", disabled);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));

        PaymentOpsConfigSnapshotDTO unknownStyle = snapshot();
        unknownStyle.getEnabledSystemControls().get(0).setStatusType("primary");
        stubResponse(restTemplate, "0", unknownStyle);
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));
    }

    @Test
    void shouldReturnNullForInvalidUrlOrHttpFailure() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        assertNull(new PaymentOpsConfigServiceImpl(true, "not-a-url", restTemplate)
                .loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));

        when(restTemplate.getForObject(eq(SNAPSHOT_URL), eq(PaymentOpsConfigServiceImpl.OpsConfigApiResponse.class)))
                .thenThrow(new IllegalStateException("remote secret detail"));
        assertNull(service(restTemplate).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"));
    }

    private PaymentOpsConfigServiceImpl service(RestTemplate restTemplate) {
        return new PaymentOpsConfigServiceImpl(
                true, " http://ops-config/effective-snapshot ", restTemplate);
    }

    private void stubResponse(
            RestTemplate restTemplate,
            String code,
            PaymentOpsConfigSnapshotDTO snapshot) {
        PaymentOpsConfigServiceImpl.OpsConfigApiResponse response =
                new PaymentOpsConfigServiceImpl.OpsConfigApiResponse();
        response.setCode(code);
        response.setData(snapshot);
        when(restTemplate.getForObject(
                eq(SNAPSHOT_URL),
                eq(PaymentOpsConfigServiceImpl.OpsConfigApiResponse.class)))
                .thenReturn(response);
    }

    private PaymentOpsConfigSnapshotDTO snapshot() {
        PaymentOpsConfigSnapshotDTO snapshot = new PaymentOpsConfigSnapshotDTO();
        snapshot.setSnapshotVersion(12L);
        snapshot.setBusinessCode("HOME_CLEAN");
        snapshot.setPayType("PAY_CONSUME");
        snapshot.setTerminalType("APP");
        snapshot.setDefaultPayMethod("微信支付");
        snapshot.setPrimaryChannelProfileCode("CHANNEL_WX_H5");
        snapshot.setBackupChannelProfileCode("CHANNEL_ALI_H5");
        snapshot.setRouteMatchPolicy("按业务线+支付类型命中");
        snapshot.setEnabledSystemControls(Collections.singletonList(control()));
        return snapshot;
    }

    private PaymentOpsSystemControlDTO control() {
        PaymentOpsSystemControlDTO control = new PaymentOpsSystemControlDTO();
        control.setControlCode("CONTROL_BIG_AMOUNT_REVIEW");
        control.setControlName("大额人工复核");
        control.setControlScope("risk-control-system");
        control.setControlValue("5000");
        control.setRiskLevel("HIGH");
        control.setStatus("ENABLED");
        control.setStatusType("success");
        control.setUpdatedAt("2026-09-11 02:30:00");
        return control;
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
