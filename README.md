---
title: CafeQR Test Backend
emoji: ☕
colorFrom: blue
colorTo: green
sdk: docker
app_port: 8080
pinned: false
---

# CafeQR 2.0 — Test Backend

This is the test/staging backend for the CafeQR 2.0 POS system, hosted on Hugging Face Spaces.

**Stack:** Spring Boot 3 + PostgreSQL (Neon) + Redis (Upstash)

All secrets (database credentials, JWT keys, API keys) are stored in Hugging Face Space Secrets and are never committed to this repository.
