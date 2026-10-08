package com.zt.security.governance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PolicyLintServiceTest {
    @Test void wildcardAllowIsCritical(){
        var x=new
PolicyLintService().lint("policy \"x\" { effect allow action == \"*\"" +
" resource.type == \"*\" }");
        assertTrue(x.stream().anyMatch(i->"CRITICAL".equals(i.get("severity"))));
    }
    @Test void normalPolicyPasses(){
        var x=new PolicyLintService().lint("policy \"x\" { effect deny action ==" +
" \"payment.transfer\" }");
    assertTrue(x.stream().noneMatch(i->"ERROR".equals(i.get("severity"))));
}
}
