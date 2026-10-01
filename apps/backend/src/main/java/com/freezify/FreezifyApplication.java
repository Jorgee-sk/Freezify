package com.freezify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FreezifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreezifyApplication.class, args);
    }
}
