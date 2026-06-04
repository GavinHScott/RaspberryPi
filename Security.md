# Security

This Raspberry Pi is a high-trust device on the local network. If an attacker gets an SSH session on the Pi, they should be treated as having a serious path into the rest of the network.

The Pi can see local services, trusted device traffic, application source, service configuration, logs, and any credentials or private keys stored on disk. For that reason, SSH access to the Pi and inbound access to DataHub are the two main security boundaries.

## Current Pi SSH State

As of 2026-06-04, the effective SSH configuration allows password authentication:

```text
PasswordAuthentication yes
PubkeyAuthentication yes
KbdInteractiveAuthentication no
UsePAM yes
AuthenticationMethods any
```

There is currently no `/home/gavinsco/.ssh/authorized_keys` file. That means the Pi is not yet ready to safely enforce SSH public-key-only login, because doing so before installing a working key would lock out the account.

The `gavinsco` user is in the `sudo` group and currently has passwordless sudo:

```text
(ALL : ALL) ALL
(ALL) NOPASSWD: ALL
```

That means the current security boundary is mostly the SSH login itself. The desired target is stronger: every SSH path, including direct root SSH, must be YubiKey-gated. For non-root sessions, sudo/root actions should still require the `gavinsco` account password inside the session.

## Required SSH Direction

The desired SSH posture is:

- every new SSH session must require a YubiKey-backed SSH credential
- `gavinsco` SSH login must be allowed with YubiKey protection
- direct root SSH may be allowed, but only with YubiKey protection
- password SSH login must be disabled
- keyboard-interactive SSH may be enabled only when it is YubiKey-backed and cannot fall back to a plain password
- root-level administration happens through `gavinsco` and password-required sudo after login
- passwordless sudo must be removed
- the current SSH session must remain open while testing a new login

For remote SSH, the preferred YubiKey model is a client-side OpenSSH FIDO/security-key credential. The YubiKey is plugged into the device initiating SSH, not into the Pi.

Accepted public key types should start with one of:

```text
sk-ssh-ed25519@openssh.com
sk-ecdsa-sha2-nistp256@openssh.com
```

The planned target SSH settings for FIDO public-key-only SSH are:

```text
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
AuthenticationMethods publickey
PermitRootLogin prohibit-password
```

If keyboard-interactive SSH is enabled, it must be configured as a YubiKey-backed PAM challenge or an additional factor after a YubiKey-backed public key, never as an alternative password path. In that mode, the intended shape is:

```text
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication yes
AuthenticationMethods publickey,keyboard-interactive
PermitRootLogin yes
```

In either mode, both `/home/gavinsco/.ssh/authorized_keys` and root's authorized keys must contain only approved YubiKey/FIDO-backed `sk-*` public keys for interactive admin access. Direct root SSH should not accept ordinary `ssh-ed25519`, RSA, ECDSA, password, or password-only keyboard-interactive login.

The planned target sudo posture is:

```text
gavinsco ALL=(ALL:ALL) ALL
```

There should be no broad `NOPASSWD: ALL` rule for `gavinsco`. Any narrow `NOPASSWD` exception should be explicit, justified, and documented before being kept.

Do not apply those SSH settings until `/home/gavinsco/.ssh/authorized_keys` and, if direct root SSH is required, root's authorized keys contain tested YubiKey/FIDO-backed `sk-*` public keys.

The rollout order is:

1. Create a YubiKey-backed SSH key on the client machine.
2. Add the generated public key to `/home/gavinsco/.ssh/authorized_keys`.
3. If direct root SSH is required, add the same approved public key to root's authorized keys.
4. Keep the current SSH session open.
5. Test a second SSH login as `gavinsco` and confirm it requires the YubiKey.
6. If direct root SSH is required, test a separate root SSH login and confirm it requires the YubiKey.
7. Only after those tests succeed, disable password SSH login.
8. If keyboard-interactive is enabled, confirm it is YubiKey-backed and not password-only.
9. Remove broad passwordless sudo for `gavinsco` so sudo asks for the account password.
10. Reload SSH only after `sshd -t` validates the config.
11. Confirm fresh YubiKey-backed SSH logins still work before closing the original session.
12. Confirm `sudo -v` asks for the `gavinsco` password and then grants root-capable administration.

## Creating The YubiKey SSH Key

Create the key on the client machine used to SSH into the Pi. The private key must remain on that client machine.

Recommended OpenSSH key type:

```text
ssh-keygen -t ed25519-sk -O resident -O verify-required -C "gavinpione-yubikey" -f ~/.ssh/gavinpione_yubikey
```

Copy the generated `.pub` file into:

```text
/home/gavinsco/.ssh/authorized_keys
```

If direct root SSH is required, also copy the same approved public key into root's authorized keys.

The public key line should begin with:

```text
sk-ssh-ed25519@openssh.com
```

