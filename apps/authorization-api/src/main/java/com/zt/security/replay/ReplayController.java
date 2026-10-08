package com.zt.security.replay;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/replay") public class ReplayController {
    final ReplayService service;
    ReplayController(ReplayService s){
        service=s;
    }
    @PostMapping("/historical") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR')") public
List<ActionReplay> replay(@RequestHeader("X-Tenant-Id")UUID tenant,
@RequestParam(defaultValue="100")int limit){
    return service.replay(tenant,
    limit);
    }
    }
