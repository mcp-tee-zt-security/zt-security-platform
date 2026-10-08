package com.zerotrust.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zt.security.risk-scoring")
public class RiskScoringProperties {

    private double maxScore = 100.0;

    private double insiderAccessAnomalyWeight = 0.35;
    private double insiderDataExfiltrationWeight = 0.40;
    private double insiderPrivilegeAbuseWeight = 0.25;

    private double dataSensitivityWeight = 0.40;
    private double dataExposureWeight = 0.35;
    private double dataAccessAnomalyWeight = 0.25;

    private double apiExposureWeight = 0.35;
    private double apiAbuseWeight = 0.40;
    private double apiIdentityWeight = 0.25;

    private double saasPrivilegeWeight = 0.40;
    private double saasConfigWeight = 0.35;
    private double saasShadowWeight = 0.25;

    private double threatSourceTrustWeight = 0.40;
    private double threatCorroborationWeight = 0.35;
    private double threatFreshnessWeight = 0.25;
    private double threatConfidenceRiskWeight = 20.0;

    private double intelligenceEvidenceWeight = 0.40;
    private double intelligenceCorroborationWeight = 0.30;
    private double intelligenceModelWeight = 0.30;

    private double kubernetesImageWeight = 0.30;
    private double kubernetesBehaviorWeight = 0.40;
    private double kubernetesNetworkWeight = 0.30;

    private double networkAnomalyWeight = 0.40;
    private double networkExposureWeight = 0.30;
    private double networkPrivilegeWeight = 0.30;

    private double aiThreatAnomalyWeight = 0.40;
    private double aiThreatIdentityWeight = 0.30;
    private double aiThreatExposureWeight = 0.30;
    private double aiThreatCriticalThreshold = 80.0;
    private double aiThreatHighThreshold = 60.0;

    private double crossCloudAwsWeight = 0.34;
    private double crossCloudAzureWeight = 0.33;
    private double crossCloudGcpWeight = 0.33;

    private double cloudIdentityWeight = 0.30;
    private double cloudConfigWeight = 0.30;
    private double cloudRuntimeWeight = 0.40;

    private double ransomwareEncryptionBurstWeight = 0.45;
    private double ransomwareProcessAnomalyWeight = 0.30;
    private double ransomwareLateralMovementWeight = 0.25;
    private double ransomwareIsolationThreshold = 80.0;

    private double supplyVulnerabilityWeight = 0.50;
    private double supplyProvenanceWeight = 0.20;
    private double supplyExposureWeight = 0.30;

    private double reasoningThreatWeight = 0.40;
    private double reasoningExposureWeight = 0.35;
    private double reasoningConfidenceWeight = 25.0;

