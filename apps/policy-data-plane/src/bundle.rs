use crate::{canonical, condition::{self, Condition}, config::Config, models::{Bundle, FastPolicy}};
use base64::{engine::general_purpose::STANDARD, Engine};
use chrono::{DateTime, Utc};
use ed25519_dalek::Signature;
use serde::Serialize;
use std::{collections::HashSet, time::{Duration, Instant}};

#[derive(Clone, Copy, Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SignatureState { Verified, UnsignedDevelopment }
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Effect { Allow, Deny, StepUp }
pub struct CompiledPolicy {
    pub policy: FastPolicy,
    pub effect: Effect,
    pub condition: Condition,
}
pub struct ValidatedBundle {
    pub bundle: Bundle,
    pub policies: Vec<CompiledPolicy>,
    pub signature_state: SignatureState,
    pub generated_at: DateTime<Utc>,
    accepted_at: Instant,
    age_at_acceptance: Duration,
}
impl ValidatedBundle {
    pub fn age_seconds(&self) -> u64 {
        self.age_at_acceptance.saturating_add(self.accepted_at.elapsed()).as_secs()
    }
    pub fn expired(&self, maximum: Duration) -> bool {
        self.age_at_acceptance.saturating_add(self.accepted_at.elapsed()) > maximum
    }
    pub fn signature_verified(&self) -> bool { self.signature_state == SignatureState::Verified }
}

#[derive(Debug)]
pub struct Rejection { pub code: &'static str, pub message: &'static str }
impl std::fmt::Display for Rejection {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { write!(f, "{}: {}", self.code, self.message) }
}
impl std::error::Error for Rejection {}
fn reject<T>(code: &'static str, message: &'static str) -> Result<T, Rejection> { Err(Rejection { code, message }) }

pub fn validate(bundle: Bundle, config: &Config, now: DateTime<Utc>) -> Result<ValidatedBundle, Rejection> {
    if bundle.canonical_version != canonical::CANONICAL_VERSION {
        return reject("CANONICAL_VERSION_UNSUPPORTED", "Only signed-metadata canonical format v2 is accepted");
    }
    if bundle.tenant_id != config.tenant_id || bundle.workspace_id != config.workspace_id {
        return reject("BUNDLE_SCOPE_MISMATCH", "Bundle tenant/workspace does not match this instance");
    }
    if bundle.policies.len() > 10_000 {
        return reject("POLICY_LIMIT_EXCEEDED", "Bundle contains too many policies");
    }
    let generated = DateTime::parse_from_rfc3339(&bundle.generated_at)
        .map_err(|_| Rejection { code: "BUNDLE_TIMESTAMP_INVALID", message: "Bundle timestamp is not RFC3339" })?
        .with_timezone(&Utc);
    let age = now.signed_duration_since(generated);
    if generated.signed_duration_since(now).num_milliseconds() > config.max_future_skew.as_millis() as i64 {
        return reject("BUNDLE_TIMESTAMP_IN_FUTURE", "Bundle exceeds the permitted future clock skew");
    }
    let age = age.to_std().unwrap_or(Duration::ZERO);
    if age > config.max_bundle_age {
        return reject("BUNDLE_STALE", "Candidate bundle has already expired");
    }
    let payload = canonical::payload(&bundle);
    let payload_hash = canonical::hash(&payload);
    if bundle.bundle_hash.as_deref() != Some(payload_hash.as_str()) {
        return reject("BUNDLE_HASH_MISMATCH", "Bundle hash does not match its canonical bytes");
    }
    if bundle.version != canonical::revision(&bundle) {
        return reject("BUNDLE_REVISION_MISMATCH", "Bundle revision does not identify its policy content");
    }
    if config.bundle_pin.as_ref().is_some_and(|pin| pin != &bundle.version) {
        return reject("BUNDLE_PIN_MISMATCH", "Bundle revision differs from the configured pin");
    }
    if config.expected_key_id.as_ref().is_some_and(|id| bundle.key_id.as_ref() != Some(id)) {
        return reject("BUNDLE_KEY_ID_MISMATCH", "Bundle key ID differs from the configured key ID");
    }
    let signature_state = match (&config.verify_key, &bundle.signature) {
        (Some(key), Some(encoded)) if !encoded.is_empty() => {
            let bytes = STANDARD.decode(encoded).map_err(|_| Rejection {
                code: "SIGNATURE_ENCODING_INVALID", message: "Bundle signature is not Base64",
            })?;
            let bytes: [u8; 64] = bytes.try_into().map_err(|_| Rejection {
                code: "SIGNATURE_LENGTH_INVALID", message: "Ed25519 signature must contain 64 bytes",
            })?;
            key.verify_strict(&payload, &Signature::from_bytes(&bytes)).map_err(|_| Rejection {
                code: "SIGNATURE_INVALID", message: "Bundle signature verification failed",
            })?;
            SignatureState::Verified
        }
        (None, None) if config.allow_unsigned => SignatureState::UnsignedDevelopment,
        (None, Some(encoded)) if encoded.is_empty() && config.allow_unsigned => SignatureState::UnsignedDevelopment,
        _ => return reject("SIGNATURE_REQUIRED", "Missing signature or configured verification key"),
    };
    let mut seen = HashSet::new();
    let mut policies = Vec::with_capacity(bundle.policies.len());
    let mut ordered = bundle.policies.clone();
    ordered.sort_by(|a, b| a.priority.cmp(&b.priority).then(a.name.cmp(&b.name)).then(a.version.cmp(&b.version)));
    for policy in ordered {
        if policy.name.trim().is_empty() || policy.name.len() > 512 || policy.version < 1
            || !seen.insert((policy.name.clone(), policy.version))
            || policy.actions.len() > 1024 || policy.actions.iter().any(|a| a.is_empty() || a.len() > 512) {
            return reject("POLICY_METADATA_INVALID", "Invalid or duplicate policy metadata");
        }
        let effect = match policy.effect.trim().to_ascii_lowercase().as_str() {
            "allow" => Effect::Allow, "deny" => Effect::Deny, "step_up" => Effect::StepUp,
            _ => return reject("POLICY_EFFECT_UNSUPPORTED", "Unknown policy effect"),
        };
        if policy.fast_path && (policy.principal_type.as_ref().is_none_or(|v| v.trim().is_empty())
            || policy.resource_type.as_ref().is_none_or(|v| v.trim().is_empty()) || policy.actions.is_empty()) {
            return reject("FAST_SELECTOR_INVALID", "Fast policies require explicit principal/resource/action selectors");
        }
        let condition = condition::compile(policy.condition.as_deref());
        policies.push(CompiledPolicy { policy, effect, condition });
    }
    Ok(ValidatedBundle {
        bundle, policies, signature_state, generated_at: generated,
        accepted_at: Instant::now(), age_at_acceptance: age,
    })
}

