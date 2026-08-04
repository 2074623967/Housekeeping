package com.abc123.refund;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 退款中心启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.refund.mapper")
public class RefundCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefundCenterApplication.class, args);
    }
}

