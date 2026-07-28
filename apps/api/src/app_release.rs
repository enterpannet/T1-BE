use crate::{
    error::{AppError, AppResult},
    state::AppState,
};
use axum::{extract::State, Json};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AppVersionInfo {
    pub version_code: i32,
    pub version_name: String,
    #[serde(default)]
    pub force: bool,
    pub apk_url: String,
    #[serde(default)]
    pub notes: String,
}

/// Read and validate the on-disk release manifest.
pub fn read_manifest(path: &Path) -> AppResult<AppVersionInfo> {
    let raw = std::fs::read_to_string(path).map_err(|e| {
        if e.kind() == std::io::ErrorKind::NotFound {
            AppError::NotFound
        } else {
            AppError::Other(anyhow::anyhow!("read release manifest: {e}"))
        }
    })?;
    let info: AppVersionInfo = serde_json::from_str(&raw)
        .map_err(|e| AppError::Other(anyhow::anyhow!("invalid release manifest: {e}")))?;
    if info.version_code < 1 {
        return Err(AppError::Other(anyhow::anyhow!(
            "invalid version_code in release manifest"
        )));
    }
    if info.version_name.trim().is_empty() || info.apk_url.trim().is_empty() {
        return Err(AppError::Other(anyhow::anyhow!(
            "version_name and apk_url are required"
        )));
    }
    Ok(info)
}

pub async fn get_version(State(state): State<AppState>) -> AppResult<Json<AppVersionInfo>> {
    let Some(path) = state.config.app_release_manifest.as_ref() else {
        return Err(AppError::NotFound);
    };
    Ok(Json(read_manifest(path)?))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn reads_valid_manifest() {
        let name = format!(
            "gm-manifest-{}.json",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        );
        let path = std::env::temp_dir().join(name);
        let mut f = std::fs::File::create(&path).unwrap();
        write!(
            f,
            r#"{{
              "version_code": 50,
              "version_name": "1.49",
              "force": true,
              "apk_url": "https://tmd.deals/releases/getmoney-1.49.apk",
              "notes": "hello"
            }}"#
        )
        .unwrap();
        let info = read_manifest(&path).unwrap();
        let _ = std::fs::remove_file(&path);
        assert_eq!(info.version_code, 50);
        assert_eq!(info.version_name, "1.49");
        assert!(info.force);
        assert!(info.apk_url.contains("getmoney-1.49.apk"));
        assert_eq!(info.notes, "hello");
    }

    #[test]
    fn missing_file_is_not_found() {
        let err =
            read_manifest(Path::new("/tmp/does-not-exist-getmoney-manifest.json")).unwrap_err();
        assert!(matches!(err, AppError::NotFound));
    }
}
