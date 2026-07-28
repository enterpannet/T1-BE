use std::path::PathBuf;

#[derive(Clone)]
pub struct Config {
    pub database_url: String,
    pub jwt_secret: String,
    pub access_token_ttl_secs: i64,
    pub refresh_token_ttl_secs: i64,
    pub bind_addr: String,
    /// Path to `latest.json` for in-app updates. Unset → `/app/version` returns 404.
    pub app_release_manifest: Option<PathBuf>,
}

impl Config {
    pub fn from_env() -> Self {
        dotenvy::dotenv().ok();
        Self {
            database_url: std::env::var("DATABASE_URL").expect("DATABASE_URL"),
            jwt_secret: std::env::var("JWT_SECRET").expect("JWT_SECRET"),
            access_token_ttl_secs: std::env::var("ACCESS_TOKEN_TTL_SECS")
                .unwrap_or_else(|_| "900".into())
                .parse()
                .expect("ACCESS_TOKEN_TTL_SECS"),
            refresh_token_ttl_secs: std::env::var("REFRESH_TOKEN_TTL_SECS")
                .unwrap_or_else(|_| "2592000".into())
                .parse()
                .expect("REFRESH_TOKEN_TTL_SECS"),
            bind_addr: std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".into()),
            app_release_manifest: std::env::var("APP_RELEASE_MANIFEST")
                .ok()
                .filter(|s| !s.trim().is_empty())
                .map(PathBuf::from),
        }
    }
}
