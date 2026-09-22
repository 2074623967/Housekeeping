package com.abc123.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 结算出款系统启动入口。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.abc123.settlement.mapper")
public class SettlementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementSystemApplication.class, args);
    }
}
