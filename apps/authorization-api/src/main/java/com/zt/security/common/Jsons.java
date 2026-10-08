package com.zt.security.common;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
public final class Jsons {
    private Jsons(){
    }
public static Map<String,
    Object> map(ObjectMapper m,String s){
        try{
            return m.readValue(s,new TypeReference<>(){
            }
            );
            }
            catch(Exception e){
                return Map.of();
            }
            }
            public static String string(ObjectMapper m,
Object o){
    try{
        return m.writeValueAsString(o);
    }
catch(Exception e){
    return "{}";
    }
    }
    }
