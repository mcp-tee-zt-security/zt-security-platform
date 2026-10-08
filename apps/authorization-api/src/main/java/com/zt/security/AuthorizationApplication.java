package com.zt.security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.zerotrust.security.config.RiskScoringProperties;
@SpringBootApplication @EnableScheduling
@EnableConfigurationProperties(RiskScoringProperties.class) public class AuthorizationApplication {
    public static void main(String[] args){
        SpringApplication.run(AuthorizationApplication.class,
        args);
        }
        }
