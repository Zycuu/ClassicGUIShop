# Building ClassicGUIShop

## Toolchain

Use JDK 25, Gradle 9.6.0, and Python 3.10+. Put `java`, `gradle`, and `python` on PATH, with JAVA_HOME pointing to the build JDK. No Gradle wrapper is currently tracked.

The build chooses runtime bytecode/API requirements from `ports/targets.json`. JDK 25 can compile each declared `options.release`; runtime validation still uses the profile's Java minimum.

## Per-target implementation

```text
gradle --console=plain -PtargetMinecraft=26.3 build
gradle --console=plain -PtargetMinecraft=26.2 build
gradle --console=plain -PtargetMinecraft=26.1.2 build
gradle --console=plain -PtargetMinecraft=1.21.11 build
gradle --console=plain -PtargetMinecraft=1.20.6 build
gradle --console=plain -PtargetMinecraft=1.20.4 build
gradle --console=plain -PtargetMinecraft=1.20.1 build
```

Outputs are under `build/<target>/libs/ClassicGUIShop-mc<target>-v1.4.0.jar`. Non-26.3 Java sources are generated in `build/<target>/generated/` by `prepareTargetSources`. Edit shared `src/` or the adapters, not generated output.

## One installable artifact

After all target builds:

```text
python .github/scripts/package_universal.py release/ClassicGUIShop-v1.4.0.jar
```

The destination must not exist. The script validates child metadata and excluded test classes and packages nested implementations into one outer Fabric mod. Install/distribute only the resulting universal JAR. The target profile's bundled API modules remain within the correct implementation.

Record SHA-256 using `Get-FileHash` on PowerShell or `sha256sum` on Linux. The preserved local 1.4.0 verification summary identifies its tested artifact; independently rebuilt ZIP bytes may differ, so compare/test the actual distribution you publish.

## Version changes and forks

Update `gradle.properties`, hardcoded version/path assertions in `package_universal.py`, and workflow artifact names when changing the release number. Review all profiles and optional permission API targets. Changing metadata to a broad range does not make an implementation compatible with untested versions.

Keep binary fixtures, runtime worlds, secrets, local outputs, and test addons out of the release. `build/`, `run/`, `.gradle/`, logs, and Python cache are ignored. Preserve historical source and migration evidence when removing obsolete behavior.

## CI

`.github/workflows/build.yml` builds all seven implementations, packages the universal JAR, and runs native smoke checks on disposable loopback servers under Java 25/21/17 as appropriate. It uploads the universal artifact/checksum and diagnostic test reports separately.

Pushes and pull requests targeting `main` trigger CI; `workflow_dispatch` allows manual execution. See [Testing](TESTING.md) and the [Developer Guide](../../wiki/Developer-Guide.md) for coverage and architecture.