    private double trustAttestationThreshold = 70.0;
    private double federationHealthThreshold = 70.0;
    private double detectionCoverageThreshold = 80.0;
    private double detectionPrecisionThreshold = 80.0;
    private double responseSafetyThreshold = 85.0;
    private double responseRollbackThreshold = 85.0;
    private double certificationScoreThreshold = 95.0;
    private double identityFabricMinimumScore = 0.0;
    private double identityFabricAverageDivisor = 4.0;
    private double predictiveCriticalProbability = 0.80;
    private double predictiveHighProbability = 0.60;
    private double deceptionConfidenceThreshold = 0.80;
    private double digitalTwinSafeRiskThreshold = 70.0;
    private double digitalTwinSafeImpactThreshold = 50.0;
    private double mcpToolPoisoningMaxScore = 1.0;
    private double mcpToolPoisoningSuspiciousWeight = 0.20;
    private double mcpToolPoisoningCriticalBase = 0.65;
    private double mcpToolPoisoningHighBase = 0.35;
    private double mcpToolPoisoningNormalBase = 0.05;
    private double continuousDecisionBaseRiskWeight = 0.25;
    private double continuousDecisionBehaviorWeight = 0.30;
    private double continuousDecisionAssetWeight = 0.20;
    private double continuousDecisionAttackWeight = 0.15;
    private double continuousDecisionPolicyWeight = 0.10;
    private double continuousDecisionDenyRisk = 100.0;
    private double continuousDecisionStepUpRisk = 70.0;
    private double continuousDecisionAllowRisk = 10.0;
    private double continuousDecisionHighThreshold = 70.0;
    private double continuousDecisionMediumThreshold = 75.0;
    private double controlNeutralWeight = 0.50;
    private double controlConfidenceCap = 0.95;
    private double controlConfidenceBase = 0.35;
    private double controlConfidenceSampleWeight = 0.35;
    private double controlConfidenceEvidenceWeight = 0.30;
    private double controlSampleDivisor = 10.0;
    private double controlConfidenceSampleDivisor = 20.0;
    private double controlConfidenceRiskDivisor = 20.0;
    private double continuousBaseRisk = 20.0;
    private double continuousDecisionPressureMax = 25.0;
    private double continuousDeniedWeight = 2.0;
    private double continuousStepUpWeight = 0.80;
    private double continuousRiskPressureMax = 30.0;
    private double continuousRiskWeight = 0.30;
    private double continuousAnomalyPressureMax = 25.0;
    private double continuousAnomalyWeight = 2.50;
    private double continuousScoreMax = 100.0;
    private double continuousDirectionThreshold = 8.0;
    private double forecastBaseRisk = 20.0;
    private double forecastDeniedPressureMax = 25.0;
    private double forecastDeniedWeight = 2.0;
    private double forecastStepPressureMax = 12.0;
    private double forecastStepWeight = 0.80;
    private double forecastHighPressureMax = 25.0;
    private double forecastHighWeight = 2.50;
    private double forecastAveragePressureMax = 30.0;
    private double forecastAverageWeight = 0.30;
    private double forecastProbabilityCenter = 55.0;
    private double forecastProbabilityScale = 12.0;
    private double forecastProbabilityDriftScale = 10.0;
    private double forecastProbabilitySampleDivisor = 100.0;
    private double forecastProbabilitySampleWeight = 0.30;
    private double forecastConfidenceCap = 0.98;
    private double forecastConfidenceBase = 0.45;
    private double forecastConfidenceSampleCap = 0.35;
    private double forecastConfidenceSampleDivisor = 100.0;
    private double forecastConfidenceDriftCap = 0.18;
    private double forecastConfidenceDriftDivisor = 100.0;
    private double forecastPreventiveProbability = 0.85;
    private double forecastStepUpProbability = 0.60;
    private double forecastMonitorProbability = 0.40;
    private double decisionBaseRiskWeight = 0.25;
    private double decisionBehaviorWeight = 0.30;
    private double decisionAssetWeight = 0.20;
    private double decisionAttackWeight = 0.15;
    private double decisionPolicyWeight = 0.10;




