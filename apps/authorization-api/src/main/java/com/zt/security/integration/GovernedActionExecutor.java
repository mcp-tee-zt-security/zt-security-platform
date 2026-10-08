package com.zt.security.integration;

import com.zt.security.action.EvaluateModels;
import java.util.Map;

/** An installed connector must perform and verify a real external action. No default executor is supplied. */
public interface GovernedActionExecutor {
    String action();
    Map<String,Object> execute(String contractId, EvaluateModels.EvaluateRequest request);
    boolean verify(Map<String,Object> result);
}
