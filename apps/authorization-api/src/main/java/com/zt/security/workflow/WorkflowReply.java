package com.zt.security.workflow;

/** The model can answer or propose a bounded operation; it never authorizes access. */
public record WorkflowReply(String type,String answer,TransferProposal transfer,String target,String operation){
    public WorkflowReply(String type,String answer,TransferProposal transfer,String target){this(type,answer,transfer,target,"READ");}
    public WorkflowReply {
        if(!java.util.Set.of("CHAT","TRANSFER","RETRIEVAL").contains(type))throw new IllegalArgumentException("Unsupported chat operation");
        if(answer==null||answer.isBlank()||answer.length()>12000)throw new IllegalArgumentException("A bounded assistant answer is required");
        if("TRANSFER".equals(type)&&transfer==null)throw new IllegalArgumentException("Transfer parameters required");
        if("RETRIEVAL".equals(type)&&(target==null||target.isBlank()||target.length()>256))throw new IllegalArgumentException("A folder or file name is required");
        if("RETRIEVAL".equals(type)&&!java.util.Set.of("READ","LIST").contains(operation))throw new IllegalArgumentException("READ or LIST operation required");
    }
}
