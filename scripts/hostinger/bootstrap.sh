#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# CafeQR 2.0 — Hostinger VPS Bootstrap Script
# ──────────────────────────────────────────────────────────────
# Run this ONCE on a fresh Ubuntu 22.04/24.04 VPS.
#
# Usage (as root):
#   curl -sSL https://raw.githubusercontent.com/cafeqrllp/cafeqr-backend/main/scripts/hostinger/bootstrap.sh | bash
#   OR
#   scp bootstrap.sh root@YOUR_VPS_IP:/root/
#   ssh root@YOUR_VPS_IP 'bash /root/bootstrap.sh'
# ──────────────────────────────────────────────────────────────
set -euo pipefail

DEPLOY_USER="cafeqr"
DEPLOY_HOME="/home/${DEPLOY_USER}"
APP_DIR="${DEPLOY_HOME}/app"
SWAP_SIZE="4G"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║    CafeQR 2.0 — Production VPS Bootstrap                ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  This script will:                                       ║"
echo "║  1. Update the OS and install essentials                 ║"
echo "║  2. Create a deploy user (${DEPLOY_USER})               ║"
echo "║  3. Install Docker Engine + Compose v2                   ║"
echo "║  4. Create ${SWAP_SIZE} swap file                       ║"
echo "║  5. Configure UFW firewall (22, 80, 443 only)           ║"
echo "║  6. Harden SSH (key-only, no root password login)       ║"
echo "║  7. Install fail2ban + unattended-upgrades              ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ──────────────────────────────────────────────────────────────
# 1. System Update
# ──────────────────────────────────────────────────────────────
echo "── [1/7] Updating system packages ──"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
  curl wget git unzip htop ncdu jq \
  apt-transport-https ca-certificates gnupg lsb-release \
  software-properties-common

# ──────────────────────────────────────────────────────────────
# 2. Create Deploy User
# ──────────────────────────────────────────────────────────────
echo "── [2/7] Creating deploy user: ${DEPLOY_USER} ──"
if id "${DEPLOY_USER}" &>/dev/null; then
  echo "User ${DEPLOY_USER} already exists, skipping."
else
  adduser --disabled-password --gecos "CafeQR Deploy" "${DEPLOY_USER}"
  usermod -aG sudo "${DEPLOY_USER}"
  # Allow sudo without password for deploy user
  echo "${DEPLOY_USER} ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/${DEPLOY_USER}"
  chmod 0440 "/etc/sudoers.d/${DEPLOY_USER}"
fi

# Copy root's SSH keys to deploy user (if they exist)
if [ -f /root/.ssh/authorized_keys ]; then
  mkdir -p "${DEPLOY_HOME}/.ssh"
  cp /root/.ssh/authorized_keys "${DEPLOY_HOME}/.ssh/authorized_keys"
  chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_HOME}/.ssh"
  chmod 700 "${DEPLOY_HOME}/.ssh"
  chmod 600 "${DEPLOY_HOME}/.ssh/authorized_keys"
  echo "SSH keys copied to ${DEPLOY_USER}."
fi

# ──────────────────────────────────────────────────────────────
# 3. Install Docker Engine + Compose v2
# ──────────────────────────────────────────────────────────────
echo "── [3/7] Installing Docker Engine ──"
if command -v docker &>/dev/null; then
  echo "Docker already installed: $(docker --version)"
else
  # Add Docker's official GPG key
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg

  # Add Docker repository
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null

  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  # Allow deploy user to run Docker without sudo
  usermod -aG docker "${DEPLOY_USER}"

  # Enable Docker to start on boot
  systemctl enable docker
  systemctl start docker

  echo "Docker installed: $(docker --version)"
  echo "Compose installed: $(docker compose version)"
fi

# ──────────────────────────────────────────────────────────────
# 4. Create Swap File
# ──────────────────────────────────────────────────────────────
echo "── [4/7] Configuring ${SWAP_SIZE} swap ──"
if swapon --show | grep -q '/swapfile'; then
  echo "Swap already configured, skipping."
