package com.zt.security.workflow;
/** Models propose parameters only; authorization and execution stay outside the provider. */
public interface WorkflowPlanner {
    String provider();
    WorkflowReply respond(String message,java.util.List<java.util.Map<String,String>> history);
}
