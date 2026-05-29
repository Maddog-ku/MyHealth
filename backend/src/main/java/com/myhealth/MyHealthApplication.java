package com.myhealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MyHealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyHealthApplication.class, args);
    }
}
