package com.zt.security.settings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TenantSettingsRepository extends JpaRepository<TenantSettings,
UUID>{
}
