use crate::{canonical, models::{Decision, Evidence}, state::AppState};
use base64::{engine::general_purpose::STANDARD, Engine};
use ed25519_dalek::Signer;
use serde_json::json;

pub fn sign(state: &AppState, raw_request: &[u8], decision: Decision, policy: Option<String>,
    version: &str, hash: Option<String>, identity: Option<String>, degraded: bool) -> Option<Evidence> {
    let key = state.0.config.evidence_key.as_ref()?;
    let timestamp = chrono::Utc::now().to_rfc3339();
    let request_hash = canonical::hash(raw_request);
    // Verifiers use these exact UTF-8 bytes, without reconstructing/reordering JSON.
    let signed_payload = json!({
        "protocol": "zt-evaluation-evidence-v2", "decision": decision, "policy": policy,
        "evaluation_scope": "POLICY_ONLY", "degraded": degraded,
        "tenant_id": state.0.config.tenant_id, "workspace_id": state.0.config.workspace_id,
        "bundle_version": version, "bundle_hash": hash, "workload_identity": identity,
        "identity_authenticated": false, "attestation": state.0.attestation,
        "timestamp": timestamp, "request_hash": request_hash,
        "key_id": state.0.config.evidence_key_id,
    }).to_string();
    let payload_hash = canonical::hash(signed_payload.as_bytes());
    let signature = STANDARD.encode(key.sign(signed_payload.as_bytes()).to_bytes());
    Some(Evidence { decision, policy, bundle_version: version.to_owned(), bundle_hash: hash,
        workload_identity: identity, timestamp, payload_hash, request_hash, signature,
        key_id: state.0.config.evidence_key_id.clone(), signed_payload, algorithm: "Ed25519" })
}
