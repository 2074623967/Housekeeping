package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付路由配置化决策单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentChannelRoutingServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Test
    void shouldRouteByEnabledRule() {
        when(paymentConfigMapper.findEnabledChannelsForRouting()).thenReturn(Arrays.asList(
                buildChannel("wx_h5", "微信支付"),
                buildChannel("alipay_h5", "支付宝")
        ));
        when(paymentConfigMapper.findEnabledRouteRulesForRouting()).thenReturn(Collections.singletonList(
                buildRule("RULE_HOME_WX", "家政 H5 微信优先", "HOME_CLEAN/H5", "paymentMethod=微信支付 AND amount<=5000", "wx_h5", "alipay_h5")
        ));
        when(paymentMapper.sumTodayAcceptedAmountByChannel("wx_h5")).thenReturn(BigDecimal.ZERO);

        PaymentRouteDecisionDTO decision = new PaymentChannelRoutingServiceImpl(paymentConfigMapper, paymentMapper)
                .resolve(buildContext("微信支付", "WX_H5", "HOME_CLEAN", "APP_H5", "张女士", new BigDecimal("168.00")));

        assertEquals("wx_h5", decision.getChannelCode());
        assertEquals("RULE_HOME_WX", decision.getRouteRule());
    }

    @Test
    void shouldFallbackToRequestedChannelWhenNoRuleMatches() {
        when(paymentConfigMapper.findEnabledChannelsForRouting()).thenReturn(Arrays.asList(
                buildChannel("wx_h5", "微信支付"),
                buildChannel("alipay_h5", "支付宝")
        ));
        when(paymentConfigMapper.findEnabledRouteRulesForRouting()).thenReturn(Collections.emptyList());
        when(paymentMapper.sumTodayAcceptedAmountByChannel("alipay_h5")).thenReturn(BigDecimal.ZERO);

        PaymentRouteDecisionDTO decision = new PaymentChannelRoutingServiceImpl(paymentConfigMapper, paymentMapper)
                .resolve(buildContext("支付宝", "ALIPAY_H5", "UNKNOWN", "APP", "李女士", new BigDecimal("88.00")));

        assertEquals("alipay_h5", decision.getChannelCode());
        assertEquals("REQUESTED_CHANNEL", decision.getRouteRule());
    }

    @Test
    void shouldFallbackToPaymentMethodDefaultWhenRequestedChannelMissing() {
        when(paymentConfigMapper.findEnabledChannelsForRouting()).thenReturn(Arrays.asList(
                buildChannel("wx_h5", "微信支付"),
                buildChannel("offline_bank", "银行转账")
        ));
        when(paymentConfigMapper.findEnabledRouteRulesForRouting()).thenReturn(Collections.emptyList());
        when(paymentMapper.sumTodayAcceptedAmountByChannel("offline_bank")).thenReturn(BigDecimal.ZERO);

        PaymentRouteDecisionDTO decision = new PaymentChannelRoutingServiceImpl(paymentConfigMapper, paymentMapper)
                .resolve(buildContext("银行转账", "", "PC", "PC", "企业客户-海星家政", new BigDecimal("6800.00")));

        assertEquals("offline_bank", decision.getChannelCode());
        assertEquals("PAYMENT_METHOD_DEFAULT", decision.getRouteRule());
    }

    @Test
    void shouldUseFallbackChannelWhenRuleTargetDisabled() {
        when(paymentConfigMapper.findEnabledChannelsForRouting()).thenReturn(Arrays.asList(
                buildChannel("alipay_h5", "支付宝")
        ));
        when(paymentConfigMapper.findEnabledRouteRulesForRouting()).thenReturn(Collections.singletonList(
                buildRule("RULE_ENTERPRISE_BANK", "企业大额订单走线下银行", "ENTERPRISE/PC", "amount>5000 OR customerType=ENTERPRISE", "offline_bank", "alipay_h5")
        ));
        when(paymentMapper.sumTodayAcceptedAmountByChannel("alipay_h5")).thenReturn(BigDecimal.ZERO);

        PaymentRouteDecisionDTO decision = new PaymentChannelRoutingServiceImpl(paymentConfigMapper, paymentMapper)
                .resolve(buildContext("银行转账", "BANK_CARD", "ENTERPRISE", "PC", "企业客户-晨星科技", new BigDecimal("12000.00")));

        assertEquals("alipay_h5", decision.getChannelCode());
        assertEquals("RULE_ENTERPRISE_BANK_FALLBACK", decision.getRouteRule());
    }

    @Test
    void shouldSkipChannelWhenDailyLimitExceededAndFallbackToAlternative() {
        when(paymentConfigMapper.findEnabledChannelsForRouting()).thenReturn(Arrays.asList(
                buildChannel("wx_h5", "微信支付", "HOME_CLEAN/H5", new BigDecimal("100.00")),
                buildChannel("alipay_h5", "微信支付", "HOME_CLEAN/H5", new BigDecimal("5000.00"))
        ));
        when(paymentConfigMapper.findEnabledRouteRulesForRouting()).thenReturn(Collections.emptyList());
        when(paymentMapper.sumTodayAcceptedAmountByChannel("wx_h5")).thenReturn(new BigDecimal("90.00"));
        when(paymentMapper.sumTodayAcceptedAmountByChannel("alipay_h5")).thenReturn(BigDecimal.ZERO);

        PaymentRouteDecisionDTO decision = new PaymentChannelRoutingServiceImpl(paymentConfigMapper, paymentMapper)
                .resolve(buildContext("微信支付", "", "HOME_CLEAN", "APP_H5", "张女士", new BigDecimal("20.00")));

        assertEquals("alipay_h5", decision.getChannelCode());
        assertEquals("PAYMENT_METHOD_DEFAULT", decision.getRouteRule());
    }

    private PaymentRouteContextDTO buildContext(
            String paymentMethod,
            String requestedChannelCode,
            String payScene,
            String terminal,
            String customerName,
            BigDecimal amount) {
        PaymentRouteContextDTO context = new PaymentRouteContextDTO();
        context.setPaymentMethod(paymentMethod);
        context.setRequestedChannelCode(requestedChannelCode);
        context.setPayScene(payScene);
        context.setTerminal(terminal);
        context.setCustomerName(customerName);
        context.setAmount(amount);
        return context;
    }

    private PaymentChannelRoutingConfigDTO buildChannel(String channelCode, String paymentMethod) {
        return buildChannel(channelCode, paymentMethod, "HOME_CLEAN/H5/APP/PC/ENTERPRISE", new BigDecimal("99999999.00"));
    }

    private PaymentChannelRoutingConfigDTO buildChannel(String channelCode,
                                                        String paymentMethod,
                                                        String sceneScope,
                                                        BigDecimal dailyLimit) {
        PaymentChannelRoutingConfigDTO channel = new PaymentChannelRoutingConfigDTO();
        channel.setChannelCode(channelCode);
        channel.setPaymentMethod(paymentMethod);
        channel.setSceneScope(sceneScope);
        channel.setDailyLimit(dailyLimit);
        return channel;
    }

    private PaymentRouteRuleRuntimeDTO buildRule(
            String ruleCode,
            String ruleName,
            String matchScene,
            String matchExpression,
            String targetChannelCode,
            String fallbackChannelCode) {
        PaymentRouteRuleRuntimeDTO rule = new PaymentRouteRuleRuntimeDTO();
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleName);
        rule.setMatchScene(matchScene);
        rule.setMatchExpression(matchExpression);
        rule.setTargetChannelCode(targetChannelCode);
        rule.setFallbackChannelCode(fallbackChannelCode);
        return rule;
    }
}
