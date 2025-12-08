# JSON Plugin for DialogOS

A JSON plugin for DialogOS.

## Building

To build this plugin as part of the DialogOS project:

```bash
cd path/to/dialogos
.\gradlew :plugins:DialogOS_JsonPlugin:build
```

## Installation

For standalone deployment, copy the built JAR to DialogOS's plugin directory:

```bash
copy build\libs\DialogOS_JsonPlugin.jar %USERPROFILE%\.dialogos\plugins\
```

## Development

This plugin is developed as a Git submodule within the DialogOS project for easier development and testing.
