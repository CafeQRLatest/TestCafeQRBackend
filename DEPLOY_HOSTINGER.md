# CafeQR 2.0 — Hostinger VPS Deployment Guide

Complete guide to deploy the production CafeQR 2.0 application on a Hostinger KVM VPS.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Purchase & Setup VPS](#1-purchase--setup-vps)
3. [Bootstrap the Server](#2-bootstrap-the-server)
4. [Configure Domain & DNS](#3-configure-domain--dns)
5. [Deploy the Application](#4-deploy-the-application)
6. [Migrate Data from Supabase](#5-migrate-data-from-supabase)
7. [Setup CI/CD (Auto-Deploy)](#6-setup-cicd-auto-deploy)
8. [Setup Automated Backups](#7-setup-automated-backups)
9. [Operations Guide](#8-operations-guide)
10. [Security Checklist](#9-security-checklist)
11. [Troubleshooting](#10-troubleshooting)

---

## Prerequisites

- A Hostinger KVM VPS (KVM 1 or higher)
- A domain name (e.g., `cafeqr.in`)
- An SSH key pair (if you don't have one, we'll create it below)
- Your production secrets (JWT keys, Razorpay keys, Gmail OAuth tokens)

---

## 1. Purchase & Setup VPS

1. Go to [Hostinger VPS](https://www.hostinger.in/vps-hosting) and purchase **KVM 1** (or KVM 2 for more headroom).
2. During setup, choose:
   - **OS:** Ubuntu 22.04 LTS (or 24.04 LTS)
   - **Data Center:** India (Mumbai) — for lowest latency
   - **Hostname:** `cafeqr-prod`
3. After purchase, note down:
   - **IP Address:** (e.g., `103.xxx.xxx.xxx`)
   - **Root Password:** (from Hostinger dashboard)

### Generate SSH Key (if needed)

On your local Windows machine (PowerShell):
```powershell
ssh-keygen -t ed25519 -C "cafeqr-deploy" -f "$HOME\.ssh\cafeqr_vps"
```

Copy the public key to the VPS:
```powershell
# Display your public key
Get-Content "$HOME\.ssh\cafeqr_vps.pub"

# SSH into VPS and paste the key
ssh root@YOUR_VPS_IP
mkdir -p ~/.ssh
echo "PASTE_YOUR_PUBLIC_KEY_HERE" >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
exit
```

---

## 2. Bootstrap the Server

SSH into your VPS and run the bootstrap script:

```bash
ssh root@YOUR_VPS_IP

# Download and run the bootstrap script
curl -sSL https://raw.githubusercontent.com/cafeqrllp/cafeqr-backend/main/scripts/hostinger/bootstrap.sh | bash
```

**Or manually copy and run:**
```bash
# From your local machine
scp "C:\RIYAS\Sharp INtell\Cafe QR 2.0\cafeqr-backend\scripts\hostinger\bootstrap.sh" root@YOUR_VPS_IP:/root/
ssh root@YOUR_VPS_IP "bash /root/bootstrap.sh"
```

This script automatically:
- ✅ Updates all system packages
- ✅ Creates a `cafeqr` deploy user (no root needed after this)
- ✅ Installs Docker Engine + Docker Compose v2
- ✅ Creates 4 GB swap file (prevents OOM crashes)
- ✅ Configures UFW firewall (only ports 22, 80, 443 open)
- ✅ Hardens SSH (key-only auth, root password login disabled)
- ✅ Installs fail2ban (blocks brute-force SSH attacks)
- ✅ Enables unattended-upgrades (auto security patches)

**After bootstrap, log out and reconnect as the deploy user:**
```bash
exit
ssh cafeqr@YOUR_VPS_IP
```

---

## 3. Configure Domain & DNS

### Option A: Using Your Domain

1. Go to your domain registrar (GoDaddy, Namecheap, Hostinger Domains, etc.)
2. Add an **A Record**:
   - **Name:** `app`
   - **Value:** `69.62.83.147`
   - **TTL:** 300 (5 minutes)
3. Wait for DNS propagation (usually 5–30 minutes)
4. Verify: `ping YOUR_DOMAIN`

### Option B: Using VPS IP Only (temporary)

If you don't have a domain yet, you can still deploy. Replace `YOUR_DOMAIN` in the Caddyfile with:
```
:80 {
    # Same config but without HTTPS
}
```

---

## 4. Deploy the Application

### Step 1: Clone the Repository

```bash
ssh cafeqr@YOUR_VPS_IP

mkdir -p ~/app && cd ~/app

# Clone the backend repo (contains docker-compose.prod.yml)
git clone https://github.com/cafeqrllp/cafeqr-backend.git .
```

### Step 2: Configure Environment Variables

```bash
# Copy the template
cp .env.production.example .env
cp .env.frontend.example .env.frontend

# Edit with your production secrets
nano .env
nano .env.frontend
```

Fill in all values:
- `DB_PASSWORD` — generate a strong password: `openssl rand -base64 24`
- `RABBITMQ_PASSWORD` — generate: `openssl rand -base64 24`
- `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` — your RSA keys
- `CADDY_SITE_ADDRESS` — `app.cafeqr.in` after the DNS record resolves
- `CADDY_REDIRECT_SITE_ADDRESS` — `http://69.62.83.147`
- `CADDY_CANONICAL_ORIGIN` — `https://app.cafeqr.in`
- `ALLOWED_ORIGINS` — `https://app.cafeqr.in`
- `FRONTEND_URL` — `https://app.cafeqr.in`
- `.env.frontend` `NEXT_PUBLIC_API_URL` — `https://app.cafeqr.in`
- `.env.frontend` `NEXT_PUBLIC_AI_PARSE_URL` — `https://app.cafeqr.in/api/ai/parse-menu`
- Gmail, Razorpay credentials

If any generated secret contains `$`, wrap the whole value in single quotes, for example:
`DB_PASSWORD='abc$def'`.

### Step 3: Configure Caddy

Before changing the VPS environment, confirm that `app.cafeqr.in` resolves to
`69.62.83.147` and that ports 80 and 443 are open. Then apply the canonical
values from the example files, rebuild the frontend image because
`NEXT_PUBLIC_*` values are compile-time settings, and redeploy. Caddy will
provision TLS automatically and redirect requests for the old IP URL to
`https://app.cafeqr.in`.

### Step 4: Start Everything

```bash
chmod +x scripts/hostinger/*.sh
./scripts/hostinger/deploy.sh all
```

### Step 5: Verify

```bash
# Check all containers are running
docker compose -f docker-compose.prod.yml ps

# Check health
./scripts/hostinger/status.sh

# View logs
./scripts/hostinger/logs.sh backend 50
```

Visit `http://YOUR_VPS_IP` in your browser. After the domain is configured, use `https://YOUR_DOMAIN`.

---

## 5. Migrate Data from Supabase

If you have existing production data on Supabase that needs to be moved to the VPS:

### Step 1: Export from Supabase

```bash
# From your local machine or any machine with psql
pg_dump \
  --host=aws-0-region.pooler.supabase.com \
  --port=5432 \
  --username=postgres.project-ref \
  --dbname=postgres \
  --no-owner --no-privileges --clean --if-exists \
  | gzip > supabase_export.sql.gz
```

### Step 2: Upload to VPS

```bash
scp supabase_export.sql.gz cafeqr@YOUR_VPS_IP:~/
```

### Step 3: Import into VPS PostgreSQL

```bash
ssh cafeqr@YOUR_VPS_IP
gunzip -c ~/supabase_export.sql.gz | docker exec -i cafeqr-db \
  psql -U cafeqr_prod -d pos_db --quiet --single-transaction
```

### Step 4: Restart Backend (clear caches)

```bash
cd ~/app
docker compose -f docker-compose.prod.yml restart backend
```

---

## 6. Setup CI/CD (Auto-Deploy)

This enables **push to GitHub → auto-deploy to VPS** in ~3 minutes.

### Step 1: Add GitHub Secrets

Go to your GitHub repository → Settings → Secrets → Actions, and add:

| Secret Name | Value |
|------------|-------|
| `VPS_HOST` | Your VPS IP address |
| `VPS_USER` | `cafeqr` |
| `VPS_SSH_KEY` | Full private key contents, including `-----BEGIN ... PRIVATE KEY-----` and `-----END ... PRIVATE KEY-----` |
| `NEXT_PUBLIC_API_URL` | `http://YOUR_VPS_IP` for IP-first deployment |
| `NEXT_PUBLIC_AI_PARSE_URL` | `http://YOUR_VPS_IP/api/ai/parse-menu` for IP-first deployment |

Add the same `VPS_HOST`, `VPS_USER`, and `VPS_SSH_KEY` secrets to both the backend and frontend repositories.

### Step 2: Login to GHCR on VPS

```bash
ssh cafeqr@YOUR_VPS_IP

# Create a GitHub Personal Access Token with "read:packages" scope
# Then login on the VPS:
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

### Step 3: Test

Push a commit to `main` and watch the Actions tab on GitHub. The workflow will build, push, and deploy automatically.

---

## 7. Setup Automated Backups

```bash
ssh cafeqr@YOUR_VPS_IP
cd ~/app

# Make scripts executable
chmod +x scripts/backups/*.sh

# Install the daily cron job (runs at 3:00 AM IST)
./scripts/backups/setup-backup-cron.sh

# Verify cron is installed
crontab -l

# Run a manual backup to test
./scripts/backups/pg-backup.sh
```

Backups are stored at `/home/cafeqr/backups/postgres/` with 7-day retention.

---

## 8. Operations Guide

### Common Commands

```bash
# SSH into VPS
ssh cafeqr@YOUR_VPS_IP
cd ~/app

# View status dashboard
./scripts/hostinger/status.sh

# View logs
./scripts/hostinger/logs.sh               # All services
./scripts/hostinger/logs.sh backend 100    # Backend, last 100 lines
./scripts/hostinger/logs.sh db             # Database

# Manual deploy
./scripts/hostinger/deploy.sh              # All
./scripts/hostinger/deploy.sh backend      # Backend only

# Restart a service
docker compose -f docker-compose.prod.yml restart backend

# Stop everything
docker compose -f docker-compose.prod.yml down

# Start everything
docker compose -f docker-compose.prod.yml up -d

# Database backup (manual)
./scripts/backups/pg-backup.sh

# Database restore
./scripts/backups/pg-restore.sh /home/cafeqr/backups/postgres/BACKUP_FILE.sql.gz
```

### View Production Database with DBeaver

PostgreSQL is not publicly exposed. The Compose file binds it only to the VPS
loopback interface on port `15432`, so use an SSH tunnel from Windows:

```powershell
ssh -i "$HOME\.ssh\id_ed25519" -N -L 15432:127.0.0.1:15432 cafeqr@YOUR_VPS_IP
```

Then create a PostgreSQL connection in DBeaver:

| Setting | Value |
|---------|-------|
| Host | `localhost` |
| Port | `15432` |
| Database | `DB_NAME` from `/home/cafeqr/app/.env` |
| Username | `DB_USER` from `/home/cafeqr/app/.env` |
| Password | `DB_PASSWORD` from `/home/cafeqr/app/.env` |
| SSL | Disabled |

Use DBeaver mainly for viewing and verification. Before any manual production
edit, take a database backup and record the exact SQL/change made.

### Monitoring

```bash
# Live resource usage
docker stats

# Disk usage
df -h
ncdu /               # Interactive disk usage browser

# Memory overview
free -h

# System load
htop
```

---

## 9. Security Checklist

### ✅ Included in Bootstrap Script (Automatic)
| Layer | Protection | Details |
|-------|-----------|---------|
| **Network** | UFW Firewall | Only ports 22, 80, 443 open |
| **Network** | Hostinger DDoS | Basic network-level DDoS protection |
| **SSH** | Key-only auth | Password login disabled |
| **SSH** | fail2ban | 3 failed attempts → 1 hour IP ban |
| **OS** | Auto-updates | Security patches applied automatically |
| **OS** | Non-root user | Application runs as `cafeqr` user |

### ✅ Included in Docker Compose (Automatic)
| Layer | Protection | Details |
|-------|-----------|---------|
| **Transport** | Auto HTTPS | Caddy + Let's Encrypt (free SSL, auto-renewed) |
| **Transport** | HTTP/3 QUIC | Modern, faster, encrypted protocol |
| **Headers** | Security headers | X-Frame-Options, CSP, HSTS, etc. |
| **Network** | Internal network | DB/Redis/RabbitMQ have NO public ports |
| **Container** | Non-root user | Frontend runs as UID 1001 |
| **Container** | Memory limits | Each service has RAM caps (prevents OOM) |
| **Container** | Health checks | Auto-restart unhealthy containers |

### ✅ Included in Application Code (Already Built)
| Layer | Protection | Details |
|-------|-----------|---------|
| **Auth** | JWT + RSA | Stateless auth with RSA-signed tokens |
| **Auth** | OTP verification | Phone/email OTP for login |
| **API** | Spring Security | All endpoints are authenticated |
| **API** | CORS whitelist | Only your domain is allowed |
| **Data** | Tenant isolation | Each restaurant only sees their own data |
| **Data** | Encrypted backups | SHA-256 checksums on backup files |
| **Payments** | Razorpay webhooks | Signature verification on all webhooks |

### 📋 Manual Steps (Do After Setup)
| Action | How |
|--------|-----|
| Enable HSTS | Uncomment the HSTS line in Caddyfile after confirming SSL works |
| Rotate JWT keys | Generate new RSA key pair for production (don't reuse test keys) |
| Rotate DB password | Generate: `openssl rand -base64 24` |
| Enable Hostinger backups | Hostinger dashboard → VPS → Backups → Enable weekly snapshots |
| Test restore | Run `pg-restore.sh` on a test backup to verify |

---

## 10. Troubleshooting

### Backend won't start
```bash
# Check logs
docker logs cafeqr-backend --tail=100

# Common: database not ready yet
docker compose -f docker-compose.prod.yml restart backend
```

### Out of Memory
```bash
# Check memory
free -h
docker stats --no-stream

# If swap is full, consider upgrading to KVM 2
# Or reduce memory limits in docker-compose.prod.yml
```

### SSL certificate not working
```bash
# Verify domain points to VPS
dig YOUR_DOMAIN

# Check Caddy logs
docker logs cafeqr-caddy --tail=50

# Common: DNS not propagated yet — wait 30 minutes
```

### Can't SSH into VPS
```bash
# If locked out, use Hostinger hPanel → VPS → Console
# Then fix /etc/ssh/sshd_config
```

If GitHub Actions shows `ssh.ParsePrivateKey: ssh: no key found`, replace the
`VPS_SSH_KEY` secret with the full private key, not the `.pub` key and not a
password.

### Docker Compose says a variable is not set

If Compose prints a random-looking missing variable from a password, the secret
probably contains `$`. Quote that value in `.env`:

```bash
DB_PASSWORD='abc$def'
```

Then validate:

```bash
docker compose -f docker-compose.prod.yml config -q
```

### Git pull blocked by local changes

Do not edit tracked files directly on the VPS. Move server-only values into
`.env` or `.env.frontend`, then make `/home/cafeqr/app` clean before deploying:

```bash
cd /home/cafeqr/app
git status --short
```

### Database connection refused
```bash
# Verify database container is running
docker ps | grep cafeqr-db

# Check database logs
docker logs cafeqr-db --tail=50

# Verify credentials match
docker exec -it cafeqr-db psql -U cafeqr_prod -d pos_db -c "SELECT 1"
```
