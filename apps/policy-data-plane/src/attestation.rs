use crate::{canonical::hash, config::{AttestationMode, Config}, models::AttestationState};

pub fn load(config: &Config) -> AttestationState {
    let document_hash = config.attestation_document.as_ref().map(|bytes| hash(bytes));
    let (status, reason) = match config.attestation_mode {
        AttestationMode::Disabled => ("DISABLED", "Attestation enforcement is explicitly disabled"),
        AttestationMode::DocumentHash if document_hash.is_some() =>
            ("INTEGRITY_ONLY", "Document hash available; signature, PCRs and provenance are not verified"),
        AttestationMode::DocumentHash =>
            ("UNVERIFIED", "No attestation document supplied"),
        AttestationMode::ExternalVerified | AttestationMode::Required =>
            ("UNVERIFIED", "No external attestation verifier is installed; configuration is not verification"),
    };
    AttestationState {
        provider: config.attestation_provider.clone(), mode: config.attestation_mode.label().into(),
        status, document_hash, expected_pcr3: config.expected_pcr3.clone(),
        expected_pcr8: config.expected_pcr8.clone(), nonce: config.nonce.clone(),
        key_id: config.attestation_key_id.clone(), verified_at: None, reason,
    }
}

pub fn gate_satisfied(state: &AttestationState) -> bool { state.mode == "DISABLED" }

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn external_setting_is_not_cryptographic_verification() {
        let config = Config::from_lookup(|name| Ok(
            (name == "ZT_ATTESTATION_MODE").then(|| "EXTERNAL_VERIFIED".into())
        )).unwrap();
        let state = load(&config);
        assert_eq!(state.status, "UNVERIFIED");
        assert!(state.verified_at.is_none());
        assert!(!gate_satisfied(&state));
    }
}

