feat(security): harden Phase 03 refresh CSRF, case ACL and document access

- Serialize refresh/revoke token operations with JPA pessimistic row locking.
- Require CSRF for cookie-based web refresh while retaining mobile endpoint exemptions.
- Update the frontend refresh flow to obtain and send the XSRF cookie token.
- Add REAL case membership read/grant/revoke API with creator/admin-only mutation.
- Centralize nested case route authorization and block member-write IDOR paths.
- Prevent direct document-id access from bypassing case-level authorization.
- Add contract and regression tests for refresh locking, case ACL and nested routes.
- Document read-only PostgreSQL scope audit and clone-only restore procedures.
- Preserve V57–V66 Flyway history; introduce no schema changes or REAL data writes.

Limitations:
- Full E2E-01/02/03/05/06/14 business scenarios remain BLOCKED until real
  restricted-identity, cross-workspace, HTTP, browser and restore evidence exists.
- TEST MinIO namespace and cross-domain RLS are not enabled by this patch.
- New tests need to be executed on the target Windows/Maven environment.
