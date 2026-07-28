#!/usr/bin/env bash
# Publish a GetMoney APK for in-app updates.
# Usage: ./scripts/publish-android-release.sh getmoney-v1.49-tmd.deals.apk 50 1.49 ["notes"] [force]
set -euo pipefail

APK_LOCAL="${1:?apk path}"
VERSION_CODE="${2:?versionCode}"
VERSION_NAME="${3:?versionName}"
NOTES="${4:-}"
FORCE="${5:-false}"
HOST="${DEPLOY_HOST:-tmd.deals}"
REMOTE_DIR=/var/getmoney/releases
REMOTE_NAME="getmoney-${VERSION_NAME}.apk"

test -f "$APK_LOCAL"

scp "$APK_LOCAL" "root@${HOST}:${REMOTE_DIR}/${REMOTE_NAME}"
ssh "root@${HOST}" "chown getmoney:getmoney '${REMOTE_DIR}/${REMOTE_NAME}' && chmod 644 '${REMOTE_DIR}/${REMOTE_NAME}'"

ssh "root@${HOST}" "cat > '${REMOTE_DIR}/latest.json' <<EOF
{
  \"version_code\": ${VERSION_CODE},
  \"version_name\": \"${VERSION_NAME}\",
  \"force\": ${FORCE},
  \"apk_url\": \"https://tmd.deals/releases/${REMOTE_NAME}\",
  \"notes\": $(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' \"$NOTES\")
}
EOF
chown getmoney:getmoney '${REMOTE_DIR}/latest.json'"

echo "Published ${REMOTE_NAME} (versionCode=${VERSION_CODE})"
curl -fsS "https://tmd.deals/app/version" || true
