package com.zt.security.stitch;

import java.util.*;

/** No inherited ALLOW can override any matching ancestor DENY. Unknown ancestry fails closed. */
public final class StitchAccess {
    private StitchAccess(){}
    public static boolean permits(String id,String permission,String human,Set<String> groups,String ai,
            Map<String,Map<String,Object>> resources,Map<String,List<Map<String,Object>>> grants){
        Set<String> seen=new HashSet<>();boolean humanAllowed=false;String current=id;
        while(current!=null){
            if(!seen.add(current)||seen.size()>64)return false;
            var r=resources.get(current);if(r==null||Boolean.TRUE.equals(r.get("deleted")))return false;
            if(r.get("purge_after") instanceof java.sql.Timestamp expiry&&!expiry.toInstant().isAfter(java.time.Instant.now()))return false;
            if(ai!=null&&!"ALLOW".equals(r.get("ai_access")))return false;
            if(human.equals(r.get("owner_subject")))humanAllowed=true;
            boolean aiAllowRequired=false,aiAllowed=false;
            for(var g:grants.getOrDefault(current,List.of())){
                if(!current.equals(g.get("resource_id"))||!permission.equals(g.get("permission")))continue;
                String kind=(String)g.get("principal_kind"),who=(String)g.get("principal_id");
                boolean humanMatch="SUBJECT".equals(kind)&&human.equals(who)||"GROUP".equals(kind)&&groups.contains(who);
                boolean aiMatch=ai!=null&&"AI_SUBJECT".equals(kind)&&ai.equals(who);
                if(ai!=null&&"AI_SUBJECT".equals(kind)&&"ALLOW".equals(g.get("effect"))){aiAllowRequired=true;if(aiMatch)aiAllowed=true;}
                if((humanMatch||aiMatch)&&"DENY".equals(g.get("effect")))return false;
                if(humanMatch&&"ALLOW".equals(g.get("effect")))humanAllowed=true;
            }
            if(aiAllowRequired&&!aiAllowed)return false;
            current=(String)r.get("parent_id");
        }
        return humanAllowed;
    }
}