else
  fallocate -l "${SWAP_SIZE}" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile

  # Make permanent
  echo '/swapfile none swap sw 0 0' >> /etc/fstab

  # Tune swappiness — prefer keeping things in RAM
  sysctl vm.swappiness=10
  echo 'vm.swappiness=10' >> /etc/sysctl.conf

  # Reduce tendency to swap out inode/dentry caches
  sysctl vm.vfs_cache_pressure=50
  echo 'vm.vfs_cache_pressure=50' >> /etc/sysctl.conf

  echo "Swap configured:"
  swapon --show
fi

# ──────────────────────────────────────────────────────────────
# 5. Configure UFW Firewall
# ──────────────────────────────────────────────────────────────
echo "── [5/7] Configuring UFW firewall ──"
apt-get install -y -qq ufw

# Reset and set defaults
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# Allow only essential ports
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 443/udp comment 'HTTP/3 QUIC'

# Enable firewall
ufw --force enable
echo "UFW status:"
ufw status verbose

# ──────────────────────────────────────────────────────────────
# 6. Harden SSH
# ──────────────────────────────────────────────────────────────
echo "── [6/7] Hardening SSH ──"
SSHD_CONFIG="/etc/ssh/sshd_config"

# Backup original config
cp "${SSHD_CONFIG}" "${SSHD_CONFIG}.bak.$(date +%Y%m%d)"

# Apply hardening (only if not already done)
if ! grep -q "# CafeQR SSH Hardening" "${SSHD_CONFIG}"; then
  cat >> "${SSHD_CONFIG}" << 'EOF'

# CafeQR SSH Hardening
PermitRootLogin prohibit-password
PasswordAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
LoginGraceTime 30
ClientAliveInterval 300
ClientAliveCountMax 2
X11Forwarding no
AllowTcpForwarding no
EOF
  systemctl restart sshd
  echo "SSH hardened: root password login disabled, key-only auth enforced."
else
  echo "SSH already hardened, skipping."
fi

# ──────────────────────────────────────────────────────────────
# 7. Install Security Tools
# ──────────────────────────────────────────────────────────────
echo "── [7/7] Installing fail2ban + unattended-upgrades ──"

# fail2ban — blocks IPs after repeated failed SSH attempts
apt-get install -y -qq fail2ban
cat > /etc/fail2ban/jail.local << 'EOF'
[DEFAULT]
bantime  = 3600
findtime = 600
maxretry = 3
backend  = systemd

[sshd]
enabled = true
port    = ssh
filter  = sshd
logpath = /var/log/auth.log
maxretry = 3
bantime = 3600
EOF
systemctl enable fail2ban
systemctl restart fail2ban

# unattended-upgrades — auto-apply security patches
apt-get install -y -qq unattended-upgrades
dpkg-reconfigure -f noninteractive unattended-upgrades

# ──────────────────────────────────────────────────────────────
# Setup Application Directory
# ──────────────────────────────────────────────────────────────
echo "── Setting up application directory ──"
mkdir -p "${APP_DIR}"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${APP_DIR}"

# ──────────────────────────────────────────────────────────────
# Done!
# ──────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ✅  VPS Bootstrap Complete!                             ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║                                                          ║"
echo "║  Deploy user:  ${DEPLOY_USER}                           ║"
echo "║  App directory: ${APP_DIR}                              ║"
echo "║  Docker:        $(docker --version | cut -d' ' -f3)     ║"
echo "║  Swap:          ${SWAP_SIZE}                            ║"
echo "║  Firewall:      SSH(22) + HTTP(80) + HTTPS(443)         ║"
echo "║  SSH:           Key-only auth, root password disabled    ║"
echo "║  fail2ban:      Active (3 strikes → 1 hour ban)         ║"
echo "║  Auto-updates:  Security patches applied automatically  ║"
echo "║                                                          ║"
echo "║  NEXT STEPS:                                             ║"
echo "║  1. Log out and SSH back as: ssh ${DEPLOY_USER}@<IP>    ║"
echo "║  2. Clone your repo:                                     ║"
echo "║     cd ${APP_DIR}                                       ║"
echo "║     git clone <your-backend-repo> .                      ║"
echo "║  3. Create .env from .env.production.example             ║"
echo "║  4. Run: docker compose -f docker-compose.prod.yml up -d║"
echo "╚══════════════════════════════════════════════════════════╝"
