# Minecart Improvements (Fabric 1.16.5)

Minecart changes for Minecraft 1.16.5 with Fabric: slope and gravity effects, faster carts on rail, rail jumps sized to the gap, a super powered rail, and smoothed acceleration.

Super Powered Rail recipe: powered rails, iron blocks, and a minecart in a 3x3 pattern (PIP / IMI / PIP), yields 4 rails. The rail needs redstone power like a vanilla powered rail (power passes between adjacent super rails; straight and sloped only, no curves). Powered, it boosts carts; unpowered, it brakes them to a stop. Its texture is derived from the vanilla rail texture (cyan alloy rails, darker ties, glowing studs).

Load protection (0.5.7): carts never read blocks in unloaded chunks (so a flying cart cannot force chunk loads), flight stops at the edge of the loaded area, no more than 32 new launches start per server tick, and debug logging is off by default. Start the game with `-Dminecartimprovements.debug=true` to turn on rate-limited debug lines.

v0.2.1 removed the speed-cap mixin, which targeted a method that doesn't exist in 1.16.5 and crashed the game at startup.

**Status: untested.** The code has not been run in game yet. The CI build checks that it compiles. Vanilla limits cart movement to 0.4 blocks per tick; the mod raises that limit (mixin on `getMaxOffRailSpeed`) only while a cart is moving along a rail.

Clean-room implementation: no code was copied from other mods or from decompiled Minecraft source.

## Build
Requires JDK 8+. Run `./gradlew build`, and the jar appears in `build/libs/`.

## Releases
Pushing a tag like `v0.1.0` runs CI, builds the jar, and attaches it to a GitHub release.

## License
MIT
