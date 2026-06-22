# Build artifact naming

ClassicGUIShop release JARs use this format:

```text
ClassicGUIShop-<minecraft-version>-v<mod-version>.jar
```

For the current release candidate:

```text
ClassicGUIShop-26.1.1-v1.0.0.jar
```

When either Minecraft support or the mod version changes, update the corresponding values in `gradle.properties`. Gradle generates the matching filename automatically.