## DataHub Role

DataHub is intended to become the controlled data access layer for applications and trusted devices.

Current module:

```text
/home/gavinsco/apps/DataHub
```

Current service port:

```text
9093
```

Current DataHub endpoints:

- `GET /health`
- `POST /pair`
- `POST /resolve`
- `POST /write`

`/resolve` and `/write` require Ed25519 request signatures using:

```http
X-DataHub-Key-Id
X-DataHub-Timestamp
X-DataHub-Signature
```

The timestamp must be within 5 minutes of the server clock. Trusted client keys are configured with:

```properties
datahub.auth.clients=appName:keyId:base64X509Ed25519PublicKey:READ|WRITE
datahub.auth.clients-file=/home/gavinsco/apps/DataHub/data/authorized-clients.txt
```

DataHub has a different YubiKey model from SSH. SSH requires the YubiKey every time a session starts. DataHub requires a YubiKey only when authorizing a new client through `/pair`. Once the client is authorized, that client can interact with DataHub indefinitely using its own Ed25519 key pair, subject to signature, timestamp, and permission checks.

Current permission model:

- `READ`: can call `/resolve`
- `WRITE`: can call both `/resolve` and `/write`

## DataHub Access Protocol

Incoming DataHub connections should be treated as untrusted until all checks pass.

Required protocol:

1. Authorize new clients through `/pair` only when the YubiKey proof gate passes.
2. Persist the authorized client's public key and permissions.
3. Identify normal callers by `X-DataHub-Key-Id`.
4. Reject unknown key IDs.
5. Reject missing or stale timestamps.
6. Reconstruct the canonical payload server-side.
7. Verify the Ed25519 signature against the registered public key.
8. Enforce `READ` or `WRITE` permission.
9. Validate query shape before touching the database.
10. Use named parameters for all SQL values.

Current read rules:

- allows single `SELECT` statements
- allows single `WITH` statements
- blocks multi-statement SQL containing `;`
- blocks mutating words in read mode

Current write rules:

- allows single `INSERT`, `UPDATE`, `DELETE`, or `REPLACE` statements
- blocks multi-statement SQL containing `;`

## DataHub Current Risks

DataHub is wired and tested, but not production-complete.

Known risks:

- MySQL database credentials are placeholders
- trusted client public keys are placeholders
- durable storage location is still a TODO
- `/pair` uses a placeholder proof comparison rather than real YubiKey verification
- paired keys are persisted, so the authorized-clients file must be protected as security-sensitive configuration
- DataHub is not currently installed as a systemd service
- LAN and VPN exposure of port `9093` makes DataHub a direct attack surface unless request signing, key management, and database permissions are hardened

Until these are fixed, DataHub should only be reachable on the minimum LAN/VPN paths needed, and network location must not be treated as authentication.

## Pi Compromise Impact

If someone manages to SSH into the Pi, assume they may be able to:

- read application source and local configuration
- inspect logs and scheduled scripts
- tamper with SmartDeviceManager behaviour
- alter DataHub access controls
- use direct root SSH if root's YubiKey-backed key or client device is compromised
- attempt sudo escalation from `gavinsco`; the target posture requires the `gavinsco` password for root-level control
- pivot to local network services
- access local DNS/Pi-hole behaviour
- capture or misuse credentials stored on disk

This makes YubiKey-protected SSH the first control, not a nice-to-have. Direct root SSH is acceptable only when it is YubiKey-gated. Sudo from `gavinsco` should require the `gavinsco` password so non-root sessions still have a second gate before root actions.

## Recommended Next Controls

Immediate controls:

- install a YubiKey-backed SSH public key for `gavinsco`
- install a YubiKey-backed SSH public key for root if direct root SSH is required
- verify a second SSH login succeeds with the YubiKey before closing the current session
- verify direct root SSH requires the YubiKey if it is enabled
- then disable password SSH login
- only enable keyboard-interactive SSH when it is YubiKey-backed and cannot fall back to a plain password
- remove broad passwordless sudo so root actions require the `gavinsco` password
- keep SSH reachable only through Tailscale/VPN where possible

DataHub controls:

- replace placeholder DataHub client keys
- replace placeholder MySQL credentials
- replace placeholder YubiKey proof comparison with real OTP, WebAuthn/FIDO2, or PIV verification
- protect `/home/gavinsco/apps/DataHub/data/authorized-clients.txt` as a sensitive authorization registry
- add a DataHub systemd service only after credentials and network binding are decided
- bind DataHub to the narrowest viable interface
- log rejected auth attempts without logging secrets or raw signatures

Operational controls:

- keep `/home/gavinsco/scripts` free of logs and runtime clutter
- keep script logs in `/home/gavinsco/ScriptLogs`
- review systemd units before enabling new services
- treat any unexpected SSH login as a network security incident