    public double getMaxScore() { return maxScore; }
    public void setMaxScore(double value) { maxScore = value; }
    public double getInsiderAccessAnomalyWeight() { return insiderAccessAnomalyWeight; }
    public void setInsiderAccessAnomalyWeight(double value) { insiderAccessAnomalyWeight = value; }
    public double getInsiderDataExfiltrationWeight() { return insiderDataExfiltrationWeight; }
    public void setInsiderDataExfiltrationWeight(double value) { insiderDataExfiltrationWeight = value; }
    public double getInsiderPrivilegeAbuseWeight() { return insiderPrivilegeAbuseWeight; }
    public void setInsiderPrivilegeAbuseWeight(double value) { insiderPrivilegeAbuseWeight = value; }
    public double getDataSensitivityWeight() { return dataSensitivityWeight; }
    public void setDataSensitivityWeight(double value) { dataSensitivityWeight = value; }
    public double getDataExposureWeight() { return dataExposureWeight; }
    public void setDataExposureWeight(double value) { dataExposureWeight = value; }
    public double getDataAccessAnomalyWeight() { return dataAccessAnomalyWeight; }
    public void setDataAccessAnomalyWeight(double value) { dataAccessAnomalyWeight = value; }
    public double getApiExposureWeight() { return apiExposureWeight; }
    public void setApiExposureWeight(double value) { apiExposureWeight = value; }
    public double getApiAbuseWeight() { return apiAbuseWeight; }
    public void setApiAbuseWeight(double value) { apiAbuseWeight = value; }
    public double getApiIdentityWeight() { return apiIdentityWeight; }
    public void setApiIdentityWeight(double value) { apiIdentityWeight = value; }
    public double getSaasPrivilegeWeight() { return saasPrivilegeWeight; }
    public void setSaasPrivilegeWeight(double value) { saasPrivilegeWeight = value; }
    public double getSaasConfigWeight() { return saasConfigWeight; }
    public void setSaasConfigWeight(double value) { saasConfigWeight = value; }
    public double getSaasShadowWeight() { return saasShadowWeight; }
    public void setSaasShadowWeight(double value) { saasShadowWeight = value; }
    public double getThreatSourceTrustWeight() { return threatSourceTrustWeight; }
    public void setThreatSourceTrustWeight(double value) { threatSourceTrustWeight = value; }
    public double getThreatCorroborationWeight() { return threatCorroborationWeight; }
    public void setThreatCorroborationWeight(double value) { threatCorroborationWeight = value; }
    public double getThreatFreshnessWeight() { return threatFreshnessWeight; }
    public void setThreatFreshnessWeight(double value) { threatFreshnessWeight = value; }
    public double getThreatConfidenceRiskWeight() { return threatConfidenceRiskWeight; }
    public void setThreatConfidenceRiskWeight(double value) { threatConfidenceRiskWeight = value; }
    public double getIntelligenceEvidenceWeight() { return intelligenceEvidenceWeight; }
    public void setIntelligenceEvidenceWeight(double value) { intelligenceEvidenceWeight = value; }
    public double getIntelligenceCorroborationWeight() { return intelligenceCorroborationWeight; }
    public void setIntelligenceCorroborationWeight(double value) { intelligenceCorroborationWeight = value; }
    public double getIntelligenceModelWeight() { return intelligenceModelWeight; }
    public void setIntelligenceModelWeight(double value) { intelligenceModelWeight = value; }
    public double getKubernetesImageWeight() { return kubernetesImageWeight; }
    public void setKubernetesImageWeight(double value) { kubernetesImageWeight = value; }
    public double getKubernetesBehaviorWeight() { return kubernetesBehaviorWeight; }
    public void setKubernetesBehaviorWeight(double value) { kubernetesBehaviorWeight = value; }
    public double getKubernetesNetworkWeight() { return kubernetesNetworkWeight; }
    public void setKubernetesNetworkWeight(double value) { kubernetesNetworkWeight = value; }
    public double getNetworkAnomalyWeight() { return networkAnomalyWeight; }
    public void setNetworkAnomalyWeight(double value) { networkAnomalyWeight = value; }
    public double getNetworkExposureWeight() { return networkExposureWeight; }
    public void setNetworkExposureWeight(double value) { networkExposureWeight = value; }
    public double getNetworkPrivilegeWeight() { return networkPrivilegeWeight; }
    public void setNetworkPrivilegeWeight(double value) { networkPrivilegeWeight = value; }
    public double getAiThreatAnomalyWeight() { return aiThreatAnomalyWeight; }
    public void setAiThreatAnomalyWeight(double value) { aiThreatAnomalyWeight = value; }
    public double getAiThreatIdentityWeight() { return aiThreatIdentityWeight; }
    public void setAiThreatIdentityWeight(double value) { aiThreatIdentityWeight = value; }
    public double getAiThreatExposureWeight() { return aiThreatExposureWeight; }
    public void setAiThreatExposureWeight(double value) { aiThreatExposureWeight = value; }
    public double getAiThreatCriticalThreshold() { return aiThreatCriticalThreshold; }
    public void setAiThreatCriticalThreshold(double value) { aiThreatCriticalThreshold = value; }
    public double getAiThreatHighThreshold() { return aiThreatHighThreshold; }
    public void setAiThreatHighThreshold(double value) { aiThreatHighThreshold = value; }
    public double getCrossCloudAwsWeight() { return crossCloudAwsWeight; }
    public void setCrossCloudAwsWeight(double value) { crossCloudAwsWeight = value; }
    public double getCrossCloudAzureWeight() { return crossCloudAzureWeight; }
    public void setCrossCloudAzureWeight(double value) { crossCloudAzureWeight = value; }
    public double getCrossCloudGcpWeight() { return crossCloudGcpWeight; }
    public void setCrossCloudGcpWeight(double value) { crossCloudGcpWeight = value; }
    public double getCloudIdentityWeight() { return cloudIdentityWeight; }
    public void setCloudIdentityWeight(double value) { cloudIdentityWeight = value; }
    public double getCloudConfigWeight() { return cloudConfigWeight; }
    public void setCloudConfigWeight(double value) { cloudConfigWeight = value; }
    public double getCloudRuntimeWeight() { return cloudRuntimeWeight; }
    public void setCloudRuntimeWeight(double value) { cloudRuntimeWeight = value; }
    public double getRansomwareEncryptionBurstWeight() { return ransomwareEncryptionBurstWeight; }
    public void setRansomwareEncryptionBurstWeight(double value) { ransomwareEncryptionBurstWeight = value; }
    public double getRansomwareProcessAnomalyWeight() { return ransomwareProcessAnomalyWeight; }
    public void setRansomwareProcessAnomalyWeight(double value) { ransomwareProcessAnomalyWeight = value; }
    public double getRansomwareLateralMovementWeight() { return ransomwareLateralMovementWeight; }
    public void setRansomwareLateralMovementWeight(double value) { ransomwareLateralMovementWeight = value; }
    public double getRansomwareIsolationThreshold() { return ransomwareIsolationThreshold; }
    public void setRansomwareIsolationThreshold(double value) { ransomwareIsolationThreshold = value; }
    public double getSupplyVulnerabilityWeight() { return supplyVulnerabilityWeight; }
    public void setSupplyVulnerabilityWeight(double value) { supplyVulnerabilityWeight = value; }
    public double getSupplyProvenanceWeight() { return supplyProvenanceWeight; }
    public void setSupplyProvenanceWeight(double value) { supplyProvenanceWeight = value; }
    public double getSupplyExposureWeight() { return supplyExposureWeight; }
    public void setSupplyExposureWeight(double value) { supplyExposureWeight = value; }
    public double getReasoningThreatWeight() { return reasoningThreatWeight; }
    public void setReasoningThreatWeight(double value) { reasoningThreatWeight = value; }
    public double getReasoningExposureWeight() { return reasoningExposureWeight; }
    public void setReasoningExposureWeight(double value) { reasoningExposureWeight = value; }
    public double getReasoningConfidenceWeight() { return reasoningConfidenceWeight; }
    public void setReasoningConfidenceWeight(double value) { reasoningConfidenceWeight = value; }
    public double getTrustAttestationThreshold() { return trustAttestationThreshold; }
    public void setTrustAttestationThreshold(double value) { trustAttestationThreshold = value; }
    public double getFederationHealthThreshold() { return federationHealthThreshold; }
    public void setFederationHealthThreshold(double value) { federationHealthThreshold = value; }
    public double getDetectionCoverageThreshold() { return detectionCoverageThreshold; }
    public void setDetectionCoverageThreshold(double value) { detectionCoverageThreshold = value; }
    public double getDetectionPrecisionThreshold() { return detectionPrecisionThreshold; }
    public void setDetectionPrecisionThreshold(double value) { detectionPrecisionThreshold = value; }
    public double getResponseSafetyThreshold() { return responseSafetyThreshold; }
    public void setResponseSafetyThreshold(double value) { responseSafetyThreshold = value; }
    public double getResponseRollbackThreshold() { return responseRollbackThreshold; }
    public void setResponseRollbackThreshold(double value) { responseRollbackThreshold = value; }
    public double getCertificationScoreThreshold() { return certificationScoreThreshold; }
    public void setCertificationScoreThreshold(double value) { certificationScoreThreshold = value; }
    public double getIdentityFabricMinimumScore() { return identityFabricMinimumScore; }
    public void setIdentityFabricMinimumScore(double value) { identityFabricMinimumScore = value; }
    public double getIdentityFabricAverageDivisor() { return identityFabricAverageDivisor; }
    public void setIdentityFabricAverageDivisor(double value) { identityFabricAverageDivisor = value; }
    public double getPredictiveCriticalProbability() { return predictiveCriticalProbability; }
    public void setPredictiveCriticalProbability(double value) { predictiveCriticalProbability = value; }
    public double getPredictiveHighProbability() { return predictiveHighProbability; }
    public void setPredictiveHighProbability(double value) { predictiveHighProbability = value; }
    public double getDeceptionConfidenceThreshold() { return deceptionConfidenceThreshold; }
    public void setDeceptionConfidenceThreshold(double value) { deceptionConfidenceThreshold = value; }
    public double getDigitalTwinSafeRiskThreshold() { return digitalTwinSafeRiskThreshold; }
    public void setDigitalTwinSafeRiskThreshold(double value) { digitalTwinSafeRiskThreshold = value; }
    public double getDigitalTwinSafeImpactThreshold() { return digitalTwinSafeImpactThreshold; }
    public void setDigitalTwinSafeImpactThreshold(double value) { digitalTwinSafeImpactThreshold = value; }
    public double getMcpToolPoisoningMaxScore() { return mcpToolPoisoningMaxScore; }
    public void setMcpToolPoisoningMaxScore(double value) { mcpToolPoisoningMaxScore = value; }
    public double getMcpToolPoisoningSuspiciousWeight() { return mcpToolPoisoningSuspiciousWeight; }
    public void setMcpToolPoisoningSuspiciousWeight(double value) { mcpToolPoisoningSuspiciousWeight = value; }
    public double getMcpToolPoisoningCriticalBase() { return mcpToolPoisoningCriticalBase; }
    public void setMcpToolPoisoningCriticalBase(double value) { mcpToolPoisoningCriticalBase = value; }
    public double getMcpToolPoisoningHighBase() { return mcpToolPoisoningHighBase; }
    public void setMcpToolPoisoningHighBase(double value) { mcpToolPoisoningHighBase = value; }
    public double getMcpToolPoisoningNormalBase() { return mcpToolPoisoningNormalBase; }
    public void setMcpToolPoisoningNormalBase(double value) { mcpToolPoisoningNormalBase = value; }
    public double getContinuousDecisionBaseRiskWeight() { return continuousDecisionBaseRiskWeight; }
    public void setContinuousDecisionBaseRiskWeight(double value) { continuousDecisionBaseRiskWeight = value; }
    public double getContinuousDecisionBehaviorWeight() { return continuousDecisionBehaviorWeight; }
    public void setContinuousDecisionBehaviorWeight(double value) { continuousDecisionBehaviorWeight = value; }
    public double getContinuousDecisionAssetWeight() { return continuousDecisionAssetWeight; }
    public void setContinuousDecisionAssetWeight(double value) { continuousDecisionAssetWeight = value; }
    public double getContinuousDecisionAttackWeight() { return continuousDecisionAttackWeight; }
    public void setContinuousDecisionAttackWeight(double value) { continuousDecisionAttackWeight = value; }
    public double getContinuousDecisionPolicyWeight() { return continuousDecisionPolicyWeight; }
    public void setContinuousDecisionPolicyWeight(double value) { continuousDecisionPolicyWeight = value; }
    public double getContinuousDecisionDenyRisk() { return continuousDecisionDenyRisk; }
    public void setContinuousDecisionDenyRisk(double value) { continuousDecisionDenyRisk = value; }
    public double getContinuousDecisionStepUpRisk() { return continuousDecisionStepUpRisk; }
    public void setContinuousDecisionStepUpRisk(double value) { continuousDecisionStepUpRisk = value; }
    public double getContinuousDecisionAllowRisk() { return continuousDecisionAllowRisk; }
    public void setContinuousDecisionAllowRisk(double value) { continuousDecisionAllowRisk = value; }
    public double getContinuousDecisionHighThreshold() { return continuousDecisionHighThreshold; }
    public void setContinuousDecisionHighThreshold(double value) { continuousDecisionHighThreshold = value; }
    public double getContinuousDecisionMediumThreshold() { return continuousDecisionMediumThreshold; }
    public void setContinuousDecisionMediumThreshold(double value) { continuousDecisionMediumThreshold = value; }
    public double getControlNeutralWeight() { return controlNeutralWeight; }
    public void setControlNeutralWeight(double value) { controlNeutralWeight = value; }

