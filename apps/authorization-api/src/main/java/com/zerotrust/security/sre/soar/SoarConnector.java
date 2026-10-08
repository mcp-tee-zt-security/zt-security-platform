package com.zerotrust.security.sre.soar;
import java.util.Map;
public interface SoarConnector {
  String name();
  boolean health();
  Map<String,Object> execute(String actionType, Map<String,Object> parameters);
}
