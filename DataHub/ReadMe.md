# DataHub Current State

DataHub is a Spring Boot service intended to act as a signed, centralized data access layer for local applications and trusted devices.

The module lives at:

```text
/home/gavinsco/apps/DataHub
```

The backend source lives at:

```text
/home/gavinsco/apps/DataHub/DataHubBE
```

The built jar is:

```text
/home/gavinsco/apps/DataHub/target/DataHub-1.0.0.jar
```

## Runtime

DataHub is a Java 21 Spring Boot application.

It is configured to listen on:

```text
http://localhost:9093
```

The current properties file is:

```text
DataHubBE/src/main/resources/application.properties
```

Current configuration includes:

- `server.port=9093`
- placeholder durable storage setting: `datahub.storage.location=TODO_SET_DATAHUB_STORAGE_LOCATION`
- MySQL datasource wiring for `jdbc:mysql://localhost:3306/datahub`
- placeholder MySQL credentials
- placeholder trusted client public key configuration
- persisted paired-client registry at `/home/gavinsco/apps/DataHub/data/authorized-clients.txt`
- YubiKey pairing disabled by default

## API Surface

`GET /health`

Returns:

```text
DataHub OK
```

`POST /resolve`

Runs a signed read-only query. The request body is:

```json
{
  "query": "select * from things where id = :id",
  "params": {
    "id": 1
  }
}
```

The response body is:

```json
{
  "rows": [],
  "rowCount": 0
}
```

`POST /write`

Runs a signed write command. The request body is:

```json
{
  "command": "insert into things(name) values (:name)",
  "params": {
    "name": "example"
  }
}
```

The response body is:

```json
{
  "rowsAffected": 1
}
```

`POST /pair`

Registers a new trusted key through the current pairing flow. Pairing is documented in:

```text
PairingReadMe.md
```

Pairing is currently guarded by a lightweight YubiKey proof comparison and is disabled by default. Once a client is paired, DataHub persists that client's public key and permissions so the client can continue using its own key pair across DataHub restarts.

## Request Signing

`/resolve` and `/write` require Ed25519 request signatures.

Required headers:

```http
X-DataHub-Key-Id: smart-device-manager-key
X-DataHub-Timestamp: 2026-06-04T15:30:00Z
X-DataHub-Signature: BASE64_SIGNATURE
```

The timestamp must be within 5 minutes of the server clock.

Trusted clients are configured with:

```properties
datahub.auth.clients=appName:keyId:base64X509Ed25519PublicKey:READ|WRITE
datahub.auth.clients-file=/home/gavinsco/apps/DataHub/data/authorized-clients.txt
```

Clients can be trusted in two ways:

- statically configured through `datahub.auth.clients`
- dynamically paired through `/pair` and persisted to `datahub.auth.clients-file`

The YubiKey is required only for dynamic client authorization through `/pair`. Normal `/resolve` and `/write` access after authorization uses the client's Ed25519 key pair and does not require a YubiKey touch.

Permissions are:

- `READ`: can call `/resolve`
- `WRITE`: can call both `/resolve` and `/write`

## Query Rules

Read requests are handled by `DataHubResolver.resolveReadOnly`.

Allowed read statements:

- single `SELECT` statements
- single `WITH` statements

Blocked in read mode:

- multi-statement SQL containing `;`
- mutating words such as `insert`, `update`, `delete`, `drop`, `alter`, `create`, and `truncate`

Write requests are handled by `DataHubResolver.resolveWrite`.

Allowed write statements:

- single `INSERT` statements
- single `UPDATE` statements
- single `DELETE` statements
- single `REPLACE` statements

All SQL execution uses Spring's `NamedParameterJdbcTemplate`, so callers should use named parameters instead of string-concatenating values into SQL.

## Current Limitations

DataHub is wired but not production-complete.

Known current limitations:

- durable storage ownership is still marked as TODO
- MySQL database/user/password are placeholders
- the configured trusted client public key is a placeholder
- YubiKey verification is currently a placeholder proof comparison
- there is no installed systemd service for DataHub in the current boot wiring

## Build And Test

From the DataHub module:

```text
cd /home/gavinsco/apps/DataHub
mvn test
```

Current verification:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
```

The latest local test run passed on 2026-06-04.
