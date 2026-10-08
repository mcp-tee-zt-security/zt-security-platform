package com.zt.security.agent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.
annotation.Transactional;
import com.zt.security.common.TenantSession;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/agents") public class AgentTaskController {
    final AgentTaskRepository tasks;
    final AgentToolRepository tools;
    final TaskToolRepository links;
    final TenantSession session;
    AgentTaskController(AgentTaskRepository t,
    AgentToolRepository a,TaskToolRepository l,TenantSession s){
        tasks=t;
        tools=a;
        links=l;
        session=s;
        }
 @PostMapping("/tasks") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')") @Transactional
public AgentTask createTask(@RequestHeader("X-Tenant-Id")UUID t,
 @RequestBody AgentTask x){
     session.set(t);
     x.setTenantId(t);
     return tasks.save(x);
 }
 @PostMapping("/tools") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')") @Transactional
public AgentTool createTool(@RequestHeader("X-Tenant-Id")UUID t,
 @RequestBody AgentTool x){
     session.set(t);
     x.setTenantId(t);
     return tools.save(x);
 }
 @PostMapping("/tasks/{task}/tools/{tool}") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AGENT_MANAGER')")
@Transactional public Map<String,
 Object> attach(@RequestHeader("X-Tenant-Id")UUID t,@PathVariable UUID task,
 @PathVariable UUID tool){
     session.set(t);
     links.attach(task,tool);
     return Map.of("taskId",
     task,"toolId",tool,"status","ATTACHED");
     }
 @GetMapping("/tasks/{task}/tools") @PreAuthorize("hasAnyRole('PLATFORM','ADMIN','AUDITOR'" +
",'AGENT_MANAGER')") @Transactional(readOnly=
 true) public List<UUID> graph(@RequestHeader("X-Tenant-Id")UUID t,
 @PathVariable UUID task){
     session.set(t);
     return links.toolIds(task);
 }
}
