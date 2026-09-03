# ParallaxSDK security audit — 2026-09-03

Audited base commit: `bd31143f030434158053e5105b1d9cd8f3ff7877` (`main`).

This is a source/configuration security audit of the SDK, Loader, SDK panel, runtime-download path, repository governance, dependency resolution, and active release workflow. It is not a claim that a client running on a fully compromised/rooted device can be made impossible to modify. The goal is fail-closed authorization, strong server trust, minimized replay/tamper windows, and a hardened software supply chain.

## Executive summary

The V3 SDK authorization design is materially stronger than the surrounding build/runtime supply chain. V3 uses authenticated ECDH/AES-GCM transport, server ECDSA signatures, request/nonce binding, device-key proof, short leases, and redundant Java/native package/signing checks. The largest risks found were mutable executable-artifact delivery, mutable CI trust references that receive signing secrets, build-time auto-trust of the currently observed TLS certificate, reverse-proxy header trust without an immediate-proxy allowlist, unrestricted JitPack resolution, and an unprotected `main` branch with no repository rulesets.

The hardening branch fixes the high-impact code/configuration issues that can be changed without altering the SDK panel license/key-generation flow. Compatibility-sensitive changes (permission reduction and SDK R8/lint enforcement), cryptographic dependency verification metadata, and repository settings that cannot be safely generated or changed from this code PR are documented separately.

## Findings

| Severity | Finding | Status |
| --- | --- | --- |
| Critical | Executable runtime ZIP was fetched from mutable `Parallaxapp/main/JANGAM.zip`; accepted native files were validated as ZIP/ELF but not against a signed SHA-256 manifest | **Mitigated now:** URL pinned to immutable audited commit. **Residual:** add signed SHA-256 artifact manifest for independent content verification |
| High | DPT reusable workflow was referenced by mutable branch while receiving production Android signing secrets | **Fixed:** reusable workflow and `dpt_ref` pinned to exact DPT commit |
| High | Loader Gradle resolved the live licensing certificate during CI and appended that observed pin into the release trust set | **Fixed:** removed live-network pin auto-enrollment; release only accepts preconfigured pins |
| High | `TRUST_PROXY=true` trusted `X-Forwarded-For` / `X-Forwarded-Proto` without verifying the immediate proxy | **Fixed:** forwarded headers are authoritative only when `REMOTE_ADDR` is explicitly in `TRUSTED_PROXIES` |
| High | Repository `main` is reported as `protected: false`, required status checks are off, and the repository exposes no rulesets | **Open / repository setting:** protect `main`, block force/deletion, require PR review and required security/build checks before merge |
| High | SDK library manifest requests a very large set of sensitive/system permissions; those may merge into consuming apps | **Open / compatibility-sensitive:** split into minimal-core and virtualization/full permission profiles before removal |
| Medium | Both Gradle projects allowed unrestricted JitPack repository resolution | **Fixed/partially residual:** JitPack is now removed from buildscript resolution and scoped to `com.github.*`; generate Gradle dependency verification metadata/locks from a clean resolved build as the next supply-chain step |
| Medium | SDK release currently keeps `minifyEnabled false` and disables release lint enforcement | **Open / compatibility-sensitive:** restore hardening gradually after reflection/JNI regression suite is green |
| Medium | V3 issues and stores a hashed `session_token`, but the Android activation path does not use that token for a heartbeat/session-validation endpoint | **Open:** revocation remains bounded by the signed lease renewal window rather than near-real-time session checks |
| Medium | Runtime native archive authenticity now depends on an immutable Git commit + HTTPS rather than an application-verified signed SHA-256 manifest | **Open:** next phase should sign artifact metadata with an offline/server signing key and verify before extraction/load |
| Medium | Other active workflows still use major-tag references such as official GitHub Actions `@v6/@v5/@v4` rather than exact SHAs | **Partially addressed:** sensitive DPT workflow is pinned; Dependabot monitoring added. Pin remaining action refs in a follow-up |
| Low | `One-Core-Engine/signing.properties` contains obvious plaintext fallback credentials. The referenced JKS was not present at the inspected repository path, but the pattern encourages unsafe local signing if reused | **Open:** replace with generated local config/example and keep real signing material only in CI secrets |
| Low | Local HTML WebView enables JavaScript and a JS bridge, but network loads are blocked, external navigations are rejected, and only local data is loaded | **Accepted with constraints:** keep network blocked and do not feed remote HTML into this WebView |

