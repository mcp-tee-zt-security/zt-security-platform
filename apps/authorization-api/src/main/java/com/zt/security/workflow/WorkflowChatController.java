package com.zt.security.workflow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v1/workflow-chat")
@ConditionalOnProperty(name="zt.workflow.chat.enabled",havingValue="true")
public class WorkflowChatController {
    private final WorkflowChatService chat;private final WorkflowAccess access;
    public WorkflowChatController(WorkflowChatService chat,WorkflowAccess access){this.chat=chat;this.access=access;}
    public record Message(String message){}
    @GetMapping("/proposals") public Object recent(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth){return chat.recent(t,w,auth);}
    @GetMapping("/config") public Object config(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth){access.human(auth,t,w);return chat.config(t,w);}
    @PostMapping("/setup") @PreAuthorize("hasAnyRole('ADMIN','PLATFORM')") public Object setup(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth){return chat.setup(t,w,auth);}
    @PostMapping("/messages") @PreAuthorize("hasAnyRole('REQUESTER','ADMIN','PLATFORM')") public Object message(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@RequestBody Message body){
        access.request(auth,t,w);var reply=chat.respond(body.message(),chat.history(t,w,auth));var saved=chat.saveReply(t,w,auth,body.message(),reply);
        if("RETRIEVAL".equals(saved.get("type"))&&"ALLOW".equals(saved.get("securityDecision"))){var file=read(t,w,UUID.fromString(String.valueOf(saved.get("id"))),auth);var output=new LinkedHashMap<String,Object>(saved);output.put("fileResult",file);return output;}
        return saved;
    }
    @PostMapping("/proposals/{id}/retrieve") public Object retrieve(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return read(t,w,id,auth);}
    private Map<String,Object> read(UUID t,UUID w,UUID id,Authentication auth){
        Map<String,Object> result;
        try{result=chat.readFile(t,w,id,auth);}catch(org.springframework.security.access.AccessDeniedException|IllegalStateException|org.springframework.web.server.ResponseStatusException e){result=Map.of("decision","DENY","reason",Optional.ofNullable(e.getMessage()).orElse("File access unavailable; fail closed"),"results",List.of());}
        chat.recordFileOutcome(t,w,id,auth,result);return result;
    }
    @PostMapping("/proposals/{id}/submit") public Object submit(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return chat.submit(t,w,id,auth);}
    @GetMapping("/proposals/{id}") public Object status(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return chat.status(t,w,id,auth);}
}
