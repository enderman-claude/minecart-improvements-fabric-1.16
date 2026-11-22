# Minecart Improvements (Fabric 1.16.5)

Raises the minecart maximum speed on Minecraft 1.16.5 with Fabric, and adds slope and gravity effects: downhill carts gain speed, uphill carts lose it.

**Status: untested.** The code has not been run in game yet. The CI build checks that it compiles. Check the mixin target `AbstractMinecartEntity#getMaxSpeed` in your client before relying on it.

Clean-room implementation: no code was copied from other mods or from decompiled Minecraft source.

## Build
Requires JDK 8+. Run `./gradlew build`, and the jar appears in `build/libs/`.

## Releases
Pushing a tag like `v0.1.0` runs CI, builds the jar, and attaches it to a GitHub release.

## License
MIT
