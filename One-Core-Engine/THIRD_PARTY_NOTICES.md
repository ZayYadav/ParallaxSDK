# Third-party build tooling

## StringFog

The release build uses `MegatronKing/StringFog` for automatic string protection
in first-party Loader and SDK bytecode.

- Gradle plugin: `com.github.megatronking.stringfog:gradle-plugin:5.2.0`
- XOR runtime/transform implementation: `com.github.megatronking.stringfog:xor:5.0.0`
- Release configuration uses `StringFogMode.bytes` with per-string random keys.

StringFog is licensed under the Apache License 2.0. Upstream source and license
information are available at:

- https://github.com/MegatronKing/StringFog
- https://www.apache.org/licenses/LICENSE-2.0

StringFog rewrites protected bytecode string literals into runtime decrypt calls.
It is defense in depth and does not make client-side data impossible to recover
at runtime.
