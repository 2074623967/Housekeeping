package com.abc123.gatewayaccess;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关接入系统启动类。
 */
@SpringBootApplication
@MapperScan("com.abc123.gatewayaccess.mapper")
public class GatewayAccessSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayAccessSystemApplication.class, args);
    }
}
