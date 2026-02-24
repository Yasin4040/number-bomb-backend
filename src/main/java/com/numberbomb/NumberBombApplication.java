package com.numberbomb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.numberbomb.mapper")
@EnableScheduling
public class NumberBombApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NumberBombApplication.class, args);
    }
}
