package com.zt.security.risk;

import com.zt.security.behavior.SecurityGraphService;
import com.zt.security.common.TenantSession;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class IncrementalRiskService {
    private final SecurityGraphService graph;
    private final TenantSession tenant;
    public IncrementalRiskService(SecurityGraphService graph,TenantSession tenant){
        this.graph=graph;
        this.tenant=tenant;
    }

    @Transactional(readOnly=true)
    @Cacheable(cacheNames="security-graph", key="#tenantId.toString()+':'+#windowMinutes")
    public Map<String,Object> snapshot(UUID tenantId,int windowMinutes){
        tenant.set(tenantId);
        return graph.snapshot(tenantId,windowMinutes);
        }

    @Transactional(readOnly=true)
    public Map<String,Object> calculateDelta(UUID tenantId,int windowMinutes,Set<String> changedNodeIds){
        Map<String,Object> current=snapshot(tenantId,windowMinutes);
        List<?> nodes=(List<?>)current.getOrDefault("nodes",List.of());
        List<?> edges=(List<?>)current.getOrDefault("edges",List.of());
        Set<String> affected=new LinkedHashSet<>(changedNodeIds==null?Set.of():changedNodeIds);
        boolean expanded=true;
        int rounds=0;
        while(expanded && rounds++<3){
            expanded=false;
            for(Object o:edges){
                Map<?,?> e=(Map<?,?>)o;
                String from=String.valueOf(e.get("from")),to=String.valueOf(e.get("to"));
                if(affected.contains(from)&&affected.add(to))expanded=true;
                }
                }
        List<Map<String,Object>> affectedNodes=new ArrayList<>();
        for(Object o:nodes){
            Map<String,Object> n=(Map<String,Object>)o;
            if(affected.contains(String.valueOf(n.get("id"))))affectedNodes.
            add(n);
        }
        return Map.of("tenantId",tenantId,"incremental",true,"changedNodes",
        changedNodeIds==null?Set.of():changedNodeIds,"affectedNodeCount",affectedNodes.size(),
        "affectedNodes",affectedNodes,"graphVersion",Integer.toHexString(Objects.hash(nodes.size(),
        edges.size(),affectedNodes.size())));
    }
}
