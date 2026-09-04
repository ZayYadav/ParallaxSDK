# TLS pin rotation

OneCore Loader keeps OkHttp certificate pinning enabled for `keshavxownerloader.keshavxownerserver.online`.

## Effective trust set

The APK always contains the current source-controlled production SPKI pin:

```text
sha256/Ina/GY9COYngSpB7Ht+asGkJ5LV99jpBHYz11jXRSWM=
```

`KESHAVXOWNER_TLS_PINS` from GitHub Actions/Gradle is **additive**. Any valid pins supplied there are merged with the source-controlled pin and de-duplicated before `BuildConfig.KESHAVXOWNER_TLS_PINS` is generated.

This prevents a syntactically valid typo in a repository variable from removing the known-good production trust anchor from a release APK.

## Rotation procedure

1. Before changing the server certificate, calculate the new certificate/intermediate SPKI pin using a trusted connection.
2. Add the new pin to the repository variable `KESHAVXOWNER_TLS_PINS` while the old source-controlled pin is still valid.
3. Build and deploy a Loader containing both pins.
4. Rotate the server certificate.
5. Confirm Loader activation succeeds on the new certificate.
6. In a later source release, replace the source-controlled old pin with the new approved production pin and remove obsolete rotation pins when they are no longer needed.

Pins are never learned or auto-enrolled from the live network during a build. A CA, DNS, or build-network compromise therefore cannot silently add a new trust anchor to the APK.

The security audit workflow also checks that the approved source pin is present and rejects the previously observed mistyped pin.
