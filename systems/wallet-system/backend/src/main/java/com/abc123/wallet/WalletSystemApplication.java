package com.abc123.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.abc123.wallet.mapper")
public class WalletSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletSystemApplication.class, args);
    }
}
