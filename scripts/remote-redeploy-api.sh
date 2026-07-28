#!/usr/bin/env bash
set -euo pipefail
source /root/.cargo/env
rm -rf /opt/getmoney-api/src /opt/getmoney-api/migration
cp -a /tmp/getmoney-upload/Cargo.toml /tmp/getmoney-upload/Cargo.lock /opt/getmoney-api/
cp -a /tmp/getmoney-upload/src /tmp/getmoney-upload/migration /opt/getmoney-api/
rm -rf /tmp/getmoney-upload
cd /opt/getmoney-api
cargo build --release
chown -R getmoney:getmoney /opt/getmoney-api
systemctl restart getmoney-api
sleep 1
curl -fsS http://127.0.0.1:8080/health
echo
echo REDEPLOY_OK
