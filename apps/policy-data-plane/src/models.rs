use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Bundle {
    #[serde(default)]
    pub canonical_version: u32,
    pub version: String,
    #[serde(default)]
    pub tenant_id: String,
    #[serde(default)]
    pub workspace_id: Option<String>,
    pub generated_at: String,
    pub signature: Option<String>,
    pub key_id: Option<String>,
    pub bundle_hash: Option<String>,
    pub policies: Vec<FastPolicy>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FastPolicy {
    pub name: String,
    pub version: i32,
    pub priority: i32,
    pub effect: String,
    pub principal_type: Option<String>,
    pub actions: Vec<String>,
    pub resource_type: Option<String>,
    #[serde(default)]
    pub condition: Option<String>,
    pub fast_path: bool,
}

#[derive(Clone, Deserialize)]
pub struct EvalRequest {
    pub principal: Principal,
    pub action: Action,
    pub resource: Resource,
    #[serde(default)]
    pub context: HashMap<String, Value>,
}
#[derive(Clone, Deserialize)]
pub struct Principal { pub id: String, #[serde(rename = "type")] pub kind: String }
#[derive(Clone, Deserialize)]
pub struct Action { pub name: String }
#[derive(Clone, Deserialize)]
pub struct Resource { #[serde(rename = "type")] pub kind: String, pub id: String }

impl EvalRequest {
    pub fn valid(&self) -> bool {
        [&self.principal.id, &self.principal.kind, &self.action.name, &self.resource.kind, &self.resource.id]
            .iter().all(|value| !value.trim().is_empty() && value.len() <= 512)
            && self.context.len() <= 256
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Decision { Allow, Deny, StepUp, Defer }
impl Decision {
    pub fn label(self) -> &'static str {
        match self { Self::Allow => "ALLOW", Self::Deny => "DENY", Self::StepUp => "STEP_UP", Self::Defer => "DEFER" }
    }
}

#[derive(Serialize)]
pub struct EvalResponse {
    pub decision: Decision,
    pub reason: String,
    pub reason_code: &'static str,
    pub policy: Option<String>,
    pub engine: &'static str,
    pub evaluation_scope: &'static str,
    pub bundle_version: String,
    pub latency_us: u128,
    pub bundle_age_seconds: Option<u64>,
    pub bundle_hash: Option<String>,
    pub workload_identity: Option<String>,
    pub identity_authenticated: bool,
    pub degraded: bool,
    pub evidence: Option<Evidence>,
}

#[derive(Clone, Serialize)]
pub struct Evidence {
    pub decision: Decision,
    pub policy: Option<String>,
    pub bundle_version: String,
    pub bundle_hash: Option<String>,
    pub workload_identity: Option<String>,
    pub timestamp: String,
    pub payload_hash: String,
    pub request_hash: String,
    pub signature: String,
    pub key_id: String,
    pub signed_payload: String,
    pub algorithm: &'static str,
}

#[derive(Clone, Serialize)]
pub struct AttestationState {
    pub provider: String,
    pub mode: String,
    pub status: &'static str,
    pub document_hash: Option<String>,
    pub expected_pcr3: Option<String>,
    pub expected_pcr8: Option<String>,
    pub nonce: Option<String>,
    pub key_id: Option<String>,
    pub verified_at: Option<String>,
    pub reason: &'static str,
}

