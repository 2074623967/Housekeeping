package com.abc123.opsconfig;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 运营配置系统启动入口。
 */
@SpringBootApplication
@MapperScan("com.abc123.opsconfig.mapper")
public class OpsConfigSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsConfigSystemApplication.class, args);
    }
}
