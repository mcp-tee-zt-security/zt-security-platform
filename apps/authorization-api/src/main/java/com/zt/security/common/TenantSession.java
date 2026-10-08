package com.zt.security.common;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component public class TenantSession {
    final EntityManager em;
    TenantSession(EntityManager e){
        em=e;
        }
        public UUID get(){
            Object v=em.createNativeQuery("select nullif(current_setting('app.tenant_id', true)," +
" '')").
        getSingleResult();
        return v==null?null:UUID.fromString(v.toString());
        }
        public void set(UUID tenant){
        em.createNativeQuery("select set_config('app.tenant_id', :tenant, true)").setParameter("tenant",
        tenant.toString()).getSingleResult();
        }
        }
