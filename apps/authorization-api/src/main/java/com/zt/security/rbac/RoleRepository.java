package com.zt.security.rbac;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoleRepository extends JpaRepository<Role,
UUID>{
    Optional<Role> findByTenantIdAndName(UUID t,String n);
    List<Role> findByTenantIdOrderByName(UUID t);
    List<Role> findByTenantId(UUID t);
    }
