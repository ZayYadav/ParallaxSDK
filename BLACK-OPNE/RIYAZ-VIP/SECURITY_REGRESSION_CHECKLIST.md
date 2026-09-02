# Security regression checklist

- Live PackageManager package identity must match the host package.
- Live PackageManager signer SHA-256 must match the signer sent to the panel.
- At least one installed base APK archive must parse and match package + signer in Java.
- At least one installed base APK archive must parse and match package + signer in native JNI.
- Public/split APKs, when parseable, must match package + signer; unavailable split signing metadata is tolerated for OEM compatibility.
- V3 encrypted response signature remains mandatory.
- V3 server-signed identity binding remains mandatory.
- SPECIFIC/AUTO/ANY signing policy remains server-controlled and unchanged.