    public double getControlConfidenceCap() { return controlConfidenceCap; }
    public void setControlConfidenceCap(double value) { controlConfidenceCap = value; }

    public double getControlConfidenceBase() { return controlConfidenceBase; }
    public void setControlConfidenceBase(double value) { controlConfidenceBase = value; }

    public double getControlConfidenceSampleWeight() { return controlConfidenceSampleWeight; }
    public void setControlConfidenceSampleWeight(double value) { controlConfidenceSampleWeight = value; }

    public double getControlConfidenceEvidenceWeight() { return controlConfidenceEvidenceWeight; }
    public void setControlConfidenceEvidenceWeight(double value) { controlConfidenceEvidenceWeight = value; }

    public double getControlSampleDivisor() { return controlSampleDivisor; }
    public void setControlSampleDivisor(double value) { controlSampleDivisor = value; }

    public double getControlConfidenceSampleDivisor() { return controlConfidenceSampleDivisor; }
    public void setControlConfidenceSampleDivisor(double value) { controlConfidenceSampleDivisor = value; }

    public double getControlConfidenceRiskDivisor() { return controlConfidenceRiskDivisor; }
    public void setControlConfidenceRiskDivisor(double value) { controlConfidenceRiskDivisor = value; }

    public double getContinuousBaseRisk() { return continuousBaseRisk; }
    public void setContinuousBaseRisk(double value) { continuousBaseRisk = value; }

