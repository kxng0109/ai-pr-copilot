# Production authentication — decision brief

Status: **unresolved.** This document records the researched options and
the decisions still needed from you. Nothing here has been implemented.

## Current state

`prod` auth mode is configured (Spring Security OAuth2 Resource Server,
JWT) but has never been exercised, because no OIDC issuer URI has been
supplied. `selfhost` mode (pre-shared API key) works and is tested.

## Options researched

| Provider | Cost | JWT issuance | Best fit |
|---|---|---|---|
| **Keycloak** (self-hosted) | free, self-hosted | you control issuer + JWKS | most common for self-hosted open source |
| **GitHub OAuth** | free for OAuth apps | issues JWTs for OIDC federation | cheapest managed option |
| **Auth0** | free dev plan, paid above | standard OIDC `id_token` | managed, simplest to operate |
| **Okta / Google / Azure AD** | paid / free-with-account | standard OIDC | enterprise |

Keycloak wins if you want full control of the issuer URI and JWKS keys;
GitHub OAuth wins if you want zero infrastructure.

## Spring Security configuration (what is needed)

- `spring.security.oauth2.resourceserver.jwt.issuer-uri` — Spring derives
  the JWK Set URI from the IdP's `.well-known/openid-configuration`.
- Common pitfalls: JWKS caching across key rotation, clock skew on `exp`/`nbf`,
  and issuer mismatch (any mismatch returns 401).
- `jwk-set-uri` can be set alongside `issuer-uri` so startup does not depend
  on IdP availability.

## GHCR visibility

Default on first publish is **private**; once made public it cannot be made
private again. Public packages allow anonymous `docker pull`. Sigstore
keyless signing flow is unchanged by visibility.

## Tenant isolation

Recommended minimum for v1.0: per-tenant API keys plus cache isolation at
the application layer.

## Decisions still needed from you

1. OIDC issuer URI — which provider, or "selfhost only for now"?
2. GHCR visibility — public or private?
3. Tenant model — single-tenant, or per-tenant isolation?

Answer any subset and the corresponding configuration gets implemented.
Until then, `prod` mode remains configured but unverified.