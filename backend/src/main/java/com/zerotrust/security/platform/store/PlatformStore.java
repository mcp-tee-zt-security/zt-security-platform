package com.zerotrust.security.platform.store;

import com.zerotrust.security.platform.domain.GovernanceModels;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlatformStore {
    public final Map<String, GovernanceModels.PolicyDecision> decisions = new ConcurrentHashMap<>();
    public final Map<String, GovernanceModels.Evidence> evidence = new ConcurrentHashMap<>();
    public final Map<String, GovernanceModels.Approval> approvals = new ConcurrentHashMap<>();
    public final Map<String, GovernanceModels.ExecutionContract> contracts = new ConcurrentHashMap<>();
    public final Map<String, GovernanceModels.Execution> executions = new ConcurrentHashMap<>();
    public final Map<String, GovernanceModels.Verification> verifications = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> resources = new ConcurrentHashMap<>();
}
