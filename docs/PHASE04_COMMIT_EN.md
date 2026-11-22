feat(crawler): add Phase04 SSRF boundaries and synthetic knowledge golden corpus

- Disable automatic HTTP redirects in PageFetcher and revalidate each redirect destination.
- Restrict crawling to configured allowedHost; deny cross-host redirects and HTTPS downgrades.
- Reject localhost, private/reserved IP ranges and unsafe URL syntax; limit response bodies to 8 MiB.
- Preserve TESTER outbound block in the HTTP adapter and reject TESTER in IngestionPipeline before database writes.
- Add security-focused JUnit tests, including a no-network mock HTTP redirect test.
- Add a two-page clearly synthetic Persian PDF fixture with page identifiers, JSONL evidence and SHA256.
- Include corrupt ZIP and tampered checksum negative fixtures for future isolated import testing.
- Document eight Phase04 scenario gaps and supply read-only scope/RLS audit queries.
- No new migration, no DB role changes, no claim of completed business E2E.

Validation: JDK-only static smoke performed in packaging environment; Maven/HTTP/DB/Browser tests still require Windows runtime.
