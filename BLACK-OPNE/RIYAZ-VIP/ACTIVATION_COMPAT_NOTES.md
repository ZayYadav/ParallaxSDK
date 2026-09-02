# Activation compatibility hardening

The SDK identity guard requires the live PackageManager signer and at least one installed base APK archive to match. Public/split APK archives remain additional tamper signals: if Android exposes their signing metadata they must match, but OEMs that cannot parse an individual split no longer block a genuine activation.

Panel signing policy semantics are unchanged: SPECIFIC remains pinned, AUTO remains first-bind, and ANY accepts any genuine current signer. The v3 server-signed identity binding remains mandatory.
