package com.zerotrust.security.sre.soar;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class SoarConnectorRegistry {
  private final Map<String,SoarConnector> connectors=new HashMap<>();
  public void register(SoarConnector c) {
      connectors.put(c.name(),c);
  }
  public Optional<SoarConnector> find(String name) {
      return Optional.ofNullable(connectors.get(name));
  }
  public Set<String> names() {
      return Collections.unmodifiableSet(connectors.keySet());
  }
}
