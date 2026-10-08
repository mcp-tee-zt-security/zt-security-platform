package com.zt.security.rbac;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface RoleBindingRepository extends JpaRepository<RoleBinding,
UUID>{
    List<RoleBinding> findByTenantIdAndIdentityId(UUID t,UUID i);
    List<RoleBinding> findByTenantId(UUID t);
}
