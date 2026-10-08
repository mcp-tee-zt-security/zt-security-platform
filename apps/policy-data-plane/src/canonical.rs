use crate::models::{Bundle, FastPolicy};
use sha2::{Digest, Sha256};

pub const CANONICAL_VERSION: u32 = 2;

// Length-prefix UTF-8 fields avoid ambiguous separators and preserve null vs empty.
// Java PolicyBundleController uses the same protocol and unsigned UTF-8 sorting.
fn field(out: &mut Vec<u8>, value: Option<&str>) {
    match value {
        None => out.extend_from_slice(b"-\n"),
        Some(value) => {
            out.extend_from_slice(value.len().to_string().as_bytes());
            out.push(b':');
            out.extend_from_slice(value.as_bytes());
            out.push(b'\n');
        }
    }
}
fn policies(out: &mut Vec<u8>, values: &[FastPolicy]) {
    let mut sorted: Vec<_> = values.iter().collect();
    sorted.sort_by(|a, b| a.priority.cmp(&b.priority).then(a.name.cmp(&b.name)).then(a.version.cmp(&b.version)));
    field(out, Some(&sorted.len().to_string()));
    for policy in sorted {
        field(out, Some(&policy.name));
        field(out, Some(&policy.version.to_string()));
        field(out, Some(&policy.priority.to_string()));
        field(out, Some(&policy.effect));
        field(out, policy.principal_type.as_deref());
        field(out, Some(&policy.actions.len().to_string()));
        for action in &policy.actions { field(out, Some(action)); }
        field(out, policy.resource_type.as_deref());
        field(out, policy.condition.as_deref());
        field(out, Some(if policy.fast_path { "true" } else { "false" }));
    }
}

pub fn payload(bundle: &Bundle) -> Vec<u8> {
    let mut out = b"zt-policy-bundle-v2\n".to_vec();
    field(&mut out, Some(&bundle.canonical_version.to_string()));
    field(&mut out, Some(&bundle.tenant_id));
    field(&mut out, bundle.workspace_id.as_deref());
    field(&mut out, Some(&bundle.version));
    field(&mut out, Some(&bundle.generated_at));
    field(&mut out, bundle.key_id.as_deref());
    policies(&mut out, &bundle.policies);
    out
}

pub fn revision(bundle: &Bundle) -> String {
    let mut out = b"zt-policy-set-v2\n".to_vec();
    field(&mut out, Some(&bundle.tenant_id));
    field(&mut out, bundle.workspace_id.as_deref());
    policies(&mut out, &bundle.policies);
    format!("sha256:{}", hash(&out))
}

pub fn hash(bytes: &[u8]) -> String { format!("{:x}", Sha256::digest(bytes)) }

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn null_empty_and_separator_fields_are_distinct() {
        let mut null = Vec::new(); field(&mut null, None);
        let mut empty = Vec::new(); field(&mut empty, Some(""));
        let mut text = Vec::new(); field(&mut text, Some("a|b\nc"));
        assert_ne!(null, empty);
        assert_eq!(empty, b"0:\n");
        assert_eq!(text, b"5:a|b\nc\n");
    }
}

