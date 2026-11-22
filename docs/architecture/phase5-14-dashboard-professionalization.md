# Phase 5.14 — Dashboard & Help Professionalization

## Operations
- Core, AI and Observability health are separate.
- RED: Rate, Errors, Duration/P95.
- USE: JVM/CPU utilization and DB-pool saturation.
- Grafana local endpoint is `http://localhost:13001`; Docker-to-Docker remains `http://grafana:3000`.
- Observability failures are WARNING, not automatic application CRITICAL incidents.
- Correlation search is proxied through the authenticated SakhtYar backend.

## AI Control Center
Persian tabs:
- نمای کلی
- ارائه‌دهنده‌ها
- مدل‌ها
- عامل‌ها
- مسیریابی عامل‌ها
- پرامپت‌ها
- کیفیت
- مصرف و عملکرد

Real reliability data comes from `ai_usage_event`. Quality values are deliberately not fabricated: the UI clearly marks metrics that require future Evaluation Events.

## Help
Help is now a full tabbed training surface:
- معرفی
- مفاهیم
- نحوه استفاده / مسیریابی
- تکنولوژی‌ها
- عیب‌یابی

## Security
The browser still never connects directly to Prometheus/Loki/Tempo. Deep investigation is linked to Grafana while infrastructure access remains server-side.