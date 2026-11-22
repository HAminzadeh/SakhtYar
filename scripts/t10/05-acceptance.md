# T10 acceptance gates (NOT YET PASSED)

- [ ] Fresh PostgreSQL installation with all Flyway migrations completes.
- [ ] Upgrade of existing PostgreSQL with real records is non-destructive.
- [ ] Per-table RLS + FORCE policies validated for applicable tables and roles.
- [ ] Direct SQL cannot cross TEST/REAL boundaries; FK relationships cannot bridge scopes.
- [ ] Worker/outbox/email/SMS/AI/HTTP/cache/object storage safely sandboxed.
- [ ] 24/24 executors implement actual steps and assertions, not endpoint-existence checks.
- [ ] Durable test history is queryable across application restarts.
- [ ] Failed runs retain evidence and never delete REAL records.
- [ ] Negative security tests and full CI integration suite pass.
