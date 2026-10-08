package com.zt.security.tenant;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="tenants") public class Tenant {
    @Id UUID id;
    @Column(nullable=false,unique=true) String slug;
    String name;
    String status="ACTIVE";
    Instant createdAt=Instant.now(),updatedAt=Instant.now();
    public UUID getId(){
        return id;
    }
    public void setId(UUID x){
        id=x;
    }
    public String getSlug(){
        return slug;
        }
        public void setSlug(String x){
            slug=x;
        }
        public String getName(){
        return name;
        }
        public void setName(String x){
            name=x;
        }
        public String getStatus(){
        return status;
        }
        }
