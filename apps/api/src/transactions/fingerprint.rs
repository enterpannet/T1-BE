use sha2::{Digest, Sha256};

pub fn slip_fingerprint(amount: &str, spent_at: &str, bank: &str, reference: &str) -> String {
    let normalized = format!(
        "{}|{}|{}|{}",
        amount.trim(),
        spent_at.trim(),
        bank.trim().to_lowercase(),
        reference.trim()
    );
    let mut hasher = Sha256::new();
    hasher.update(normalized.as_bytes());
    hex::encode(hasher.finalize())
}
