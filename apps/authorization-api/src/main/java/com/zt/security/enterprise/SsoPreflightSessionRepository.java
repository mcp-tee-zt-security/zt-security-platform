package com.zt.security.enterprise;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SsoPreflightSessionRepository extends JpaRepository<SsoPreflightSession,
UUID>{
    Optional<SsoPreflightSession> findByStateHash(String stateHash);
}
