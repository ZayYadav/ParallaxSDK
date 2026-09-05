#!/usr/bin/env python3
import argparse
import json
import shutil
import urllib.parse
import zipfile
from pathlib import Path

DEFAULT_CHUNK_MIB = 400


def parse_args():
    p = argparse.ArgumentParser(
        description="Create a ZIP_STORED OBB archive, split it into CDN-friendly parts, and emit manifest.json."
    )
    p.add_argument("--obb", required=True, help="Path to the final .obb file")
    p.add_argument("--apk-url", required=True, help="Public HTTPS URL of the APK on your CDN/storage")
    p.add_argument("--cdn-base", required=True, help="HTTPS folder URL where the generated OBB parts will be uploaded")
    p.add_argument("--package", default="com.pubg.imobile", help="Android package name")
    p.add_argument("--chunk-mib", type=int, default=DEFAULT_CHUNK_MIB, help="Chunk size in MiB; keep below CDN cache per-file limits")
    p.add_argument("--archive-name", default="bgmi_obb.zip", help="Reconstructed archive name")
    p.add_argument("--apk-filename", default="BGMI.apk", help="Local display filename for downloaded APK")
    p.add_argument("--output-dir", default="server_payload", help="Output folder")
    p.add_argument("--keep-archive", action="store_true", help="Keep the intermediate full ZIP after splitting")
    return p.parse_args()


def require_https(value: str, label: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme.lower() != "https" or not parsed.netloc:
        raise SystemExit(f"{label} must be an HTTPS URL")
    return value.rstrip("/")


def split_file(source: Path, output_dir: Path, chunk_bytes: int):
    parts = []
    with source.open("rb") as src:
        index = 0
        while True:
            chunk = src.read(chunk_bytes)
            if not chunk:
                break
            name = f"{source.name}.part{index:02d}"
            path = output_dir / name
            path.write_bytes(chunk)
            parts.append(path)
            index += 1
    if not parts:
        raise SystemExit("Archive was empty")
    return parts


def main():
    args = parse_args()
    obb = Path(args.obb).expanduser().resolve()
    if not obb.is_file():
        raise SystemExit(f"OBB not found: {obb}")
    if not obb.name.lower().endswith(".obb"):
        raise SystemExit("Input file must end with .obb")
    if args.chunk_mib < 16 or args.chunk_mib > 500:
        raise SystemExit("--chunk-mib must be between 16 and 500")

    apk_url = require_https(args.apk_url, "APK URL")
    cdn_base = require_https(args.cdn_base, "CDN base URL")

    out = Path(args.output_dir).expanduser().resolve()
    out.mkdir(parents=True, exist_ok=True)

    archive = out / Path(args.archive_name).name
    if archive.exists():
        archive.unlink()

    print(f"[1/3] Creating {archive.name} with ZIP_STORED (no recompression)...")
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as zf:
        zf.write(obb, arcname=obb.name)

    chunk_bytes = args.chunk_mib * 1024 * 1024
    print(f"[2/3] Splitting into {args.chunk_mib} MiB parts...")
    parts = split_file(archive, out, chunk_bytes)

    manifest = {
        "schema": 1,
        "package_name": args.package,
        "apk": {
            "filename": Path(args.apk_filename).name,
            "url": apk_url,
        },
        "obb": {
            "archive_name": archive.name,
            "output_name": obb.name,
            "parts": [
                {
                    "name": part.name,
                    "url": f"{cdn_base}/{urllib.parse.quote(part.name)}",
                }
                for part in parts
            ],
        },
    }

    manifest_path = out / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    if not args.keep_archive:
        archive.unlink(missing_ok=True)

    print("[3/3] Done")
    print(f"Payload folder: {out}")
    print(f"Manifest:       {manifest_path}")
    print(f"OBB parts:      {len(parts)}")
    for part in parts:
        print(f"  - {part.name} ({part.stat().st_size / (1024*1024):.1f} MiB)")
    print("\nUpload the APK and all .partXX files to your CDN/storage.")
    print("Upload manifest.json to your small main server.")
    print("Then put that manifest HTTPS URL in:")
    print("  app/src/main/assets/server_download_config.json")


if __name__ == "__main__":
    main()
