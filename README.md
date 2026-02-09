# DialogOS JSON Plugin

A DialogOS extension that adds dedicated nodes for sending HTTP requests and mapping JSON responses back into DialogOS variables.

## Key Features
- **SendNode** – Build a JSON payload from DialogOS slots, configure path/query variables, authentication and custom headers, and send HTTP requests (GET/POST/PUT/DELETE/PATCH). Switch between slot-mapping mode and raw JSON input for the request body.
- **SendAndReceiveNode** – Everything from `SendNode` plus flexible response handling:
  - Store the entire JSON response either as a struct or as a raw string.
  - Map multiple JSON fields (including nested objects/arrays via `path.to.field` or `array[0].value`) directly into separate DialogOS variables.
- Expression support: every variable reference can be an expression evaluated by the DialogOS expression engine before the request is sent.

## Requirements
- Java 17 (LTS) or newer for building the plugin.
- DialogOS `2.1.5-SNAPSHOT` (or compatible snapshot that exposes the plugin API used here).

## Dependencies
The plugin shades its runtime dependencies into the JAR. The main external library is:
- `org.json:json:20231013` for JSON parsing/serialization.

Gradle also declares a `compileOnly` dependency on `com.github.dialogos-project:dialogos:2.1.4` to compile against the DialogOS APIs; the runtime copy is provided by DialogOS itself.

## Project Structure
- `src/main/java/com/clt/dialogos/jsonplugin/`
  - `JsonPlugin.java` – Plugin entry point that registers the nodes.
  - `SendNode.java` – Node for sending JSON without response mapping.
  - `SendAndReceiveNode.java` – Node for sending requests and writing JSON responses into variables.
  - `JsonConverter.java` – Helper utilities for converting between DialogOS values and JSON, evaluating expressions, and mapping response JSON.
  - `HttpHandler.java` – Shared HTTP client that performs the actual requests.
  - `JsonPluginSettings.java` / `JsonPluginRuntime.java` – Settings and runtime wiring.
- `src/main/resources/META-INF/services/` – ServiceLoader entries so DialogOS can discover the plugin.

## Building
```bash
cd dialogos-plugin-json
./gradlew clean jar
```
The resulting artifact `build/libs/dialogos-plugin-json-<version>.jar` already contains shaded dependencies and can be dropped into your DialogOS `plugins/` folder.

## Usage Overview
1. Copy the built JAR into `DialogOS/plugins/` and restart DialogOS. The nodes “Send JSON” and “Send and Receive JSON” become available in the node palette.
2. Configure request details inside the node editor:
  - HTTP method, URL, and optional path/query variable mappings.
  - Body section: toggle between “Use variable mappings” (each entry as `jsonKey=slotName`) and “Enter raw JSON” to paste a full JSON document directly.
  - Fields accept either a selected DialogOS variable or a literal string typed directly into the editor; literal text is treated as-is unless you wrap it in quotes to force expression parsing.
  - Authentication mode (`Bearer`, `Basic`, API key) and custom headers.
3. For `SendAndReceiveNode`, choose **Response Mode**:
   - *Single*: store full response as struct or string in one slot.
   - *Multiple*: supply comma-separated mappings like `user.address.city=citySlot, orders[0].id=firstOrderId` to extract nested values.
4. Run your DialogOS scenario; logs show request/response details and any mapping warnings.

## Notes
- Expressions used in path/query/body mappings are evaluated against DialogOS slots before the request runs.
- Response mappings support dotted paths and array indices (`orders[1].total`). Paths that cannot be resolved are skipped with a warning.
- For troubleshooting, enable DialogOS console logging to inspect HTTP output.

## License
This project inherits the DialogOS project licensing (see parent repository for details).

