package com.abc123.hsp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BusinessChainIdNormalizationTest {

    @Test
    void shouldNormalizePaymentOrderIdBeforeDerivingChainId() {
        assertChainId(PaymentListItemDTO::new, dto -> dto.setPaymentOrderId("  PAY-001  "));
        assertChainId(CashierPageDTO::new, dto -> dto.setPaymentOrderId("  PAY-001  "));
        assertChainId(PrepayOrderDTO::new, dto -> dto.setPaymentOrderId("  PAY-001  "));
        assertChainId(RefundDetailDTO::new, dto -> dto.setPaymentOrderId("  PAY-001  "));
        assertChainId(RefundListItemDTO::new, dto -> dto.setPaymentOrderId("  PAY-001  "));
    }

    @Test
    void shouldKeepChainIdEmptyWhenPaymentOrderIdIsBlank() {
        assertEmptyChainId(PaymentListItemDTO::new, dto -> dto.setPaymentOrderId("  "));
        assertEmptyChainId(CashierPageDTO::new, dto -> dto.setPaymentOrderId(null));
        assertEmptyChainId(PrepayOrderDTO::new, dto -> dto.setPaymentOrderId("  "));
        assertEmptyChainId(RefundDetailDTO::new, dto -> dto.setPaymentOrderId(null));
        assertEmptyChainId(RefundListItemDTO::new, dto -> dto.setPaymentOrderId("  "));
    }

    private <T> void assertChainId(Supplier<T> factory, Consumer<T> setter) {
        T dto = factory.get();
        setter.accept(dto);
        assertEquals("CHAIN-PAY-001", businessChainId(dto));
    }

    private <T> void assertEmptyChainId(Supplier<T> factory, Consumer<T> setter) {
        T dto = factory.get();
        setter.accept(dto);
        assertNull(businessChainId(dto));
    }

    private String businessChainId(Object dto) {
        if (dto instanceof PaymentListItemDTO) {
            return ((PaymentListItemDTO) dto).getBusinessChainId();
        }
        if (dto instanceof CashierPageDTO) {
            return ((CashierPageDTO) dto).getBusinessChainId();
        }
        if (dto instanceof PrepayOrderDTO) {
            return ((PrepayOrderDTO) dto).getBusinessChainId();
        }
        if (dto instanceof RefundDetailDTO) {
            return ((RefundDetailDTO) dto).getBusinessChainId();
        }
        return ((RefundListItemDTO) dto).getBusinessChainId();
    }
}
