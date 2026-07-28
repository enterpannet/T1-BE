#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

DB_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
JWT_SECRET=$(openssl rand -hex 32)

sudo -u postgres psql -v ON_ERROR_STOP=1 -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'getmoney') THEN CREATE ROLE getmoney LOGIN PASSWORD '${DB_PASS}'; ELSE ALTER ROLE getmoney WITH PASSWORD '${DB_PASS}'; END IF; END \$\$;"
sudo -u postgres psql -v ON_ERROR_STOP=1 -tc "SELECT 1 FROM pg_database WHERE datname='getmoney'" | grep -q 1 \
  || sudo -u postgres psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE getmoney OWNER getmoney;"
sudo -u postgres psql -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE getmoney TO getmoney;"

PG_CONF=$(ls /etc/postgresql/*/main/postgresql.conf | head -1)
PG_HBA=$(ls /etc/postgresql/*/main/pg_hba.conf | head -1)
sed -i "s/^#\?listen_addresses.*/listen_addresses = 'localhost'/" "$PG_CONF"
grep -q "getmoney 127.0.0.1/32" "$PG_HBA" || echo "host all getmoney 127.0.0.1/32 scram-sha-256" >> "$PG_HBA"
systemctl restart postgresql

if ! command -v caddy >/dev/null 2>&1; then
  curl -1sLf "https://dl.cloudsmith.io/public/caddy/stable/gpg.key" | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf "https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt" | tee /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi
systemctl enable --now caddy

mkdir -p /etc/getmoney /opt/getmoney-api
cat > /etc/getmoney/api.env <<EOF
DATABASE_URL=postgres://getmoney:${DB_PASS}@127.0.0.1:5432/getmoney
JWT_SECRET=${JWT_SECRET}
ACCESS_TOKEN_TTL_SECS=900
REFRESH_TOKEN_TTL_SECS=2592000
BIND_ADDR=127.0.0.1:8080
EOF
chmod 600 /etc/getmoney/api.env
chown root:root /etc/getmoney/api.env

export PGPASSWORD="$DB_PASS"
psql -h 127.0.0.1 -U getmoney -d getmoney -c "SELECT current_user, current_database();"

echo SETUP_DB_CADDY_OK
caddy version
psql --version
