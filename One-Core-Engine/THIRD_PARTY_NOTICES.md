# Third-party build tooling

## BlackObfuscator

The release build uses the following build-time projects:

- `CodingGay/BlackObfuscator-ASPlugin`, pinned to commit
  `67aec4c457be0d2644224100fa85aed7eac87cb6`
- `CodingGay/BlackObfuscator` / its `dex-tools` dependency

Both upstream repositories declare the Apache License 2.0. Their source and
license notices are available at:

- https://github.com/CodingGay/BlackObfuscator-ASPlugin
- https://github.com/CodingGay/BlackObfuscator
- https://www.apache.org/licenses/LICENSE-2.0

BlackObfuscator is a build-time control-flow transformation tool and is not
packaged as an application runtime library.
