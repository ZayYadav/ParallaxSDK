#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path("ParallaxCore")
JAVA_ROOT = ROOT / "src/main/java"
AIDL_ROOT = ROOT / "src/main/aidl"

# SDK-owned namespaces. Android platform/Binder mirror identities such as
# android.app/android.os/com.android are intentionally preserved.
# top.niunaijun.blackreflection is an external published dependency and is
# explicitly NOT part of this map.
PACKAGE_MAP = [
    ("top.niunaijun.blackbox", "com.Parallax.SDK.core"),
    ("top.niunaijun.jnihook", "com.Parallax.SDK.nativebridge"),
    ("net_62v.external", "com.Parallax.SDK.internal"),
    ("android.MetaCore", "com.Parallax.SDK.runtime"),
    ("black", "com.Parallax.SDK.mirror"),
]
TYPE_RENAME_PREFIXES = {
    "top.niunaijun.blackbox",
    "top.niunaijun.jnihook",
    "net_62v.external",
    "android.MetaCore",
}
SOURCE_EXTS = {".java", ".kt", ".aidl"}
TEXT_EXTS = {
    ".java", ".kt", ".aidl", ".xml", ".cpp", ".cc", ".c", ".h", ".hpp",
    ".mk", ".gradle", ".pro", ".txt", ".md", ".properties", ".json", ".yml", ".yaml"
}
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\s*;?")
IMPORT_RE = re.compile(r"(?m)^\s*import\s+(?:static\s+)?([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)(\.\*)?\s*;?")


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def map_package(pkg: str) -> str:
    for old, new in PACKAGE_MAP:
        if pkg == old:
            return new
        if pkg.startswith(old + "."):
            return new + pkg[len(old):]
    return pkg


def should_rename_type(pkg: str) -> bool:
    return any(pkg == p or pkg.startswith(p + ".") for p in TYPE_RENAME_PREFIXES)


def clean_type_body(name: str) -> str:
    body = name
    if body.startswith("BlackBox"):
        body = body[len("BlackBox"):]
    elif body.startswith("Meta") and len(body) > 4:
        body = body[4:]
    elif len(body) > 1 and body.startswith("B") and body[1].isupper():
        body = body[1:]
    if body == "RCore":
        body = "RuntimeCore"
    elif body == "RNative":
        body = "Native"
    elif body == "db":
        body = "Database"
    elif body == "nk":
        body = "SecurityState"
    return body or "Core"


def parallax_type_name(name: str) -> str:
    if name.startswith("Parallax") or name.startswith("IParallax"):
        return name
    if len(name) > 1 and name.startswith("I") and name[1].isupper():
        return "IParallax" + clean_type_body(name[1:])
    return "Parallax" + clean_type_body(name)


def primary_decl_matches(text: str, stem: str, suffix: str) -> bool:
    if suffix == ".aidl":
        return True
    escaped = re.escape(stem)
    if suffix == ".java":
        pat = rf"\b(?:class|interface|enum|record|@interface)\s+{escaped}\b"
    else:
        pat = rf"\b(?:class|object|interface|enum\s+class|annotation\s+class|data\s+class|sealed\s+class)\s+{escaped}\b"
    return re.search(pat, text) is not None


def source_files() -> list[Path]:
    out: list[Path] = []
    for root in (JAVA_ROOT, AIDL_ROOT):
        if root.exists():
            out.extend(p for p in root.rglob("*") if p.is_file() and p.suffix in SOURCE_EXTS)
    return sorted(out)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def replace_identifier(text: str, old: str, new: str) -> str:
    if old == new:
        return text
    return re.sub(rf"(?<![A-Za-z0-9_$]){re.escape(old)}(?![A-Za-z0-9_$])", new, text)


def replace_package_prefix(text: str, old: str, new: str) -> str:
    """Replace a dot-form package only when it begins a qualified identifier.

    The preceding-dot exclusion is important for the root package `black`: it
    prevents touching the external `top.niunaijun.blackreflection` namespace.
    """
    pattern = rf"(?<![A-Za-z0-9_$\.]){re.escape(old)}(?=\.|\b)"
    return re.sub(pattern, new, text)


def replace_slash_prefix(text: str, old: str, new: str) -> str:
    old_slash = old.replace(".", "/")
    new_slash = new.replace(".", "/")
    pattern = rf"(?<![A-Za-z0-9_$\/]){re.escape(old_slash)}(?=/|\b)"
    return re.sub(pattern, new_slash, text)


def jni_mangle_package(pkg: str) -> str:
    return "_".join(part.replace("_", "_1") for part in pkg.split("."))


def replace_jni_prefix(text: str, old: str, new: str) -> str:
    old_jni = jni_mangle_package(old)
    new_jni = jni_mangle_package(new)
    return re.sub(rf"(?<![A-Za-z0-9_$]){re.escape(old_jni)}(?=_|\b)", new_jni, text)


