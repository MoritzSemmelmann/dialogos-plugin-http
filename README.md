# DialogOS HTTP Plugin

A plugin for [DialogOS](https://github.com/dialogos-project/dialogos) that enables users to send and receive HTTP requests and responses within a dialogue flow.

## Usage

The easiest way to get started is by using the [distribution repository](https://github.com/MoritzSemmelmann/dialogos-httpPluginDistribution). This version comes with DialogOS and the HTTP plugin pre-bundled, requiring no additional setup.

## Implementation Details

The plugin integrates a new node into the DialogOS editor, allowing you to trigger web queries as part of the conversation logic. Currently, the plugin focuses on JSON-based communication to ensure compatibility with modern REST APIs.


## Functionality

* **HTTP Methods**: Support for `GET`, `POST`, `PUT`, `DELETE`, and `PATCH`.
* **Headers**: Support for custom headers and authentication headers (e.g., Bearer tokens).
* **Parameters**: Configuration of path and query parameters.
* **Body**: Support for JSON payloads in requests.
* **Responses**: Ability to process and handle JSON replies from external services.

## Configuration

You can configure the request details directly within the node editor:

* **Request Settings**: Define the HTTP method, target URL, and optional path or query variable mappings.
* **Body Section**: Toggle between two modes:
    * **Variable Mappings**: Define entries as `jsonKey=slotName`.
    * **Raw JSON**: Paste a complete JSON document directly into the editor.
* **Input Handling**: Fields accept either a selected DialogOS variable or a literal string. Literal text is treated as-is unless wrapped in quotes to force expression parsing.
* **Authentication & Headers**: Supports authentication modes (Bearer, Basic, API key) and custom headers.
* **Security**: Enable the "Trust all SSL certificates" toggle if you need to call systems with self-signed certificates. This skips certificate validation for that node only—use it for testing scenarios where you control the target service.

### Response Handling (SendAndReceiveNode)

For the `SendAndReceiveNode`, you can choose between two response modes:
* **Single**: Stores the full response as a struct or string in a single slot.
* **Multiple**: Allows for specific data extraction using comma-separated mappings (e.g., `user.address.city=citySlot`, `orders[0].id=firstOrderId`).

The DialogOS logs will display the request and response details, as well as any mapping warnings during execution.

## Notes

* Expressions used in path/query/body mappings are evaluated against DialogOS slots before the request runs.
* Response mappings support dotted paths and array indices (orders[1].total). Paths that cannot be resolved are skipped with a warning.
* For troubleshooting, enable DialogOS console logging to inspect HTTP output.