package com.zt.security.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class WorkspaceSession {
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    public WorkspaceSession(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc){
        this.jdbc=jdbc;
    }
    @Transactional public void set(UUID workspaceId){
        jdbc.queryForObject("select set_config('app.workspace_id', :workspace, true)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("workspace",
                workspaceId == null ? "" : workspaceId.toString()), String.class);
    }
    @Transactional public void clear(){
        set(null);
    }
}
