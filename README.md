# Golden Head

Meteor Client addon for Pit. Minecraft 1.21.11 with Fabric.

Modules are in Meteor's Pit category. Meteor Client has to be installed too, or the game will not load this jar.

## Install

1. Download this repository (Code, then Download ZIP) or download [golden-head-addon.jar](https://github.com/apeirs/golden-head/raw/refs/heads/main/golden-head-addon.jar) on its own.
2. Put `golden-head-addon.jar` in your mods folder, next to Meteor.
   - Official launcher: `.minecraft/mods`
   - Modrinth or Prism: the instance's `mods` folder
3. Launch that instance.

The same jar is also attached to the [Dev Build](https://github.com/apeirs/golden-head/releases/tag/snapshot) release. Each push rebuilds it.

## Building

Java 21. `./gradlew build` writes `build/libs/golden-head-addon-0.1.0.jar`. The jar in the repo root is the one to install.