    public double getContinuousDecisionPressureMax() { return continuousDecisionPressureMax; }
    public void setContinuousDecisionPressureMax(double value) { continuousDecisionPressureMax = value; }

    public double getContinuousDeniedWeight() { return continuousDeniedWeight; }
    public void setContinuousDeniedWeight(double value) { continuousDeniedWeight = value; }

    public double getContinuousStepUpWeight() { return continuousStepUpWeight; }
    public void setContinuousStepUpWeight(double value) { continuousStepUpWeight = value; }

    public double getContinuousRiskPressureMax() { return continuousRiskPressureMax; }
    public void setContinuousRiskPressureMax(double value) { continuousRiskPressureMax = value; }

    public double getContinuousRiskWeight() { return continuousRiskWeight; }
    public void setContinuousRiskWeight(double value) { continuousRiskWeight = value; }

    public double getContinuousAnomalyPressureMax() { return continuousAnomalyPressureMax; }
    public void setContinuousAnomalyPressureMax(double value) { continuousAnomalyPressureMax = value; }

    public double getContinuousAnomalyWeight() { return continuousAnomalyWeight; }
    public void setContinuousAnomalyWeight(double value) { continuousAnomalyWeight = value; }

    public double getContinuousScoreMax() { return continuousScoreMax; }
    public void setContinuousScoreMax(double value) { continuousScoreMax = value; }

