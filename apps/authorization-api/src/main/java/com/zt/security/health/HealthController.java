package com.zt.security.health;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController public class HealthController {
    @GetMapping("/v1/health") Map<String,Object> health(){
        return Map.of("status",
        "UP","service","zt-authorization-api","time",Instant.now());
        }
        }
