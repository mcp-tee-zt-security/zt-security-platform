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
    @PostMapping("/messages") @PreAuthorize("hasAnyRole('REQUESTER','ADMIN','PLATFORM')") public Object message(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@RequestBody Message body){access.request(auth,t,w);var reply=chat.respond(body.message(),chat.history(t,w,auth));return chat.saveReply(t,w,auth,body.message(),reply);}
    @PostMapping("/proposals/{id}/retrieve") public Object retrieve(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return chat.readFile(t,w,id,auth);}
    @PostMapping("/proposals/{id}/submit") public Object submit(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return chat.submit(t,w,id,auth);}
    @GetMapping("/proposals/{id}") public Object status(@RequestHeader("X-Tenant-Id") UUID t,@RequestHeader("X-Workspace-Id") UUID w,Authentication auth,@PathVariable UUID id){return chat.status(t,w,id,auth);}
}
