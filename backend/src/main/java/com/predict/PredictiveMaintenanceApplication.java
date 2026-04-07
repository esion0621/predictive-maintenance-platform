package com.predict;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.predict.mapper")
public class PredictiveMaintenanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PredictiveMaintenanceApplication.class, args);
    }
}
