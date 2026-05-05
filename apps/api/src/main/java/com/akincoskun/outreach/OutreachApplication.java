package com.akincoskun.outreach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OutreachApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutreachApplication.class, args);
    }
}
