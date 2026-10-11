package com.zt.security.workflow;

import com.zt.security.action.EvaluateModels;
import com.zt.security.integration.GovernedActionExecutor;
import com.zt.security.integration.GovernanceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@ConditionalOnProperty(name="zt.workflow.simulation.enabled",havingValue="true")
public class SimulatedTransferExecutor implements GovernedActionExecutor {
    private final JdbcTemplate jdbc;
    public SimulatedTransferExecutor(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String action(){return "simulation.transfer";}
    public Map<String,Object> execute(String contractId,EvaluateModels.EvaluateRequest request){
        var context=request.context();
        var proposal=new TransferProposal(String.valueOf(context.get("recipient")),new java.math.BigDecimal(String.valueOf(context.get("amount"))),String.valueOf(context.get("currency")));
        UUID tenant=GovernanceStore.uuid(context.get("_workflowTenant")),workspace=GovernanceStore.uuid(context.get("_workflowWorkspace"));
        UUID contract=GovernanceStore.uuid(contractId);
        jdbc.update("INSERT INTO workflow_simulated_transfers(tenant_id,workspace_id,contract_id,recipient,amount,currency) VALUES(?,?,?,?,?,?) ON CONFLICT(contract_id) DO NOTHING",tenant,workspace,contract,proposal.recipient(),proposal.amount(),proposal.currency());
        var row=jdbc.queryForMap("SELECT id,recipient,amount,currency,status FROM workflow_simulated_transfers WHERE contract_id=? AND tenant_id=? AND workspace_id=?",contract,tenant,workspace);
        if(!proposal.recipient().equals(row.get("recipient"))||proposal.amount().compareTo((java.math.BigDecimal)row.get("amount"))!=0||!proposal.currency().equals(row.get("currency")))throw new IllegalStateException("Simulated transaction does not match saved request");
        return Map.of("simulation",true,"executed",true,"transactionId",row.get("id").toString(),"contractId",contractId,
            "recipient",row.get("recipient"),"amount",row.get("amount"),"currency",row.get("currency"),"status",row.get("status"),"tenantId",tenant.toString(),"workspaceId",workspace.toString());
    }
    public boolean verify(Map<String,Object> result){
        var rows=jdbc.queryForList("SELECT recipient,amount,currency,status FROM workflow_simulated_transfers WHERE id=? AND contract_id=? AND tenant_id=? AND workspace_id=?",
            GovernanceStore.uuid(result.get("transactionId")),GovernanceStore.uuid(result.get("contractId")),GovernanceStore.uuid(result.get("tenantId")),GovernanceStore.uuid(result.get("workspaceId")));
        if(rows.size()!=1)return false;
        var row=rows.get(0);
        return Boolean.TRUE.equals(result.get("simulation"))&&Objects.equals(row.get("recipient"),result.get("recipient"))
            &&((java.math.BigDecimal)row.get("amount")).compareTo(new java.math.BigDecimal(String.valueOf(result.get("amount"))))==0
            &&Objects.equals(row.get("currency"),result.get("currency"))&&"COMPLETED".equals(row.get("status"));
    }
}
