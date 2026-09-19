# Capacity Plan

## Baseline production

پیشنهاد اولیه:

```text
8 vCPU
32 GB RAM
500 GB NVMe
```

تقسیم منطقی منابع:

```text
PostgreSQL     8-12 GB
API            3-5 GB
Worker         3-5 GB
Agent          4-8 GB
Collector      2-4 GB
Redis          2 GB
MinIO          2-4 GB
OS/Proxy       remaining
```

اعداد بالا guideline هستند و باید با metrics تنظیم شوند.

## AI server

اگر مدل محلی جدی اجرا شود:

```text
16 vCPU
64 GB RAM
GPU recommended
```

در production بهتر است inference سنگین روی سرویس مستقل باشد.

## Storage

اسناد مشارکت می‌توانند رشد زیادی داشته باشند.

پیشنهاد:

- NVMe برای PostgreSQL
- disk/volume مجزا برای MinIO
- nightly backup
- off-server backup copy