## Strong controls already present

- V3 request confidentiality/integrity: ephemeral P-256 ECDH, HKDF-SHA256, AES-256-GCM.
- V3 server authenticity: ECDSA-SHA256 signed response envelope plus separately signed identity binding.
- Replay defense: timestamp window, nonce consumption, unique request IDs, server-side tables and cleanup.
- Device binding: Android Keystore P-256 device key proof and server-side device fingerprint/session state.
- Package/signing binding: PackageManager + installed APK/archive checks in Java and independent native/JNI validation.
- Short signed authorization lease with monotonic elapsed-time handling to resist clock rollback.
- Loader release uses R8/resource shrinking and release signing identity is compiled into Java/native checks.
- Loader network security disables cleartext and trusts system roots only; licensing additionally uses configured TLS pins.
- Native SDK build uses hidden visibility, stack protector, FORTIFY, RELRO/NOW and no-exec-stack flags.
- Facebook exported OAuth callback validates a live short-lived session/state instead of trusting a package/user from browser-controlled URI data.
- Panel uses HTTPS enforcement, HSTS, Secure/HttpOnly/SameSite cookies, CSRF, session rotation/timeouts, role checks, DB-backed rate limiting and audit logs.
- V3 private ECDH/signing keys are configured as server-side files outside the public web root in the example configuration.

## Changes made in this hardening branch

1. `One-Core-Engine/app/src/main/jni/backends/ModsLoader.h`
   - Replaced mutable branch runtime URL with immutable `Parallaxapp` commit `c31b43f515e5af248ce575520dfe80d139ac2f8d`.

2. `One-Core-Engine/app/build.gradle`
   - Removed build-time TLS leaf-pin discovery/auto-append.
   - Release builds now only validate explicitly configured public key/pins; the network cannot silently add a new trust anchor during compilation.

3. `.github/workflows/build-hardened-safe.yml`
   - Pinned checkout/setup-android/setup-java/upload-artifact action revisions used by the sensitive workflow.
   - Pinned `ParallaxDPT` reusable workflow and `dpt_ref` to `7d72080525c22f2987cd46636730ee42a2056054`.
   - Production DPT release now fails closed if the API public key or TLS pins are absent instead of generating fake transport trust.

4. `panels/sdk-panel/panel_security.php`
   - Added immediate reverse-proxy allowlist enforcement.
   - `X-Forwarded-For` and `X-Forwarded-Proto` are ignored unless `REMOTE_ADDR` is allowlisted.
   - `TRUST_PROXY=true` with an empty allowlist fails closed.

5. `panels/sdk-panel/config.local.example.php`
   - Added documented `TRUSTED_PROXIES` setting.

6. `panels/sdk-panel/tests/proxy-trust-self-test.php`
   - Added regression coverage for untrusted/trusted/empty-allowlist proxy cases.

7. `.github/dependabot.yml`
   - Added weekly GitHub Actions and Gradle dependency monitoring.

8. `.github/workflows/security-audit-tests.yml`
   - Added a pinned-action security gate that executes SDK panel crypto/proxy tests.
   - Enforces immutable runtime URL, offline TLS-pin trust, immutable DPT workflow/ref, and scoped JitPack invariants on relevant PRs and pushes.

9. `BLACK-OPNE/build.gradle` and `One-Core-Engine/build.gradle`
   - Removed unnecessary JitPack access from buildscript plugin resolution.
   - Restricted project-level JitPack resolution to `com.github.*` coordinates instead of making JitPack a fallback repository for every dependency group.

## Deployment notes

### Reverse proxy

If PHP is directly exposed to the internet, keep:

