# Security

This Raspberry Pi is a high-trust local-network device. Requests to the Pi should be honored only when they come from the local LAN or the Pi's Tailscale VPN network. Internet-origin traffic should not be accepted directly.

Network location is only the first gate. Local/VPN clients still need the correct application-level authorization.

## Core Model

- SSH/admin access is YubiKey-gated.
- YubiKey-backed SSH/admin authorization lasts for 24 hours, then must be renewed.
- DataHub is the central authorization service for exposed application services.
- DataHub stores authorized client public keys.
- Each client keeps its own private key.
- Client keys can only be created/onboarded through a YubiKey-backed process.
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

The practical way to implement the 24-hour expiry is to use short-lived SSH certificates:

```text
YubiKey proof -> issue SSH user certificate -> certificate valid for 24h -> SSH accepts certificate -> certificate expires
```

The long-lived SSH private key should remain protected by the YubiKey. The short-lived certificate should be useless after 24 hours.

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
2. The client key pair is created through a YubiKey-backed process.
3. The client keeps the private key.
4. DataHub receives the public key, key ID, client name, requested services, and requested permissions.
5. DataHub only adds the client to the whitelist if the onboarding request has valid YubiKey proof.
6. The YubiKey proof for onboarding is valid for at most 24 hours.
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

DataHub is the only external read/write application.

DataHub target posture:

- owns the public key repository for authorized clients
- verifies client signatures for read/write requests
- enforces allowed services and permissions
- rejects non-LAN/non-VPN clients
- is the only external path for write-capable operations
- requires YubiKey-backed onboarding before adding a new client public key

SmartDeviceManager should not be exposed as an external write surface. If Dashboard needs SmartDeviceManager state, it should view that state through Dashboard/DataHub read paths, not by exposing SmartDeviceManager command endpoints directly.

Pi-hole admin surfaces should not be external write surfaces. DNS service exposure may be allowed according to local network needs, but Pi-hole administration should remain LAN/VPN-restricted and separately reviewed.

## YubiKey Required

A YubiKey should be required for:

- starting or renewing SSH/admin access
- issuing a 24-hour SSH/admin credential
- creating/onboarding DataHub client key pairs
- adding clients to the DataHub whitelist
- removing, rotating, or revoking DataHub clients
- changing systemd services, scripts, app configs, and DataHub policy files

A YubiKey is not required for:

- normal authorized DataHub requests after onboarding
- normal requests from a whitelisted client to an approved service
- DNS queries, unless DNS is separately restricted
- Dashboard view-only requests from LAN/VPN clients

## Open Design Questions

- Whether the DataHub client private key should live directly on a YubiKey or be generated by a YubiKey-approved onboarding flow.
- Whether DataHub client authorizations should be indefinite until revoked or require periodic renewal.
- Which component issues the 24-hour SSH certificate after YubiKey proof.
- Where DataHub stores the whitelist and how file permissions/backups are handled.
- How Dashboard obtains read-only data without gaining write capability.
- How DataHub routes or brokers write-capable operations to internal services.
- How revoked keys are distributed and enforced immediately.
