# DialogOS JSON Plugin

Ein Plugin für DialogOS zur Verarbeitung von JSON-Daten.

## Voraussetzungen

- Java 17 (LTS) oder höher für den Build
- DialogOS 2.1.5-SNAPSHOT

## Build

Das Plugin kann als separater Build kompiliert werden:

```powershell
# Mit Java 17
$Env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
.\gradlew.bat build
```

Oder verwende das Build-Skript:

```powershell
.\build.ps1
```

## Installation

### Automatisch

Führe aus, um das Plugin automatisch nach DialogOS zu kopieren:

```powershell
.\gradlew.bat copyPluginToDialogOS
```

### Manuell

Kopiere die generierte JAR-Datei aus `build/libs/dialogos-plugin-json-1.0.0.jar` in das DialogOS-Plugin-Verzeichnis.

## Verwendung

Nach der Installation wird das Plugin automatisch von DialogOS über den Java ServiceLoader erkannt.

## Struktur

- `src/main/java/com/clt/dialogos/jsonplugin/` - Plugin-Quellcode
  - `JsonPlugin.java` - Hauptplugin-Klasse
  - `JsonNode.java` - JSON-Node-Implementation
  - `JsonPluginSettings.java` - Plugin-Einstellungen
  - `JsonPluginRuntime.java` - Runtime-Funktionalität
- `src/main/resources/META-INF/services/` - ServiceLoader-Konfiguration

## Dependencies

Das Plugin bündelt folgende externe Dependencies:
- org.json:json:20231013 - JSON-Verarbeitung

DialogOS-Dependencies werden als `compileOnly` referenziert und nicht gebündelt.
