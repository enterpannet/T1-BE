use getmoney_api::auth::password::{hash_password, verify_password};

#[test]
fn argon2_hash_and_verify() {
    let hash = hash_password("correct horse").unwrap();
    assert!(verify_password("correct horse", &hash).unwrap());
    assert!(!verify_password("wrong", &hash).unwrap());
}
