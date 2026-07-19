package com.abc123.hsp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.abc123.hsp.mapper")
@EnableScheduling
public class HomeServicePaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeServicePaymentApplication.class, args);
    }
}
