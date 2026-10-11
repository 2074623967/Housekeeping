package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class PaymentCallbackWindowStartupVerifierTest {

    private final PaymentConfigMapper paymentConfigMapper = mock(PaymentConfigMapper.class);

    @Test
    void shouldAllowEnabledSeedChannelAtDefaultBoundary() {
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(
                channel("wx_h5", "ENABLED", 300),
                channel("offline_bank", "ENABLED", 1800)));

        assertDoesNotThrow(() -> verifier(true, 3601).afterSingletonsInstantiated());
    }

    @Test
    void shouldRejectStaleExplicitTtlBeforeAcceptingCallbacks() {
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.singletonList(
                channel("offline_bank", "ENABLED", 1800)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> verifier(true, 601).afterSingletonsInstantiated());

        assertTrue(exception.getMessage().contains("nonce TTL"));
    }

    @Test
    void shouldRejectMissingAndUncoveredEnabledWindows() {
        for (Integer windowSeconds : Arrays.asList(null, 0, 1801)) {
            when(paymentConfigMapper.findChannels()).thenReturn(Collections.singletonList(
                    channel("bank", "ENABLED", windowSeconds)));
            assertThrows(IllegalStateException.class,
                    () -> verifier(true, 3601).afterSingletonsInstantiated());
        }
    }

    @Test
    void shouldNotSkipStatusAliasesThatMayRouteOnCaseInsensitiveDatabases() {
        for (String status : Arrays.asList("enabled", "ENABLED ", null)) {
            when(paymentConfigMapper.findChannels()).thenReturn(Collections.singletonList(
                    channel("bank", status, 1801)));
            assertThrows(IllegalStateException.class,
                    () -> verifier(true, 3601).afterSingletonsInstantiated());
        }
    }

    @Test
    void shouldRejectUnverifiableChannelSnapshot() {
        when(paymentConfigMapper.findChannels()).thenReturn(null, Collections.singletonList(null));

        assertThrows(IllegalStateException.class, () -> verifier(true, 3601).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> verifier(true, 3601).afterSingletonsInstantiated());
    }

    @Test
    void shouldIgnoreDisabledChannelAndSkipQueryWhenSignatureDisabled() {
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.singletonList(
                channel("bank", "DISABLED", 1801)));
        assertDoesNotThrow(() -> verifier(true, 3601).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> verifier(false, 601).afterSingletonsInstantiated());
        verify(paymentConfigMapper).findChannels();
    }

    private PaymentCallbackWindowStartupVerifier verifier(boolean requireSignature, long nonceTtlSeconds) {
        return new PaymentCallbackWindowStartupVerifier(paymentConfigMapper, requireSignature, nonceTtlSeconds);
    }

    private PaymentChannelConfigDTO channel(String code, String status, Integer windowSeconds) {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        channel.setChannelCode(code);
        channel.setStatus(status);
        channel.setNotifySignWindowSec(windowSeconds);
        return channel;
    }
}
