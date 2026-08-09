package com.abc123.riskcontrol;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 风控系统启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.riskcontrol.mapper")
public class RiskControlSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskControlSystemApplication.class, args);
    }
}