# Old editor backup sources are not compiled and only preserve obsolete folder
# names. Remove them rather than carrying stale duplicate code into the SDK.
for backup in JAVA_ROOT.rglob("*.bak") if JAVA_ROOT.exists() else []:
    backup.unlink()

files = source_files()
metadata: dict[Path, tuple[str, str]] = {}
class_map: dict[str, str] = {}
used_new: dict[str, str] = {}

for path in files:
    text = read_text(path)
    m = PACKAGE_RE.search(text)
    if not m:
        continue
    pkg = m.group(1)
    stem = path.stem
    metadata[path] = (pkg, stem)
    if stem in {"package-info", "module-info"} or not should_rename_type(pkg):
        continue
    if not primary_decl_matches(text, stem, path.suffix):
        continue
    old_fqcn = f"{pkg}.{stem}"
    new_pkg = map_package(pkg)
    new_simple = parallax_type_name(stem)
    candidate = f"{new_pkg}.{new_simple}"
    if candidate in used_new and used_new[candidate] != old_fqcn:
        new_simple = "ParallaxCore" + stem.lstrip("B")
        candidate = f"{new_pkg}.{new_simple}"
        if candidate in used_new and used_new[candidate] != old_fqcn:
            raise SystemExit(f"Type collision: {old_fqcn} -> {candidate}")
    class_map[old_fqcn] = candidate
    used_new[candidate] = old_fqcn

by_package: dict[str, dict[str, str]] = defaultdict(dict)
for old_fqcn, new_fqcn in class_map.items():
    old_pkg, old_simple = old_fqcn.rsplit(".", 1)
    by_package[old_pkg][old_simple] = new_fqcn.rsplit(".", 1)[1]

all_old_fqcns = sorted(class_map, key=len, reverse=True)
package_pairs = sorted(PACKAGE_MAP, key=lambda x: len(x[0]), reverse=True)


def source_simple_replacements(text: str, current_pkg: str, own_fqcn: str | None) -> dict[str, str]:
    replacements: dict[str, str] = dict(by_package.get(current_pkg, {}))
    if own_fqcn and own_fqcn in class_map:
        replacements[own_fqcn.rsplit(".", 1)[1]] = class_map[own_fqcn].rsplit(".", 1)[1]
    for match in IMPORT_RE.finditer(text):
        target = match.group(1)
        if match.group(2):
            replacements.update(by_package.get(target, {}))
            continue
        for old_fqcn in all_old_fqcns:
            if target == old_fqcn or target.startswith(old_fqcn + "."):
                replacements[old_fqcn.rsplit(".", 1)[1]] = class_map[old_fqcn].rsplit(".", 1)[1]
                break
    return replacements


def rewrite_common(text: str) -> str:
    # Concrete classes first so reflection strings/JNI registration entries are
    # updated before their package prefix is moved.
    for old_fqcn in all_old_fqcns:
        new_fqcn = class_map[old_fqcn]
        text = replace_package_prefix(text, old_fqcn, new_fqcn)
        text = replace_slash_prefix(text, old_fqcn, new_fqcn)
        text = replace_jni_prefix(text, old_fqcn, new_fqcn)

    for old_pkg, new_pkg in package_pairs:
        text = replace_package_prefix(text, old_pkg, new_pkg)
        text = replace_slash_prefix(text, old_pkg, new_pkg)
        # Do not apply the extremely generic `black_` JNI prefix globally.
        if old_pkg != "black":
            text = replace_jni_prefix(text, old_pkg, new_pkg)
    return text


for path in files:
    if not path.exists():
        continue
    text = read_text(path)
    meta = metadata.get(path)
    if not meta:
        continue
    old_pkg, stem = meta
    own_fqcn = f"{old_pkg}.{stem}"
    for old_simple, new_simple in sorted(
        source_simple_replacements(text, old_pkg, own_fqcn).items(),
        key=lambda x: len(x[0]), reverse=True
    ):
        text = replace_identifier(text, old_simple, new_simple)
    path.write_text(rewrite_common(text), encoding="utf-8")

simple_global: dict[str, str] = {}
for old_fqcn, new_fqcn in class_map.items():
    old_simple = old_fqcn.rsplit(".", 1)[1]
    new_simple = new_fqcn.rsplit(".", 1)[1]
    previous = simple_global.get(old_simple)
    if previous is None or previous == new_simple:
        simple_global[old_simple] = new_simple

for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_EXTS or path in files:
        continue
    try:
        text = read_text(path)
    except UnicodeDecodeError:
        continue
    text = rewrite_common(text)
    # Simple Java type names are safe to update in declarative consumer files,
    # but not in native C/C++ or Android.mk. Native source has local symbols and
    # physical include paths such as JniHook/JniHook.h and BoxCore.h which are
    # intentionally not Java type names. Rewriting those strings without moving
    # the native files corrupts the NDK build. Fully-qualified JNI/reflection
    # identities above are still migrated by rewrite_common().
    if path.suffix.lower() in {".xml", ".pro", ".gradle", ".properties"}:
        for old_simple, new_simple in sorted(simple_global.items(), key=lambda x: len(x[0]), reverse=True):
            text = replace_identifier(text, old_simple, new_simple)
    path.write_text(text, encoding="utf-8")

