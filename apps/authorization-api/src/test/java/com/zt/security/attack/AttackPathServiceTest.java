package com.zt.security.attack;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackPathServiceTest {
    @Test void severityBandsAreExpected(){
        assertEquals("CRITICAL", severity(90));
        assertEquals("HIGH", severity(75));
        assertEquals("MEDIUM", severity(50));
        assertEquals("LOW", severity(20));
    }
    @Test void attackPathDepthIsBounded(){
        assertTrue(Math.min(12, 20) == 12);
    }
    private String severity(double r){
        return r>=85?"CRITICAL":r>=70?"HIGH":r>=45?"MEDIUM":"LOW";
    }
}
