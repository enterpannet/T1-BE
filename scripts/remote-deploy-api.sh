#!/usr/bin/env bash
set -euo pipefail

# Place sources
rm -rf /opt/getmoney-api/src /opt/getmoney-api/migration
mkdir -p /opt/getmoney-api
cp -a /tmp/getmoney-upload/Cargo.toml /tmp/getmoney-upload/Cargo.lock /opt/getmoney-api/
cp -a /tmp/getmoney-upload/src /tmp/getmoney-upload/migration /opt/getmoney-api/
rm -rf /tmp/getmoney-upload

# Install Rust if missing
if [ ! -x /root/.cargo/bin/cargo ]; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
fi
# shellcheck disable=SC1091
source /root/.cargo/env

cd /opt/getmoney-api
cargo build --release

# Dedicated system user
id getmoney >/dev/null 2>&1 || useradd --system --home /opt/getmoney-api --shell /usr/sbin/nologin getmoney
chown -R getmoney:getmoney /opt/getmoney-api
chmod 750 /opt/getmoney-api
# env readable by service user only
chown root:getmoney /etc/getmoney/api.env
chmod 640 /etc/getmoney/api.env

# Release APKs + manifest (before systemd so ReadOnlyPaths exists)
mkdir -p /var/getmoney/releases
chown -R getmoney:getmoney /var/getmoney/releases
chmod 755 /var/getmoney/releases
if [ ! -f /var/getmoney/releases/latest.json ]; then
  cat > /var/getmoney/releases/latest.json <<'JSON'
{
  "version_code": 49,
  "version_name": "1.48",
  "force": false,
  "apk_url": "https://tmd.deals/releases/getmoney-1.48.apk",
  "notes": ""
}
JSON
  chown getmoney:getmoney /var/getmoney/releases/latest.json
fi
if ! grep -q '^APP_RELEASE_MANIFEST=' /etc/getmoney/api.env 2>/dev/null; then
  echo 'APP_RELEASE_MANIFEST=/var/getmoney/releases/latest.json' >> /etc/getmoney/api.env
fi

cat > /etc/systemd/system/getmoney-api.service <<'EOF'
[Unit]
Description=GetMoney API
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=getmoney
Group=getmoney
WorkingDirectory=/opt/getmoney-api
EnvironmentFile=/etc/getmoney/api.env
ExecStart=/opt/getmoney-api/target/release/getmoney-api
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/getmoney-api
ReadOnlyPaths=/var/getmoney/releases
CapabilityBoundingSet=
AmbientCapabilities=
LockPersonality=true
MemoryDenyWriteExecute=true
RestrictRealtime=true
RestrictSUIDSGID=true
SystemCallArchitectures=native

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now getmoney-api
systemctl restart getmoney-api
sleep 2
systemctl --no-pager --full status getmoney-api || true
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/app/version || true

# Caddy: static APKs under /releases/* + API proxy
cat > /etc/caddy/Caddyfile <<'EOF'
tmd.deals {
	encode gzip
	header {
		Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
		X-Content-Type-Options "nosniff"
		X-Frame-Options "DENY"
		Referrer-Policy "no-referrer"
		-Server
	}
	handle_path /releases/* {
		root * /var/getmoney/releases
		file_server
	}
	handle {
		reverse_proxy 127.0.0.1:8080
	}
}
EOF

caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy

echo DEPLOY_OK
