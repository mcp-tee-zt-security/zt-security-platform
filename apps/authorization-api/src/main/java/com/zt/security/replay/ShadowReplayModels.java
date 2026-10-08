package com.zt.security.replay;
import java.time.Instant;
import java.util.*;
public final class ShadowReplayModels {
 public record ReplayRequest(String policyText, Instant from, Instant to, Integer maxEvents, UUID workspaceId) {
 }
 public record ReplayRow(UUID eventId, String action, String resourceType,
 String resourceId, String baseline, String shadow, boolean changed, boolean falsePositiveCandidate,
 double businessImpact, String agent, Instant createdAt) {
 }
 public record ReplayResponse(UUID jobId,String mode,String policyHash,
 String resultHash,int eventCount,int changedDecisions,int newDenies,int newStepUps,
 int falsePositiveCandidates,double securityImprovementPct,double businessImpactScore,
 List<ReplayRow> rows,String status) {
 }
}
