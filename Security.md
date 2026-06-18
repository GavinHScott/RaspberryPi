# Security

This Raspberry Pi is a high-trust local-network device. Requests to the Pi should be honored only when they come from the local LAN or the Pi's Tailscale VPN network. Internet-origin traffic should not be accepted directly.

Network location is only the first gate. Local/VPN clients still need the correct application-level authorization.

## Core Model

- SSH/admin access is YubiKey-gated.
- YubiKey-backed SSH/admin authorization lasts for 24 hours, then must be renewed.
- DataHub is the central authorization service for exposed application services.
- DataHub stores authorized client public keys.
- Each client keeps its own private key.
- Every new key pair must be created through a YubiKey physical-presence-backed event.
- Client keys can only be authorized through a YubiKey-backed admin process.
- Normal DataHub access uses signed requests from authorized clients.
- Dashboard is the external-facing view-only UI.
- DataHub is the only external read/write application.

## Network Boundary

The Pi should only honor requests from:

- the local LAN
- the Tailscale VPN network that includes the Pi
- localhost for internal service-to-service calls

Requests from outside those networks should be dropped before they reach application logic.

This applies to:

- SSH
- DataHub
- Dashboard
- Pi-hole admin surfaces
- SmartDeviceManager if it is ever exposed directly
- future HTTP/API services

DNS service exposure may be different from admin/API exposure, but it still needs an explicit network rule.

## SSH Admin Access

All interactive administration should happen through SSH.

Target SSH posture:

- SSH is reachable only from LAN/VPN.
- every SSH session requires a YubiKey-backed credential.
- password-only SSH is not allowed.
- keyboard-interactive SSH is allowed only if it is YubiKey-backed and cannot fall back to a plain password.
- any successful SSH session is admin/root-capable by design.
- sudo should require the `gavinsco` account password after login.
- YubiKey-backed SSH/admin authorization expires after 24 hours.

The 24-hour window means the YubiKey proves human presence on the SSH client, then that proof is exchanged for a short-lived SSH certificate:

```text
YubiKey proof -> issue SSH user certificate -> certificate valid for 24h -> SSH accepts certificate -> certificate expires
```

The long-lived SSH private key should remain protected by the YubiKey. The short-lived certificate should be useless after 24 hours.

A valid YubiKey-backed SSH admin session is the authority for Pi administration, including root-capable work, service changes, and whitelist changes.

Safe rollout rules:

1. Add and test the YubiKey-backed SSH credential.
2. Add and test the 24-hour SSH certificate flow.
3. Keep the current SSH session open while testing a second login.
4. Confirm the intended admin/root workflow works from the YubiKey-backed session.
5. Disable password-only SSH only after the YubiKey flow works.
6. Validate SSH config with `sshd -t` before reloading SSH.

## DataHub Authorization

DataHub is the central whitelist and authorization service for clients that interact with exposed services on the Pi.

DataHub should maintain:

- client name
- key ID
- public key
- allowed services
- permissions
- created time
- revoked status
- optional expiry or rotation metadata

Client onboarding:

1. A new client requests authorization from inside the LAN/VPN boundary.
2. The client creates its key pair through a YubiKey physical-presence-backed event.
3. The client provides DataHub with its public key, key ID, name, requested services, and requested permissions.
4. DataHub does not add the client to the whitelist automatically.
5. A Pi admin approves the request from a valid YubiKey-backed SSH session.
6. The admin's YubiKey-backed SSH/admin proof is valid for at most 24 hours.
7. DataHub stores the authorized public key and policy.

After onboarding, the client does not need a YubiKey for every DataHub request. It signs requests with its private key. DataHub verifies the signature against the stored public key and enforces the client policy.

Normal DataHub requests must include:

```http
X-DataHub-Key-Id
X-DataHub-Timestamp
X-DataHub-Signature
```

DataHub must:

- reject requests from outside LAN/VPN
- reject unknown key IDs
- reject revoked clients
- reject stale timestamps
- verify the request signature against the whitelisted public key
- enforce allowed services
- enforce `READ` or `WRITE` permissions
- reject requests whose SQL or command shape is not allowed

## Exposed Services

Dashboard is the external-facing UI.

Dashboard target posture:

- view/get only
- no write commands
- no direct device-control mutations
- no whitelist administration
- only served to LAN/VPN clients
- reads should come from DataHub or another read-only source of truth

Dashboard client authentication flow:

1. A browser requests Dashboard from the LAN/VPN boundary.
2. Dashboard asks DataHub whether that browser/client identity is whitelisted.
3. DataHub checks its public key repository.
4. If the client is known, DataHub creates a one-time challenge.
5. Dashboard serves the challenge to the browser/client.
6. The browser/client signs the challenge with its private key.
7. Dashboard returns the signed challenge to DataHub.
8. DataHub verifies the signature against the stored public key.
9. Dashboard grants view-only access only after DataHub confirms the proof.

DataHub is the only external read/write application.

DataHub target posture:

- owns the public key repository for authorized clients
- verifies client signatures for read/write requests
- enforces allowed services and permissions
- rejects non-LAN/non-VPN clients
- is the only external path for write-capable operations
- requires YubiKey-backed admin approval before adding a new client public key

SmartDeviceManager should not be exposed as an external write surface. If Dashboard needs SmartDeviceManager state, it should view that state through Dashboard/DataHub read paths, not by exposing SmartDeviceManager command endpoints directly.

Pi-hole admin surfaces should not be external write surfaces. DNS service exposure may be allowed according to local network needs, but Pi-hole administration should remain LAN/VPN-restricted and separately reviewed.

## YubiKey Required

A YubiKey should be required for:

- starting or renewing SSH/admin access
- issuing a 24-hour SSH/admin credential
- creating any new SSH, DataHub client, Dashboard client, or service-client key pair
- approving DataHub client onboarding requests
- adding clients to the DataHub whitelist
- removing, rotating, or revoking DataHub clients
- changing systemd services, scripts, app configs, and DataHub policy files

A YubiKey is not required for:

- normal authorized DataHub requests after onboarding
- normal requests from a whitelisted client to an approved service
- signing a Dashboard challenge from an already-whitelisted browser/client
- DNS queries, unless DNS is separately restricted
- Dashboard view-only requests from LAN/VPN clients

## Open Design Questions

- Whether DataHub client authorizations should be indefinite until revoked or require periodic renewal.
- Which component issues the 24-hour SSH certificate after YubiKey proof.
- Where DataHub stores the whitelist and how file permissions/backups are handled.
- How browser/client private keys are stored safely for Dashboard challenge signing after their YubiKey-backed creation.
- How Dashboard obtains read-only data without gaining write capability.
- How DataHub routes or brokers write-capable operations to internal services.
- How revoked keys are distributed and enforced immediately.
