# OneCore Server Download Setup

This branch adds a second BGMI installation path:

- Existing **INSTALL** keeps cloning/installing BGMI from the phone.
- New **INSTALL FROM SERVER** downloads the APK and multipart OBB payload.
- APK is installed inside OneCore with the existing BlackBox storage installer.
- OBB parts are downloaded with HTTP Range resume, joined into one ZIP, extracted, and moved to OneCore virtual storage at:
  `SdCard/Android/obb/com.pubg.imobile/`
- No SHA/hash fields are used by this server-download manifest.

## 1. What stays on your main server

Only one tiny file is required:

`manifest.json`

Example:

```json
{
  "schema": 1,
  "package_name": "com.pubg.imobile",
  "apk": {
    "filename": "BGMI.apk",
    "url": "https://cdn.example.com/bgmi/4.2/BGMI.apk"
  },
  "obb": {
    "archive_name": "bgmi_obb.zip",
    "output_name": "main.12345.com.pubg.imobile.obb",
    "parts": [
      {
        "name": "bgmi_obb.zip.part00",
        "url": "https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part00"
      },
      {
        "name": "bgmi_obb.zip.part01",
        "url": "https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part01"
      }
    ]
  }
}
```

The APK and all OBB parts should live on your CDN/object-storage origin, not on the panel/API server.

## 2. Loader manifest URL

Edit exactly one file:

`One-Core-Engine/app/src/main/assets/server_download_config.json`

Example:

```json
{
  "manifest_url": "https://panel.example.com/downloads/bgmi/manifest.json"
}
```

After this URL is compiled into the Loader, APK/OBB URLs can be changed later by editing only the remote manifest.

## 3. Create the multipart OBB payload

The included helper creates a normal ZIP containing the OBB with **no recompression**, then splits the ZIP bytes into 400 MiB pieces.

Linux / Termux / Windows Python:

```bash
cd One-Core-Engine/server-download

python make_server_payload.py \
  --obb "/path/to/main.12345.com.pubg.imobile.obb" \
  --apk-url "https://cdn.example.com/bgmi/4.2/BGMI.apk" \
  --cdn-base "https://cdn.example.com/bgmi/4.2" \
  --output-dir "./server_payload"
```

Output:

```text
server_payload/
├── bgmi_obb.zip.part00
├── bgmi_obb.zip.part01
├── bgmi_obb.zip.part02
├── bgmi_obb.zip.part03
└── manifest.json
```

For a 1-1.5 GB OBB, 400 MiB is a useful default chunk size.

## 4. Upload locations

CDN/object storage:

```text
https://cdn.example.com/bgmi/4.2/BGMI.apk
https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part00
https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part01
https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part02
https://cdn.example.com/bgmi/4.2/bgmi_obb.zip.part03
```

Main server:

```text
https://panel.example.com/downloads/bgmi/manifest.json
```

Do not overwrite old CDN URLs when releasing a new game version. Prefer a new folder such as:

```text
/bgmi/4.2/
/bgmi/4.3/
```

Then point the main-server manifest to the new version.

## 5. CDN settings

Recommended behavior for APK and part files:

- HTTPS only.
- Long cache TTL for versioned file URLs.
- HTTP Range requests enabled so interrupted downloads resume.
- Each OBB part kept below the CDN's per-file cache limit.
- Use static versioned paths rather than replacing files behind the same URL.

## 6. Manifest rules

- `package_name` must be `com.pubg.imobile` for the current BGMI profile.
- `apk.url` must be HTTPS.
- `obb.output_name` must exactly match the OBB filename inside the reconstructed ZIP.
- `obb.parts` order is the binary join order.
- Part names may be arbitrary, but generated `.part00`, `.part01` naming is recommended.
- No hash or SHA fields are required.

The Loader still checks the downloaded APK's Android package name before passing it to OneCore.
