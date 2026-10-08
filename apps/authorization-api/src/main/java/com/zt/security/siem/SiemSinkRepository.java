package com.zt.security.siem;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SiemSinkRepository extends JpaRepository<SiemSink,UUID>{
    List<SiemSink> findByTenantIdAndEnabledTrue(UUID tenant);
    }
