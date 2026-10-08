package com.zt.security.agent;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
public interface TaskToolRepository extends Repository<AgentTool,UUID>{
    @Query(value="select tool_id from task_tools where task_id=:task",nativeQuery=true) List<UUID>
toolIds(@Param("task") UUID task);
    @Modifying @Transactional @Query(value="insert into task_tools(task_id,tool_id) values(:task" +
",:tool) on conflict do nothing",
    nativeQuery=true) void attach(@Param("task")UUID task,@Param("tool")UUID tool);
}
