package com.zt.security.common;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.zt.security.scim.ScimTokenFilter;
import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Value("${zt.security.oidc.enabled:false}") boolean oidcEnabled;
  @Value("${zt.security.oidc.issuer-uri:http://localhost:8089/realms/zt}") String issuer;
  @Value("${zt.security.oidc.jwk-set-uri:}") String jwkSetUri;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ApiKeyFilter apiKey,
  ScimTokenFilter scimToken, RateLimitFilter rateLimit, TenantSecurityFilter tenantFilter) throws Exception {
    http.csrf(c->c.disable())
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(e->e
        .authenticationEntryPoint((request,response,exception)->{
          response.setStatus(401);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Valid X-API-Key or bearer token required\"}");
        })
        .accessDeniedHandler((request,response,exception)->{
          response.setStatus(403);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Insufficient permissions\"}");
        }))
      .authorizeHttpRequests(a->a
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers("/actuator/**","/swagger-ui/**","/swagger-ui.html",
        "/v3/api-docs/**","/v1/health","/v1/enterprise/sso/login/callback").permitAll()
        .anyRequest().authenticated())
      .addFilterBefore(apiKey, BearerTokenAuthenticationFilter.class)
      .addFilterBefore(scimToken, BearerTokenAuthenticationFilter.class)
      .addFilterAfter(rateLimit, ApiKeyFilter.class)
      .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);
    if (oidcEnabled) http.oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(name="zt.security.oidc.enabled", havingValue="true")
  JwtDecoder jwtDecoder() {
      if(jwkSetUri!=null&&!jwkSetUri.isBlank()){
          var decoder=org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
          decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer));
          return decoder;
      }
      return JwtDecoders.fromIssuerLocation(issuer);
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var c = new JwtAuthenticationConverter();
    c.setJwtGrantedAuthoritiesConverter(jwt -> {
      Set<org.springframework.security.core.GrantedAuthority> out = new HashSet<>();
      Object roles = jwt.getClaims().get("roles");
      if (roles instanceof Collection<?> rs) rs.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" +
      r.toString().toUpperCase())));
      Object realm = jwt.getClaims().get("realm_access");
      if (realm instanceof Map<?,?> rm && rm.get("roles") instanceof Collection<?
      > rs) rs.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase())));
      Object scope = jwt.getClaims().get("scope");
      if (scope instanceof String s) Arrays.stream(s.split(" ")).forEach(x ->
      out.add(new SimpleGrantedAuthority("SCOPE_"+x)));
      return out;
    }
    );
    return c;
  }
}
