use axum::http::HeaderValue;
use base64::{engine::general_purpose::STANDARD, Engine};
use ed25519_dalek::{SigningKey, VerifyingKey};
use std::{env, fmt, net::SocketAddr, path::PathBuf, time::Duration};

#[derive(Debug)]
pub struct ConfigError(pub String);

impl fmt::Display for ConfigError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { f.write_str(&self.0) }
}
impl std::error::Error for ConfigError {}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FailMode { Closed, Open }
impl FailMode {
    pub fn label(self) -> &'static str {
        match self { Self::Closed => "FAIL_CLOSED", Self::Open => "FAIL_OPEN" }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AttestationMode { Disabled, DocumentHash, ExternalVerified, Required }
impl AttestationMode {
    pub fn label(self) -> &'static str {
        match self {
            Self::Disabled => "DISABLED", Self::DocumentHash => "DOCUMENT_HASH",
            Self::ExternalVerified => "EXTERNAL_VERIFIED", Self::Required => "REQUIRED",
        }
    }
}

// Deliberately not Debug/Serialize: configuration includes credentials and private keys.
#[derive(Clone)]
pub struct Config {
    pub bind_addr: SocketAddr,
    pub control_plane: String,
    pub tenant_id: String,
    pub workspace_id: Option<String>,
    pub api_key: String,
    pub refresh_interval: Duration,
    pub connect_timeout: Duration,
    pub request_timeout: Duration,
    pub max_bundle_age: Duration,
    pub max_future_skew: Duration,
    pub fail_mode: FailMode,
    pub verify_key: Option<VerifyingKey>,
    pub expected_key_id: Option<String>,
    pub allow_unsigned: bool,
    pub bundle_pin: Option<String>,
    pub cache_path: Option<PathBuf>,
    pub max_bundle_bytes: usize,
    pub max_request_bytes: usize,
    pub evidence_key: Option<SigningKey>,
    pub evidence_key_id: String,
    pub attestation_mode: AttestationMode,
    pub attestation_provider: String,
    pub attestation_document: Option<Vec<u8>>,
    pub expected_pcr3: Option<String>,
    pub expected_pcr8: Option<String>,
    pub nonce: Option<String>,
    pub attestation_key_id: Option<String>,
    pub incoming_api_key: Option<String>,
    pub require_api_key: bool,
    pub cors_origins: Option<Vec<HeaderValue>>,
}

impl Config {
    pub fn from_env() -> Result<Self, ConfigError> {
        Self::from_lookup(|name| match env::var(name) {
            Ok(value) => Ok(Some(value)),
            Err(env::VarError::NotPresent) => Ok(None),
            Err(_) => Err(ConfigError(format!("{name} must contain Unicode text"))),
        })
    }

