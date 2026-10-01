# Universal Local Agent

USMAN AND UMAR's universal agent uses **Needle 3 as its only local decision and tool-calling engine**. Connector implementations do not contain planning intelligence; they expose schemas and execute authorized requests.

## Execution flow

1. The user provides a goal.
2. `UniversalToolRegistry` exposes qualified connector tools to Needle 3.
3. Needle 3 selects a registered tool and structured JSON arguments locally.
4. The registry resolves the connector and applies `ConnectorApprovalGate`.
5. The connector resolves a credential reference through Android Keystore-backed storage. The secret is never included in the Needle prompt, schema, event, or execution log.
6. The connector executes the request and returns a bounded result.
7. The redacted result is fed back to Needle 3 for the next step.
8. The loop stops on completion, an authorization requirement, a connector failure, or the step limit.

## Connector families

The registry is provider-neutral. It supports:

- MCP JSON-RPC servers through `McpConnector`
- REST APIs and webhooks through `HttpConnector`
- OAuth-backed services through the same transport plus a secure credential reference
- CLI tools through `CliConnector` with direct `ProcessBuilder` execution and an explicit executable/tool allowlist
- Developer, cloud, hosting, and other platforms through HTTP or MCP manifests

To add a service, register a `UniversalConnector` and its `ConnectorTool` schemas. The Needle runtime and orchestrator do not change.

## Security

- Credential values remain behind `ProviderCredentialStore` and Android Keystore.
- Needle receives credential IDs, never credential values.
- Read-only and reversible actions can run unattended.
- Sensitive and destructive connector actions return `User approval required` until an app-controlled approval gate allows them.
- Execution logs contain connector/tool IDs, risk, status, and errors only; they do not contain arguments or secrets.
- CLI connectors never invoke a shell and accept only pre-allowlisted executable arguments.
- HTTP responses and prompt-fed connector results are bounded and marked as untrusted data.

## Current boundary

The universal registry and orchestration path are implemented and unit-tested. A production connector-management UI can register manifests and initiate OAuth takeover flows without changing the core agent. Physical Needle model execution still requires an ARM Android device or emulator with the model installed.

## Cloudflare

Cloudflare is included as a manifest-only cloud connector. It exposes read-only deployment listing and sensitive deployment tools, but it starts as **Not Connected** when no credential exists. The Connect Cloudflare flow stores a user-supplied API token only through the existing Keystore-backed credential store; it does not call Cloudflare during connection and does not claim that a deployment or account lookup succeeded. Deployment remains approval-gated.
