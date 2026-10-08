use axum::http::HeaderMap;
use serde::Serialize;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentityReport {
    pub authenticated: bool,
    pub workload_identity: Option<String>,
    pub identity_source: &'static str,
    pub cryptographic_boundary: &'static str,
    pub reason: &'static str,
}

pub fn reported_identity(headers: &HeaderMap) -> Option<String> {
    let raw = headers.get("x-forwarded-client-cert")?.to_str().ok()?;
    if raw.len() > 4096 { return None; }
    // Multiple certificates/entries are ambiguous; never choose one implicitly.
    if raw.contains(',') { return None; }
    let mut identity = None;
    for part in raw.split(';') {
        if let Some(uri) = part.trim().strip_prefix("URI=") {
            let uri = uri.trim_matches('"');
            if identity.is_some() || uri.is_empty() || uri.len() > 512 || uri.chars().any(char::is_control) {
                return None;
            }
            identity = Some(uri.to_owned());
        }
    }
    identity
}

pub fn report(headers: &HeaderMap) -> IdentityReport {
    IdentityReport {
        authenticated: false, workload_identity: reported_identity(headers),
        identity_source: "unverified-forwarded-client-cert",
        cryptographic_boundary: "NOT_VERIFIED_BY_DATA_PLANE",
        reason: "A forwarded header is a claim; this process does not verify the TLS peer or certificate",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn spoofed_header_does_not_authenticate_a_workload() {
        let mut headers = HeaderMap::new();
        headers.insert("x-forwarded-client-cert", "URI=spiffe://example/agent".parse().unwrap());
        let value = report(&headers);
        assert_eq!(value.workload_identity.as_deref(), Some("spiffe://example/agent"));
        assert!(!value.authenticated);
    }
}

