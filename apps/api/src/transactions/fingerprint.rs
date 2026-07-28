use sha2::{Digest, Sha256};

/// Stable slip identity for dedupe.
/// When `reference` (เลขที่รายการ) is present, fingerprint is reference-only
/// so OCR noise on amount/datetime does not create duplicates.
pub fn slip_fingerprint(amount: &str, spent_at: &str, bank: &str, reference: &str) -> String {
    let reference = reference.trim();
    let normalized = if !reference.is_empty() {
        format!("ref:{}", reference.to_ascii_uppercase())
    } else {
        format!(
            "{}|{}|{}|",
            amount.trim(),
            spent_at.trim(),
            bank.trim().to_lowercase()
        )
    };
    let mut hasher = Sha256::new();
    hasher.update(normalized.as_bytes());
    hex::encode(hasher.finalize())
}
