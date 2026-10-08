package com.zt.security.lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GitOpsSourceRepository extends JpaRepository<GitOpsSource,
UUID>{
    List<GitOpsSource> findByTenantId(UUID t);
}
