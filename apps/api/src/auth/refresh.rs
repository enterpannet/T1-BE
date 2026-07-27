use rand::{RngCore, rngs::OsRng};
use sha2::{Digest, Sha256};

pub fn hash_refresh_token(raw: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(raw.as_bytes());
    hex::encode(hasher.finalize())
}

pub fn generate_refresh_token() -> (String, String) {
    let mut bytes = [0u8; 32];
    OsRng.fill_bytes(&mut bytes);
    let raw = hex::encode(bytes);
    let hash = hash_refresh_token(&raw);
    (raw, hash)
}