    public double getContinuousDirectionThreshold() { return continuousDirectionThreshold; }
    public void setContinuousDirectionThreshold(double value) { continuousDirectionThreshold = value; }

    public double getForecastBaseRisk() { return forecastBaseRisk; }
    public void setForecastBaseRisk(double value) { forecastBaseRisk = value; }

    public double getForecastDeniedPressureMax() { return forecastDeniedPressureMax; }
    public void setForecastDeniedPressureMax(double value) { forecastDeniedPressureMax = value; }

    public double getForecastDeniedWeight() { return forecastDeniedWeight; }
    public void setForecastDeniedWeight(double value) { forecastDeniedWeight = value; }

    public double getForecastStepPressureMax() { return forecastStepPressureMax; }
    public void setForecastStepPressureMax(double value) { forecastStepPressureMax = value; }

    public double getForecastStepWeight() { return forecastStepWeight; }
    public void setForecastStepWeight(double value) { forecastStepWeight = value; }

    public double getForecastHighPressureMax() { return forecastHighPressureMax; }
    public void setForecastHighPressureMax(double value) { forecastHighPressureMax = value; }

    public double getForecastHighWeight() { return forecastHighWeight; }
    public void setForecastHighWeight(double value) { forecastHighWeight = value; }

    public double getForecastAveragePressureMax() { return forecastAveragePressureMax; }
    public void setForecastAveragePressureMax(double value) { forecastAveragePressureMax = value; }

