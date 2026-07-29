package com.abc123.clearing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 清分清算系统启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.clearing.mapper")
public class ClearingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClearingSystemApplication.class, args);
    }
}
