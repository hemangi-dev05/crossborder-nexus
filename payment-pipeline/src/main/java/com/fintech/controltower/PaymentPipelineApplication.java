package com.fintech.controltower;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentPipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentPipelineApplication.class, args);
        System.out.println("\n=======================================================");
        System.out.println("🏛️  CROSS-BORDER CONTROL TOWER ONLINE ON PORT 8080  🚀");
        System.out.println("=======================================================");
    }
}