    pub fn from_lookup<F>(lookup: F) -> Result<Self, ConfigError>
    where F: Fn(&str) -> Result<Option<String>, ConfigError> {
        let text = |name: &str, default: &str| -> Result<String, ConfigError> {
            let value = lookup(name)?.unwrap_or_else(|| default.to_owned());
            if value.trim().is_empty() || value.contains('\r') || value.contains('\n') {
                return Err(ConfigError(format!("{name} must be nonempty and have no line breaks")));
            }
            Ok(value.trim().to_owned())
        };
        let optional = |name: &str| -> Result<Option<String>, ConfigError> {
            Ok(lookup(name)?.map(|v| v.trim().to_owned()).filter(|v| !v.is_empty()))
        };
        let number = |name: &str, default: u64, min: u64, max: u64| -> Result<u64, ConfigError> {
            let value = text(name, &default.to_string())?.parse::<u64>()
                .map_err(|_| ConfigError(format!("{name} must be an integer")))?;
            if !(min..=max).contains(&value) {
                return Err(ConfigError(format!("{name} must be between {min} and {max}")));
            }
            Ok(value)
        };
        let boolean = |name: &str, default: bool| -> Result<bool, ConfigError> {
            match text(name, if default { "true" } else { "false" })?.to_ascii_lowercase().as_str() {
                "true" => Ok(true), "false" => Ok(false),
                _ => Err(ConfigError(format!("{name} must be true or false"))),
            }
        };
        let tenant_id = uuid_text(&text("ZT_TENANT_ID", "11111111-1111-1111-1111-111111111111")?, "ZT_TENANT_ID")?;
        let workspace_id = optional("ZT_WORKSPACE_ID")?.map(|v| uuid_text(&v, "ZT_WORKSPACE_ID")).transpose()?;
        let control_plane = text("ZT_CONTROL_PLANE_URL", "http://localhost:8080")?;
        let url = reqwest::Url::parse(&control_plane)
            .map_err(|_| ConfigError("ZT_CONTROL_PLANE_URL must be an HTTP(S) URL".into()))?;
        if !matches!(url.scheme(), "http" | "https") || url.host_str().is_none()
            || !url.username().is_empty() || url.password().is_some()
            || url.query().is_some() || url.fragment().is_some() {
            return Err(ConfigError("ZT_CONTROL_PLANE_URL must not include credentials/query/fragment".into()));
        }
        let verify_key = optional("ZT_POLICY_PUBLIC_KEY_B64")?.map(|value| {
            let bytes = key_bytes(&value, "ZT_POLICY_PUBLIC_KEY_B64")?;
            let key = VerifyingKey::from_bytes(&bytes)
                .map_err(|_| ConfigError("ZT_POLICY_PUBLIC_KEY_B64 is not a valid Ed25519 key".into()))?;
            if key.is_weak() {
                return Err(ConfigError("ZT_POLICY_PUBLIC_KEY_B64 is a weak Ed25519 key".into()));
            }
            Ok(key)
        }).transpose()?;
        let require_signed = boolean("ZT_REQUIRE_SIGNED_BUNDLE", false)?;
        let allow_unsigned = boolean("ZT_ALLOW_UNSIGNED_BUNDLES", verify_key.is_none() && !require_signed)?;
        if require_signed && verify_key.is_none() {
            return Err(ConfigError("ZT_REQUIRE_SIGNED_BUNDLE requires ZT_POLICY_PUBLIC_KEY_B64".into()));
        }
        if verify_key.is_some() && allow_unsigned {
            return Err(ConfigError("A verification key cannot be combined with unsigned-bundle acceptance".into()));
        }
        if verify_key.is_none() && !allow_unsigned {
            return Err(ConfigError("Configure a verification key or explicitly allow unsigned development bundles".into()));
        }
        let evidence_key = optional("ZT_EVIDENCE_SIGNING_PRIVATE_KEY_B64")?
            .map(|value| key_bytes(&value, "ZT_EVIDENCE_SIGNING_PRIVATE_KEY_B64").map(|b| SigningKey::from_bytes(&b)))
            .transpose()?;
        let attestation_mode = match text("ZT_ATTESTATION_MODE", "DISABLED")?.to_ascii_uppercase().as_str() {
            "DISABLED" => AttestationMode::Disabled, "DOCUMENT_HASH" => AttestationMode::DocumentHash,
            "EXTERNAL_VERIFIED" => AttestationMode::ExternalVerified, "REQUIRED" => AttestationMode::Required,
            _ => return Err(ConfigError("Unknown ZT_ATTESTATION_MODE".into())),
        };
        let attestation_document = optional("ZT_NITRO_ATTESTATION_DOCUMENT_B64")?.map(|value| {
            let bytes = STANDARD.decode(value)
                .map_err(|_| ConfigError("Invalid ZT_NITRO_ATTESTATION_DOCUMENT_B64".into()))?;
            if bytes.is_empty() || bytes.len() > 1_048_576 {
                return Err(ConfigError("Attestation document must be 1..1048576 bytes".into()));
            }
            Ok(bytes)
        }).transpose()?;
        let fail_mode = match text("ZT_FAIL_MODE", "FAIL_CLOSED")?.to_ascii_uppercase().as_str() {
            "FAIL_CLOSED" => FailMode::Closed, "FAIL_OPEN" => FailMode::Open,
            _ => return Err(ConfigError("ZT_FAIL_MODE must be FAIL_CLOSED or FAIL_OPEN".into())),
        };
        let cache_path = optional("ZT_BUNDLE_CACHE_PATH")?.map(PathBuf::from);
        if cache_path.as_ref().is_some_and(|path| path.file_name().is_none()) {
            return Err(ConfigError("ZT_BUNDLE_CACHE_PATH must name a file".into()));
        }
        let incoming_api_key = optional("ZT_DP_API_KEY")?;
        if let Some(key) = &incoming_api_key {
            HeaderValue::from_bytes(key.as_bytes())
                .map_err(|_| ConfigError("ZT_DP_API_KEY contains invalid header bytes".into()))?;
        }
        let require_api_key = boolean("ZT_DP_REQUIRE_API_KEY", incoming_api_key.is_some())?;
        if require_api_key && incoming_api_key.is_none() {
            return Err(ConfigError("ZT_DP_REQUIRE_API_KEY requires ZT_DP_API_KEY".into()));
        }
        let cors_origins = match optional("ZT_CORS_ALLOWED_ORIGINS")? {
            None => None,
            Some(value) if value == "*" => None,
            Some(value) => {
                let mut origins = Vec::new();
                for item in value.split(',') {
                    let origin = reqwest::Url::parse(item.trim())
                        .map_err(|_| ConfigError("Invalid ZT_CORS_ALLOWED_ORIGINS".into()))?;
                    if !matches!(origin.scheme(), "http" | "https") || origin.host_str().is_none()
                        || !origin.username().is_empty() || origin.password().is_some()
                        || origin.query().is_some() || origin.fragment().is_some()
                        || origin.path() != "/" {
                        return Err(ConfigError("CORS entries must be HTTP(S) origins without paths".into()));
                    }
                    origins.push(HeaderValue::from_bytes(origin.origin().ascii_serialization().as_bytes())
                        .map_err(|_| ConfigError("Invalid CORS origin header".into()))?);
                }
                Some(origins)
            }
        };
        let api_key = text("ZT_API_KEY", "dev-master-key")?;
        HeaderValue::from_bytes(api_key.as_bytes())
            .map_err(|_| ConfigError("ZT_API_KEY contains invalid header bytes".into()))?;
        Ok(Self {
            bind_addr: text("ZT_BIND_ADDR", "0.0.0.0:8091")?.parse()
                .map_err(|_| ConfigError("ZT_BIND_ADDR must be an IP address and port".into()))?,
            control_plane, tenant_id, workspace_id, api_key,
            refresh_interval: Duration::from_secs(number("ZT_POLICY_REFRESH_SECONDS", 5, 1, 3600)?),
            connect_timeout: Duration::from_secs(number("ZT_CONTROL_PLANE_CONNECT_TIMEOUT_SECONDS", 1, 1, 120)?),
            request_timeout: Duration::from_secs(number("ZT_CONTROL_PLANE_TIMEOUT_SECONDS", 3, 1, 300)?),
            max_bundle_age: Duration::from_secs(number("ZT_BUNDLE_MAX_AGE_SECONDS", 60, 1, 86400)?),
            max_future_skew: Duration::from_secs(number("ZT_BUNDLE_MAX_FUTURE_SKEW_SECONDS", 5, 0, 300)?),
            fail_mode, verify_key, allow_unsigned,
            expected_key_id: optional("ZT_POLICY_PUBLIC_KEY_ID")?,
            bundle_pin: optional("ZT_POLICY_BUNDLE_PIN")?, cache_path,
            max_bundle_bytes: number("ZT_MAX_BUNDLE_BYTES", 4_194_304, 1024, 67_108_864)? as usize,
            max_request_bytes: number("ZT_MAX_REQUEST_BYTES", 65_536, 1024, 1_048_576)? as usize,
            evidence_key, evidence_key_id: text("ZT_EVIDENCE_SIGNING_KEY_ID", "zt-dp-evidence-ed25519")?,
            attestation_mode, attestation_document,
            attestation_provider: text("ZT_ATTESTATION_PROVIDER", "NONE")?,
            expected_pcr3: optional("ZT_NITRO_EXPECTED_PCR3")?, expected_pcr8: optional("ZT_NITRO_EXPECTED_PCR8")?,
            nonce: optional("ZT_ATTESTATION_NONCE")?, attestation_key_id: optional("ZT_ATTESTATION_KMS_KEY_ID")?,
            incoming_api_key, require_api_key, cors_origins,
        })
    }
}

