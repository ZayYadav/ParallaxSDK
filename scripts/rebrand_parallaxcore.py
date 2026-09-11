#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path("ParallaxCore")
JAVA_ROOT = ROOT / "src/main/java"
AIDL_ROOT = ROOT / "src/main/aidl"

# SDK-owned namespaces. Android platform mirror namespaces (android.app,
# android.os, com.android, etc.) are intentionally not touched.
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
    # Preserve Java interface convention while branding the implementation name.
    if len(name) > 1 and name.startswith("I") and name[1].isupper():
        body = clean_type_body(name[1:])
        return "IParallax" + body
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
    out = []
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


def jni_mangle_package(pkg: str) -> str:
    # JNI underscore escaping: '_' -> '_1', then package separators -> '_'.
    return "_".join(part.replace("_", "_1") for part in pkg.split("."))


files = source_files()
metadata: dict[Path, tuple[str, str]] = {}
class_map: dict[str, str] = {}
used_new: dict[str, str] = {}

# Build one deterministic FQCN map first so Java/Kotlin/AIDL references can be
# rewritten consistently before any file is moved.
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
        # Kotlin source files containing only top-level functions keep their JVM
        # file facade name; their package is still migrated.
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
    new_simple = new_fqcn.rsplit(".", 1)[1]
    by_package[old_pkg][old_simple] = new_simple

all_old_fqcns = sorted(class_map, key=len, reverse=True)
package_pairs = sorted(PACKAGE_MAP, key=lambda x: len(x[0]), reverse=True)


def source_simple_replacements(text: str, current_pkg: str, own_fqcn: str | None) -> dict[str, str]:
    replacements: dict[str, str] = {}
    replacements.update(by_package.get(current_pkg, {}))
    if own_fqcn and own_fqcn in class_map:
        old_simple = own_fqcn.rsplit(".", 1)[1]
        replacements[old_simple] = class_map[own_fqcn].rsplit(".", 1)[1]

    for match in IMPORT_RE.finditer(text):
        target = match.group(1)
        wildcard = bool(match.group(2))
        if wildcard:
            replacements.update(by_package.get(target, {}))
            continue
        for old_fqcn in all_old_fqcns:
            if target == old_fqcn or target.startswith(old_fqcn + "."):
                replacements[old_fqcn.rsplit(".", 1)[1]] = class_map[old_fqcn].rsplit(".", 1)[1]
                break
    return replacements


def rewrite_common(text: str) -> str:
    # Fully-qualified type references first.
    for old_fqcn in all_old_fqcns:
        text = text.replace(old_fqcn, class_map[old_fqcn])
        text = text.replace(old_fqcn.replace(".", "/"), class_map[old_fqcn].replace(".", "/"))
        text = text.replace(jni_mangle_package(old_fqcn), jni_mangle_package(class_map[old_fqcn]))

    # Then namespace prefixes, including JNI and slash-form reflection strings.
    for old_pkg, new_pkg in package_pairs:
        text = text.replace(old_pkg, new_pkg)
        text = text.replace(old_pkg.replace(".", "/"), new_pkg.replace(".", "/"))
        text = text.replace(jni_mangle_package(old_pkg), jni_mangle_package(new_pkg))
        # Some hand-written native symbols used naive dot->underscore names.
        text = text.replace(old_pkg.replace(".", "_"), new_pkg.replace(".", "_"))
    return text


# Rewrite source with import/same-package-aware simple-name changes.
for path in files:
    if not path.exists():
        continue
    text = read_text(path)
    meta = metadata.get(path)
    if not meta:
        continue
    old_pkg, stem = meta
    own_fqcn = f"{old_pkg}.{stem}"
    replacements = source_simple_replacements(text, old_pkg, own_fqcn)
    for old_simple, new_simple in sorted(replacements.items(), key=lambda x: len(x[0]), reverse=True):
        text = replace_identifier(text, old_simple, new_simple)
    text = rewrite_common(text)
    path.write_text(text, encoding="utf-8")

# Rewrite every other textual project/build/native resource. In non-language
# files it is safe and necessary to update simple class strings used by JNI,
# reflection, manifests and ProGuard rules.
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
    if path.suffix.lower() in {".xml", ".cpp", ".cc", ".c", ".h", ".hpp", ".mk", ".pro", ".gradle", ".properties"}:
        for old_simple, new_simple in sorted(simple_global.items(), key=lambda x: len(x[0]), reverse=True):
            text = replace_identifier(text, old_simple, new_simple)
    path.write_text(text, encoding="utf-8")

# Gradle namespace is SDK-owned and should expose the Parallax package.
gradle = ROOT / "build.gradle"
if gradle.exists():
    text = read_text(gradle)
    text = re.sub(r'namespace\s+["\'][^"\']+["\']', 'namespace "com.Parallax.SDK"', text, count=1)
    gradle.write_text(text, encoding="utf-8")

# Move Java/Kotlin/AIDL files so filesystem paths match the new package and
# primary type names. Platform mirror packages not covered by PACKAGE_MAP stay.
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

# Final content pass after moves catches relative class names in manifests and
# native registration tables that were not source imports.
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in TEXT_EXTS:
        continue
    try:
        text = read_text(path)
    except UnicodeDecodeError:
        continue
    text = rewrite_common(text)
    path.write_text(text, encoding="utf-8")

# Strong audit: old SDK-owned namespaces/folders must be gone from the module.
old_tokens = ["top.niunaijun", "net_62v", "android.MetaCore"]
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

# Audit primary custom types: every SDK-owned implementation type is branded.
bad_types: list[str] = []
for path in source_files():
    text = read_text(path)
    m = PACKAGE_RE.search(text)
    if not m:
        continue
    pkg = m.group(1)
    if not pkg.startswith(("com.Parallax.SDK.core", "com.Parallax.SDK.nativebridge", "com.Parallax.SDK.internal", "com.Parallax.SDK.runtime")):
        continue
    stem = path.stem
    if stem in {"package-info", "module-info"}:
        continue
    if not (stem.startswith("Parallax") or stem.startswith("IParallax")):
        bad_types.append(str(path))
if bad_types:
    print("Unbranded custom primary types remain:")
    print("\n".join(bad_types[:200]))
    raise SystemExit(1)

report = ROOT / "REBRAND_REPORT.md"
lines = [
    "# ParallaxCore rebrand report",
    "",
    "SDK-owned implementation namespaces were migrated to Parallax namespaces.",
    "Android/framework mirror namespaces required for platform compatibility were retained.",
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
