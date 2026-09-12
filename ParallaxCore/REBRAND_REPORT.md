# ParallaxCore rebrand report

SDK-owned implementation namespaces and primary classes were migrated to Parallax namespaces/names.
Android/framework mirror identities required for platform compatibility were retained.
The external BlackReflection dependency keeps its published top.niunaijun.blackreflection namespace.

Renamed primary SDK-owned types: **259**

## Namespace map
- `top.niunaijun.blackbox` → `com.Parallax.SDK.core`
- `top.niunaijun.jnihook` → `com.Parallax.SDK.nativebridge`
- `net_62v.external` → `com.Parallax.SDK.internal`
- `android.MetaCore` → `com.Parallax.SDK.runtime`
- `black` → `com.Parallax.SDK.mirror`

## Public API

`com.Parallax.SDK.ParallaxSDK`