fn key_bytes(value: &str, name: &str) -> Result<[u8; 32], ConfigError> {
    STANDARD.decode(value)
        .map_err(|_| ConfigError(format!("{name} must be valid Base64")))?
        .try_into().map_err(|_| ConfigError(format!("{name} must decode to exactly 32 bytes")))
}

fn uuid_text(value: &str, name: &str) -> Result<String, ConfigError> {
    let bytes = value.as_bytes();
    if bytes.len() != 36 || bytes.iter().enumerate().any(|(i, b)| {
        if matches!(i, 8 | 13 | 18 | 23) { *b != b'-' } else { !b.is_ascii_hexdigit() }
    }) {
        return Err(ConfigError(format!("{name} must be a canonical UUID")));
    }
    Ok(value.to_ascii_lowercase())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    fn config(values: &[(&str, &str)]) -> Result<Config, ConfigError> {
        let env: HashMap<_, _> = values.iter().copied().collect();
        Config::from_lookup(|name| Ok(env.get(name).map(|v| v.to_string())))
    }
    #[test]
    fn malformed_key_cannot_silently_disable_verification() {
        assert!(config(&[("ZT_POLICY_PUBLIC_KEY_B64", "not base64")]).is_err());
    }
    #[test]
    fn signed_mode_without_key_is_rejected() {
        assert!(config(&[("ZT_REQUIRE_SIGNED_BUNDLE", "true")]).is_err());
    }
    #[test]
    fn invalid_duration_and_fail_mode_are_rejected() {
        assert!(config(&[("ZT_POLICY_REFRESH_SECONDS", "0")]).is_err());
        assert!(config(&[("ZT_FAIL_MODE", "typo")]).is_err());
    }
    #[test]
    fn incoming_auth_requires_a_key() {
        assert!(config(&[("ZT_DP_REQUIRE_API_KEY", "true")]).is_err());
    }
}