    public double getForecastAverageWeight() { return forecastAverageWeight; }
    public void setForecastAverageWeight(double value) { forecastAverageWeight = value; }

    public double getForecastProbabilityCenter() { return forecastProbabilityCenter; }
    public void setForecastProbabilityCenter(double value) { forecastProbabilityCenter = value; }

    public double getForecastProbabilityScale() { return forecastProbabilityScale; }
    public void setForecastProbabilityScale(double value) { forecastProbabilityScale = value; }

    public double getForecastProbabilityDriftScale() { return forecastProbabilityDriftScale; }
    public void setForecastProbabilityDriftScale(double value) { forecastProbabilityDriftScale = value; }

    public double getForecastProbabilitySampleDivisor() { return forecastProbabilitySampleDivisor; }
    public void setForecastProbabilitySampleDivisor(double value) { forecastProbabilitySampleDivisor = value; }

    public double getForecastProbabilitySampleWeight() { return forecastProbabilitySampleWeight; }
    public void setForecastProbabilitySampleWeight(double value) { forecastProbabilitySampleWeight = value; }

    public double getForecastConfidenceCap() { return forecastConfidenceCap; }
    public void setForecastConfidenceCap(double value) { forecastConfidenceCap = value; }

    public double getForecastConfidenceBase() { return forecastConfidenceBase; }
    public void setForecastConfidenceBase(double value) { forecastConfidenceBase = value; }

    public double getForecastConfidenceSampleCap() { return forecastConfidenceSampleCap; }
    public void setForecastConfidenceSampleCap(double value) { forecastConfidenceSampleCap = value; }

    public double getForecastConfidenceSampleDivisor() { return forecastConfidenceSampleDivisor; }
    public void setForecastConfidenceSampleDivisor(double value) { forecastConfidenceSampleDivisor = value; }

    public double getForecastConfidenceDriftCap() { return forecastConfidenceDriftCap; }
    public void setForecastConfidenceDriftCap(double value) { forecastConfidenceDriftCap = value; }

    public double getForecastConfidenceDriftDivisor() { return forecastConfidenceDriftDivisor; }
    public void setForecastConfidenceDriftDivisor(double value) { forecastConfidenceDriftDivisor = value; }

    public double getForecastPreventiveProbability() { return forecastPreventiveProbability; }
    public void setForecastPreventiveProbability(double value) { forecastPreventiveProbability = value; }

    public double getForecastStepUpProbability() { return forecastStepUpProbability; }
    public void setForecastStepUpProbability(double value) { forecastStepUpProbability = value; }

    public double getForecastMonitorProbability() { return forecastMonitorProbability; }
    public void setForecastMonitorProbability(double value) { forecastMonitorProbability = value; }
    public double getDecisionBaseRiskWeight() { return decisionBaseRiskWeight; }
    public void setDecisionBaseRiskWeight(double value) { decisionBaseRiskWeight = value; }
    public double getDecisionBehaviorWeight() { return decisionBehaviorWeight; }
    public void setDecisionBehaviorWeight(double value) { decisionBehaviorWeight = value; }
    public double getDecisionAssetWeight() { return decisionAssetWeight; }
    public void setDecisionAssetWeight(double value) { decisionAssetWeight = value; }
    public double getDecisionAttackWeight() { return decisionAttackWeight; }
    public void setDecisionAttackWeight(double value) { decisionAttackWeight = value; }
    public double getDecisionPolicyWeight() { return decisionPolicyWeight; }
    public void setDecisionPolicyWeight(double value) { decisionPolicyWeight = value; }



}
