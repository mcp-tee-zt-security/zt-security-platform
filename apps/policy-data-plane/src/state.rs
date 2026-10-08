use crate::{attestation, bundle::{self, Rejection, ValidatedBundle}, config::Config,
    metrics::Stats, models::{AttestationState, Bundle}};
use parking_lot::RwLock;
use serde::Serialize;
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};

#[derive(Clone)]
pub struct AppState(pub Arc<Inner>);
pub struct Inner {
    pub config: Config,
    pub client: reqwest::Client,
    pub active: RwLock<Option<Arc<ValidatedBundle>>>,
    pub stats: Stats,
    pub refresh: RwLock<RefreshStatus>,
    pub attestation: AttestationState,
    pub stopping: AtomicBool,
}
#[derive(Default, Clone, Serialize)]
pub struct RefreshStatus {
    pub attempts: u64,
    pub successes: u64,
    pub failures: u64,
    pub rejections: u64,
    pub consecutive_failures: u64,
    pub last_attempt_at: Option<String>,
    pub last_success_at: Option<String>,
    pub last_error_code: Option<String>,
    pub last_error: Option<String>,
    pub security_failure: Option<String>,
    pub active_source: Option<String>,
    pub cache_error: Option<String>,
}
#[derive(Serialize)]
pub struct Readiness {
    pub ready: bool,
    pub reason_code: &'static str,
    pub reason: &'static str,
    pub availability_failure: bool,
}
impl AppState {
    pub fn new(config: Config) -> Result<Self, reqwest::Error> {
        let mut headers = reqwest::header::HeaderMap::new();
        // Config validated these values; builder has an explicit error path nonetheless.
        headers.insert("x-tenant-id", reqwest::header::HeaderValue::from_bytes(config.tenant_id.as_bytes()).expect("validated tenant UUID"));
        headers.insert("x-api-key", reqwest::header::HeaderValue::from_bytes(config.api_key.as_bytes()).expect("validated API key"));
        if let Some(workspace) = &config.workspace_id {
            headers.insert("x-workspace-id", reqwest::header::HeaderValue::from_bytes(workspace.as_bytes()).expect("validated workspace UUID"));
        }
        let client = reqwest::Client::builder().default_headers(headers)
            .redirect(reqwest::redirect::Policy::none())
            .pool_max_idle_per_host(256)
            .connect_timeout(config.connect_timeout).timeout(config.request_timeout).build()?;
        let attestation = attestation::load(&config);
        Ok(Self(Arc::new(Inner {
            config, client, active: RwLock::new(None), stats: Stats::default(),
            refresh: RwLock::new(RefreshStatus::default()), attestation, stopping: AtomicBool::new(false),
        })))
    }
    pub fn snapshot(&self) -> Option<Arc<ValidatedBundle>> { self.0.active.read().clone() }
    pub fn accept(&self, candidate: Bundle, source: &str) -> Result<Arc<ValidatedBundle>, Rejection> {
        let validated = Arc::new(bundle::validate(candidate, &self.0.config, chrono::Utc::now())?);
        let mut active = self.0.active.write();
        if let Some(current) = active.as_ref() {
            if validated.generated_at < current.generated_at {
                return Err(Rejection { code: "BUNDLE_ROLLBACK_REJECTED", message: "Candidate predates the current accepted bundle" });
            }
            if validated.generated_at == current.generated_at && validated.bundle.bundle_hash != current.bundle.bundle_hash {
                return Err(Rejection { code: "BUNDLE_TIMESTAMP_CONFLICT", message: "Different bundle content shares the current timestamp" });
            }
        }
        // Repeated delivery must not reset the accepted snapshot's monotonic age.
        let validated = match active.as_ref() {
            Some(current) if validated.generated_at == current.generated_at => current.clone(),
            _ => validated,
        };
        *active = Some(validated.clone());
        drop(active);
        let mut refresh = self.0.refresh.write();
        refresh.security_failure = None;
        refresh.active_source = Some(source.to_owned());
        if source == "control-plane" {
            refresh.successes += 1;
            refresh.last_success_at = Some(chrono::Utc::now().to_rfc3339());
            refresh.consecutive_failures = 0;
            refresh.last_error = None;
            refresh.last_error_code = None;
        }
        Ok(validated)
    }
    pub fn failure(&self, code: &str, message: &str, rejected: bool) {
        self.0.stats.errors.fetch_add(1, Ordering::Relaxed);
        let mut refresh = self.0.refresh.write();
        refresh.failures += 1;
        refresh.consecutive_failures += 1;
        refresh.last_error_code = Some(code.to_owned());
        refresh.last_error = Some(message.to_owned());
        if rejected {
            refresh.rejections += 1;
            // A network error must not erase an integrity failure and enable FAIL_OPEN.
            refresh.security_failure = Some(code.to_owned());
        }
    }
    pub fn readiness(&self, bundle: Option<&ValidatedBundle>) -> Readiness {
        let result = |ready, code, reason, availability| Readiness {
            ready, reason_code: code, reason, availability_failure: availability,
        };
        if self.0.stopping.load(Ordering::Acquire) {
            return result(false, "SHUTTING_DOWN", "Server is shutting down", false);
        }
        if !attestation::gate_satisfied(&self.0.attestation) {
            return result(false, "ATTESTATION_UNVERIFIED", "Required attestation has not been verified", false);
        }
        let security_failure = self.0.refresh.read().security_failure.is_some();
        let Some(bundle) = bundle else {
            return if security_failure {
                result(false, "NO_TRUSTED_BUNDLE", "No accepted bundle after an integrity rejection", false)
            } else {
                result(false, "BUNDLE_UNAVAILABLE", "No validated policy bundle is available", true)
            };
        };
        if bundle.expired(self.0.config.max_bundle_age) {
            return if security_failure {
                result(false, "STALE_AFTER_INTEGRITY_REJECTION", "Last trusted bundle expired after a rejected update", false)
            } else {
                result(false, "BUNDLE_STALE", "Last trusted policy bundle has expired", true)
            };
        }
        result(true, "READY", "A validated fresh bundle is available", false)
    }
}

