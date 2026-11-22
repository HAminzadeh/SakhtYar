# T10-P01 Gate06-D — Actual DataSource Identity Probe

Status: **diagnostic only**; not approval to enable V64 or the 24 business executors.

This gate adds an opt-in `ApplicationRunner` whose SQL executes through Spring's actual runtime DataSource and, when configured, the actual dedicated fixture-admin connection provider. It does NOT prove full workload compatibility or workspace data isolation.

## Activate only for a controlled local startup

Set `APP_T10_DATASOURCE_IDENTITY_PROBE=true` (Spring property `app.quality.t10.datasource-identity-probe`). No application YAML changes are required. Keep `APP_T10_REQUIRE_RESTRICTED_RUNTIME=false` until a restricted runtime user is provisioned and all compatibility tests pass.

The probe logs current_user, session_user, privilege flags, number of T10 history policies, and whether fixture admin uses a distinct principal. It never prints passwords or URLs. It runs only on startup and is **read-only**. A failed connection prevents startup when enabled; disable the probe to restore prior behavior.

Possible outcomes: `fixture_admin_dedicated=false status=BLOCKED` under existing compatibility settings; `status=PARTIAL` if runtime principal is restricted and the dedicated admin is distinct and superuser; otherwise `BLOCKED`. `PARTIAL` does **not** authorize V64.

## Limitations

- The fixture provisioning code currently REQUIRES an actual superuser to create workspace roles. An independent admin credential is required before changing the default application credential.
- The existing runtime default likely remains privileged. Do not enable restricted enforcement yet.
- Three history tables have FORCE RLS but no policies. Do not add a policy trusting a client-controlled GUC alone.
- Live role authentication, grants, startup compatibility, cross-workspace negative tests and 24 business flows remain unproven.
- This package makes no database, Docker, Flyway, or secrets modifications.
- V63 must never be modified; verify next available version before introducing V64 or later migrations.
