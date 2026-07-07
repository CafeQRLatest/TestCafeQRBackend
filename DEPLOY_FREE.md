# CafeQR 2.0 Deployment Guide

Target stack:

- Frontend: Vercel, from `cafeqr-frontend`.
- Backend: Render Docker web service, from `cafeqr-backend`.
- Database: Supabase Postgres.
- Redis: Upstash Redis.
- RabbitMQ: CloudAMQP.
- Email: Gmail API sending as `cafeqrllp@gmail.com`.

Do not commit real secrets. Use `.env.example` only as the variable checklist.

## 1. Supabase Postgres

Create a Supabase project for CafeQR 2.0.

Use one of these connection styles for the Spring Boot backend:

- Recommended for Render free: Supabase Session Pooler on port `5432`.
- Also valid: Direct database connection only if IPv4 is available for your environment.

Do not use Transaction Pooler for this app. Hibernate/JDBC can use prepared statements and Supabase transaction pooling does not support prepared statements.

Render variables:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.project-ref
SPRING_DATASOURCE_PASSWORD=...
```

## 2. Upstash Redis

Create an Upstash Redis database in a region close to Render.

Render variables:

```env
SPRING_DATA_REDIS_HOST=...
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=...
SPRING_DATA_REDIS_SSL=true
```

Redis is used for OTPs, cache, and idempotency support.

## 3. CloudAMQP RabbitMQ

Create a CloudAMQP instance and copy the AMQP details.

Render variables:

```env
SPRING_RABBITMQ_HOST=...
SPRING_RABBITMQ_PORT=5671
SPRING_RABBITMQ_USERNAME=...
SPRING_RABBITMQ_PASSWORD=...
SPRING_RABBITMQ_VHOST=...
SPRING_RABBITMQ_SSL=true
```

CloudAMQP is provisioned for the production messaging layer even if the current app does not yet have active producers/listeners.

## 4. Gmail API Email

Render free web services block outbound SMTP ports `25`, `465`, and `587`, so production email must use Gmail API over HTTPS.

Google setup:

1. In Google Cloud Console, create or select a project.
2. Enable Gmail API.
3. Configure OAuth consent and add `cafeqrllp@gmail.com` as a test user if the app is still in testing.
4. Create OAuth credentials.
5. Generate a refresh token with the Gmail send scope.
6. Keep the consent screen in Production for a long-lived refresh token.

Render variables:

```env
EMAIL_PROVIDER=gmail-api
GMAIL_SENDER_EMAIL=cafeqrllp@gmail.com
GMAIL_CLIENT_ID=...
GMAIL_CLIENT_SECRET=...
GMAIL_REFRESH_TOKEN=...
OTP_LOG_CODE=false
```

## 5. JWT And Payments

Generate a fresh RSA key pair for production JWTs. Store Base64 PKCS8 private key material and Base64 X509 public key material in Render:

```env
JWT_PRIVATE_KEY=...
JWT_PUBLIC_KEY=...
```

Rotate Razorpay credentials before production because older keys were present in copied local files:

```env
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
```

## 6. Render Backend

Create a Render Blueprint from `cafeqr-backend/render.yaml`, or create a Docker web service manually with:

- Docker context: `.`
- Dockerfile: `Dockerfile`
- Health check path: `/actuator/health`

Also set:

```env
SPRING_PROFILES_ACTIVE=prod
ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app
FRONTEND_URL=https://your-vercel-domain.vercel.app
APP_OBSERVABILITY_LOG_ALL_API_REQUESTS=false
```

After deployment, confirm:

```text
https://your-render-backend.onrender.com/actuator/health
```

## 7. Vercel Frontend

Import `cafeqr-frontend` into Vercel.

Set:

```env
NEXT_PUBLIC_API_URL=https://your-render-backend.onrender.com
GEMINI_API_KEY=...
# or
GEMINI_API_KEYS=key1,key2
GEMINI_MODELS=gemini-2.5-flash-lite,gemini-2.5-flash,gemini-2.0-flash
NEXT_PUBLIC_AI_PARSE_URL=https://your-vercel-domain.vercel.app/api/ai/parse-menu
```

`NEXT_PUBLIC_API_URL` must not end in `/api`; the frontend already calls paths such as `/api/v1/auth/authenticate`.

## 8. Smoke Tests

Run these after both deployments are live:

- Open `/actuator/health` on Render.
- Sign up and confirm OTP email from `cafeqrllp@gmail.com`.
- Login and confirm token refresh works.
- Open public QR menu and place a test order.
- Verify Redis-backed OTP flow.
- Confirm Flyway migrations completed against Supabase.
- Create and verify a Razorpay test payment.
- Parse one menu image through the Vercel Next API route.
- Complete one POS sale flow.
