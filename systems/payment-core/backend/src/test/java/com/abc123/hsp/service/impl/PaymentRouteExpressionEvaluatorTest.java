package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentRouteContextDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 支付路由复杂表达式求值测试。
 */
class PaymentRouteExpressionEvaluatorTest {

    @Test
    void shouldRespectParenthesesAndBooleanPrecedence() {
        PaymentRouteContextDTO context = buildContext("微信支付", "HOME_CLEAN", "APP_H5", "张女士", "wx_h5", "168");

        assertTrue(PaymentRouteExpressionEvaluator.matches(context,
                "(paymentMethod=支付宝 OR paymentMethod=微信支付) AND amount<=5000"));
        assertFalse(PaymentRouteExpressionEvaluator.matches(context,
                "paymentMethod=支付宝 OR paymentMethod=微信支付 AND amount>5000"));
    }

    @Test
    void shouldSupportNotInQuotedValuesAndAdditionalContextFields() {
        PaymentRouteContextDTO context = buildContext("微信 支付", "HOME_CLEAN", "APP", "张女士", "WX_JSAPI", "168");

        assertTrue(PaymentRouteExpressionEvaluator.matches(context,
                "paymentMethod='微信 支付' AND terminal IN (H5, APP) AND payScene!=HOUSE_REPAIR"
                        + " AND NOT customerType=enterprise AND channelCode IN (wx_h5, alipay_h5)"));
    }

    @Test
    void shouldSupportAllAmountComparisonsAndExistingFlatSyntax() {
        PaymentRouteContextDTO context = buildContext("银行转账", "ENTERPRISE", "PC", "企业客户-晨星科技", "", "5000");

        assertTrue(PaymentRouteExpressionEvaluator.matches(context,
                "amount>=5000 AND amount<=5000 AND amount=5000 AND amount!=4000"
                        + " AND amount>4999 AND amount<5001 OR customerType=personal"));
    }

    @Test
    void shouldTreatMalformedOrUnsupportedExpressionAsNotMatched() {
        PaymentRouteContextDTO context = buildContext("微信支付", "HOME_CLEAN", "H5", "张女士", "", "168");

        assertFalse(PaymentRouteExpressionEvaluator.matches(context, "amount>not-a-number"));
        assertFalse(PaymentRouteExpressionEvaluator.matches(context, "(paymentMethod=微信支付"));
        assertFalse(PaymentRouteExpressionEvaluator.matches(context, "unknownField=value"));
        assertFalse(PaymentRouteExpressionEvaluator.matches(context, "terminal IN ()"));
    }

    private PaymentRouteContextDTO buildContext(String paymentMethod,
                                                String payScene,
                                                String terminal,
                                                String customerName,
                                                String requestedChannelCode,
                                                String amount) {
        PaymentRouteContextDTO context = new PaymentRouteContextDTO();
        context.setPaymentMethod(paymentMethod);
        context.setPayScene(payScene);
        context.setTerminal(terminal);
        context.setCustomerName(customerName);
        context.setRequestedChannelCode(requestedChannelCode);
        context.setAmount(new BigDecimal(amount));
        return context;
    }
}