```php
'TRUST_PROXY' => false,
'TRUSTED_PROXIES' => [],
```

If a reverse proxy terminates TLS, enable `TRUST_PROXY` only after placing the proxy's actual `REMOTE_ADDR` IP(s) in `TRUSTED_PROXIES`. The proxy must also overwrite/sanitize client-supplied forwarded headers.

### Release trust

The sensitive DPT release workflow now requires valid production values for:

- `PARALLAX_API_PUBLIC_KEY_B64`
- `PARALLAX_TLS_PINS`
- Android signing secrets used by the existing workflow

A missing transport trust anchor is now a build failure, not an automatically trusted live certificate or fake production release.

### Runtime archive updates

`JANGAM.zip` is intentionally pinned to an immutable commit. Updating the runtime requires a reviewed Loader change that moves the URL to a new audited commit. The preferred next design is a signed manifest containing at least artifact name, SHA-256, byte size, version, issued-at/expiry, and signing-key ID.

### Main branch governance

The audited repository reports `main` as unprotected and no rulesets are configured. Configure repository protection so direct/force changes cannot bypass the security gates. At minimum require pull requests, block force-push/deletion, and require the security gate plus normal Android/PHP builds before merge.

### Dependency verification

JitPack is now scoped, but cryptographic Gradle dependency verification metadata should be generated from a trusted clean build after the complete dependency graph has resolved. Commit the resulting verification metadata and, where practical, dependency lock state; do not hand-author checksums without resolving the actual graph.

## Recommended next hardening phase

1. Protect `main` and require successful security/build checks before merge.
2. Add a signed runtime-artifact manifest and verify SHA-256/size/signature before ZIP extraction and before `System.load`.
3. Generate Gradle dependency verification metadata and dependency locks from a trusted clean build.
4. Add a V3 session validation/heartbeat endpoint that accepts the opaque session token, verifies its server-side hash, device/package/signing binding, revocation flag and expiry, and returns a short signed lease refresh.
5. Split the SDK manifest into a minimal default profile and an explicit virtualization/full-permission profile so consuming apps do not inherit unnecessary dangerous permissions.
6. Re-enable SDK release lint and R8 in stages with explicit reflection/JNI keep rules and regression tests rather than globally disabling checks.
7. Remove tracked signing fallback credentials; generate local debug/CI signing config outside source control.
8. Pin the remaining official GitHub Actions to immutable SHAs and let Dependabot update those pins by PR.
9. Add device-key attestation/hardware-backed policy as an optional high-assurance mode where supported; keep a documented fallback for devices without hardware attestation.

## Validation checklist before merge

- `php -l panels/sdk-panel/panel_security.php`
- `php panels/sdk-panel/tests/security-self-test.php`
- `php panels/sdk-panel/tests/proxy-trust-self-test.php`
- Confirm the dedicated `security-gates` GitHub Actions job passes.
- Build SDK release AAR.
- Build Loader release APK with the real API public key/TLS pins and production signing certificate.
- Verify APK with `apksigner verify --verbose --print-certs` and confirm the expected signer SHA-256.
- Exercise V3 activation for `SPECIFIC`, `AUTO`, `ANY` signing policies and package/device policy combinations.
- Verify Facebook/Google/Twitter login regression paths.
- Verify runtime archive download works from the pinned commit and fails if the URL is changed to HTTP/mutable/unavailable content.
- Verify a proxy deployment with correct `TRUSTED_PROXIES`; also confirm spoofed forwarded headers are ignored on direct connections.
- Enable `main` protection/rulesets and verify required checks prevent an unvalidated merge.
- Generate dependency verification metadata from a clean trusted resolution and confirm subsequent builds reject unexpected artifact checksum changes.

## Risk statement

Client-side anti-tamper checks raise attacker cost but cannot be treated as an unbypassable trust boundary on an attacker-controlled/rooted device. License validity, policy, revocation, signing/package binding, and runtime-artifact authorization should remain server-authenticated and fail closed. The V3 design is already aligned with that principle; the changes in this branch bring the build/runtime delivery path closer to the same model.
