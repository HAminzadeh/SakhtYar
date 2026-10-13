# Phase 5.17 - Identity and SSO decision record

Status: **REQUIRES EXTERNAL CONFIGURATION AND SECURITY APPROVAL**.
The patch does NOT claim that SSO has been enabled.

## Source of truth
The Java SakhtYar Identity module controls the business user and its permissions.
WordPress roles control CMS access. NEVER map a WordPress administrator role
implicitly to `USER_MANAGE` or grant business permissions because WordPress says so.

## Target architecture
- Choose an OIDC-compliant IdP (e.g. an organization's existing provider or an approved dedicated IdP).
- Register separate OIDC clients for WordPress and React. Use authorization code flow;
  browser public clients need PKCE. Avoid sharing login passwords, WordPress session
  cookies or Java JWT signing secrets between platforms.
- Java backend validates issuer/audience/signatures, links the stable OIDC `sub`
  to its local user and derives application permissions from server-side policy.
- WordPress CMS keeps its own authorization and maps selected authenticated subjects
  only after explicit account-linking/verification.
- Implement logout and token/session expiry tests across both applications.
- Any account migration must support existing users and rollback.

## Acceptance tests (not yet run)
1. One IdP login enables authorized WordPress and React user journeys.
2. Anonymous visitor cannot read a private case.
3. User A cannot request User B's case/contract/builder private data.
4. WordPress admin is NOT Java admin unless separately authorized in Java.
5. Logout, token expiry, CSRF and refresh are tested under production cookie rules.
6. OIDC redirect URI, nonce, state, PKCE, back-channel validation and replay defenses pass review.
7. Production IdP, client registrations and secrets remain outside the repository.

The WordPress app button added in this phase is an explicit hand-off link, not SSO.