gradle = ROOT / "build.gradle"
if gradle.exists():
    text = read_text(gradle)
    text = re.sub(r'namespace\s+["\'][^"\']+["\']', 'namespace "com.Parallax.SDK"', text, count=1)
    gradle.write_text(text, encoding="utf-8")

# Move files according to their rewritten package and primary class names.
for old_path in files:
    if not old_path.exists():
        continue
    text = read_text(old_path)
    m = PACKAGE_RE.search(text)
    if not m:
        continue
    new_pkg = m.group(1)
    old_pkg, old_stem = metadata[old_path]
    old_fqcn = f"{old_pkg}.{old_stem}"
    new_stem = class_map.get(old_fqcn, f"{new_pkg}.{old_stem}").rsplit(".", 1)[1]
    source_root = JAVA_ROOT if JAVA_ROOT in old_path.parents else AIDL_ROOT
    new_path = source_root / Path(*new_pkg.split(".")) / f"{new_stem}{old_path.suffix}"
    if new_path == old_path:
        continue
    new_path.parent.mkdir(parents=True, exist_ok=True)
    if new_path.exists():
        raise SystemExit(f"Target already exists: {new_path}")
    run("git", "mv", str(old_path), str(new_path))

# Catch manifests/native tables/ProGuard strings after the physical moves.
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_EXTS:
        continue
    try:
        text = read_text(path)
    except UnicodeDecodeError:
        continue
    path.write_text(rewrite_common(text), encoding="utf-8")

old_tokens = [
    "top.niunaijun.blackbox",
    "top.niunaijun.jnihook",
    "net_62v",
    "android.MetaCore",
]
bad_tokens: list[str] = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_EXTS:
        continue
    try:
        text = read_text(path)
    except UnicodeDecodeError:
        continue
    for token in old_tokens:
        if token in text or token.replace(".", "/") in text:
            bad_tokens.append(f"{path}: {token}")
    if re.search(r"(?m)^\s*(?:package|import)\s+black(?:\.|;)", text):
        bad_tokens.append(f"{path}: black package/import")
    # Guard the external dependency from accidental rebranding.
    if "com.Parallax.SDK.mirrorreflection" in text or "top.niunaijun.com.Parallax" in text:
        bad_tokens.append(f"{path}: BlackReflection namespace was corrupted")

old_dirs = [
    JAVA_ROOT / "top/niunaijun",
    JAVA_ROOT / "net_62v",
    JAVA_ROOT / "android/MetaCore",
    JAVA_ROOT / "black",
    AIDL_ROOT / "top/niunaijun",
    AIDL_ROOT / "android/MetaCore",
]
for directory in old_dirs:
    if directory.exists() and any(p.is_file() for p in directory.rglob("*")):
        bad_tokens.append(f"old source directory still populated: {directory}")

if bad_tokens:
    print("Rebrand audit failed:")
    print("\n".join(bad_tokens[:200]))
    raise SystemExit(1)

bad_types: list[str] = []
for path in source_files():
    text = read_text(path)
    m = PACKAGE_RE.search(text)
    if not m:
        continue
    pkg = m.group(1)
    if not pkg.startswith((
        "com.Parallax.SDK.core",
        "com.Parallax.SDK.nativebridge",
        "com.Parallax.SDK.internal",
        "com.Parallax.SDK.runtime",
    )):
        continue
    stem = path.stem
    if stem not in {"package-info", "module-info"} and not (
        stem.startswith("Parallax") or stem.startswith("IParallax")
    ):
        bad_types.append(str(path))
if bad_types:
    print("Unbranded custom primary types remain:")
    print("\n".join(bad_types[:200]))
    raise SystemExit(1)

report = ROOT / "REBRAND_REPORT.md"
lines = [
    "# ParallaxCore rebrand report",
    "",
    "SDK-owned implementation namespaces and primary classes were migrated to Parallax namespaces/names.",
    "Android/framework mirror identities required for platform compatibility were retained.",
    "The external BlackReflection dependency keeps its published top.niunaijun.blackreflection namespace.",
    "",
    f"Renamed primary SDK-owned types: **{len(class_map)}**",
    "",
    "## Namespace map",
]
for old, new in PACKAGE_MAP:
    lines.append(f"- `{old}` → `{new}`")
lines += ["", "## Public API", "", "`com.Parallax.SDK.ParallaxSDK`", ""]
report.write_text("\n".join(lines), encoding="utf-8")

print(f"ParallaxCore migration complete: {len(class_map)} primary types renamed")
print("Namespace audit passed")
