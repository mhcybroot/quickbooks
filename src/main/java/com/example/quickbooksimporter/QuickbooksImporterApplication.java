package com.example.quickbooksimporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class QuickbooksImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickbooksImporterApplication.class, args);
    }
}
