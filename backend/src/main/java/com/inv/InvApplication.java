package com.inv;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.inv.**.mapper")
public class InvApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvApplication.class, args);
    }
}
