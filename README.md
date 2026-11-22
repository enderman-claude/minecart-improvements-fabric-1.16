# Minecart Improvements (Fabric 1.16.5)

Minecart changes for Minecraft 1.16.5 with Fabric: slope and gravity effects, faster carts on rail, rail jumps sized to the gap, a super powered rail, and smoothed acceleration.

Super Powered Rail recipe: 4 powered rails, redstone blocks, and gold blocks in a 3x3 pattern (PRP / RGR / PRP), yields 4 rails.

v0.2.1 removed the speed-cap mixin, which targeted a method that doesn't exist in 1.16.5 and crashed the game at startup.

**Status: untested.** The code has not been run in game yet. The CI build checks that it compiles. Check the mixin target `AbstractMinecartEntity#getMaxSpeed` in your client before relying on it.

Clean-room implementation: no code was copied from other mods or from decompiled Minecraft source.

## Build
Requires JDK 8+. Run `./gradlew build`, and the jar appears in `build/libs/`.

## Releases
Pushing a tag like `v0.1.0` runs CI, builds the jar, and attaches it to a GitHub release.

## License
MIT
