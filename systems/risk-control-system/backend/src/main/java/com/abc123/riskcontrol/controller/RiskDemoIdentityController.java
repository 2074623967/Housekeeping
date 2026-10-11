package com.abc123.riskcontrol.controller;

import com.abc123.riskcontrol.common.ApiResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-risk-review")
@Conditional(LocalPaymentChainIdentityController.LocalInMemoryH2.class)
@RequestMapping("/api/demo")
public class RiskDemoIdentityController {

    @GetMapping("/identity")
    public ApiResponse<String> identity() {
        return ApiResponse.success("risk-control-h2-local-review");
    }
}
