package com.abc123.reconciliation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 对账系统启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.reconciliation.mapper")
public class ReconciliationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationSystemApplication.class, args);
    }
}

