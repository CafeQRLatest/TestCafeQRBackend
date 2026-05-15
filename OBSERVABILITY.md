# Production Observability Checks

Use this while the current deployment remains Vercel frontend, Render backend, Supabase Postgres, and Redis.

## API Route Size And Timing

The backend logs high-risk routes at INFO with:

```text
api_request method=GET route=/api/v1/orders/history?... status=200 durationMs=42 responseBytes=18423
```

High-risk routes currently include Sales live data, Order History, sync bootstrap/changes, and reports. To log all API routes at INFO, set:

```env
APP_OBSERVABILITY_LOG_ALL_API_REQUESTS=true
```

## Supabase Query Checks

Enable the `pg_stat_statements` extension in Supabase if it is not already enabled, then inspect frequent or heavy queries:

```sql
create extension if not exists pg_stat_statements;

select
  calls,
  round(total_exec_time::numeric, 2) as total_exec_ms,
  round(mean_exec_time::numeric, 2) as mean_exec_ms,
  rows,
  left(query, 180) as query
from pg_stat_statements
order by total_exec_time desc
limit 20;
```

Watch for unbounded `orders`, `order_lines`, reports, or sync queries returning large row counts.

## Frontend Payload Checks

From `cafeTestQRFrontend`, run the lightweight payload check with a valid token/context:

```bash
API_URL=https://your-render-backend ACCESS_TOKEN=... CLIENT_ID=... ORG_ID=... npm run measure:api
```

Compare the byte counts before and after Sales/history/sync changes.
