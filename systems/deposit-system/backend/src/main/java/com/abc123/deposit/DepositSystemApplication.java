package com.abc123.deposit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 保证金系统启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.deposit.mapper")
public class DepositSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DepositSystemApplication.class, args);
    }
}
