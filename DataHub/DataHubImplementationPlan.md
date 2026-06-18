# DataHub Implementation Plan

## Stage 1: YubiKey Admin Registry And Verification

Goal: make DataHub a service that can register trusted YubiKeys from the Pi itself only, then verify whether an incoming admin/onboarding request is backed by one of those registered YubiKeys.

### Step 1.1: Add YubiKey Admin Credential Model

Add a DataHub model for trusted YubiKey-backed admin credentials.

Record fields:

- `adminId`
- `credentialId`
- `publicKey`
- `displayName`
- `createdAt`
- `revoked`

Unit tests:

- creates a valid admin credential record
- rejects blank `adminId`
- rejects blank `credentialId`
- rejects blank `publicKey`
- treats revoked credentials as unusable

### Step 1.2: Add Admin Credential Repository

Create a repository for storing trusted YubiKey admin credentials.

Initial storage can be file-backed or in-memory with a clear interface, but the interface must support:

- add credential
- find by credential ID
- list credentials
- revoke credential

Unit tests:

- stores and retrieves a credential by ID
- returns empty for unknown credential ID
- persists revoked state
- rejects duplicate credential IDs
- does not return revoked credentials as active

### Step 1.3: Restrict YubiKey Onboarding To Pi-Local Requests

Add a guard so new YubiKey admin credentials can only be onboarded from the Pi itself.

Allowed sources:

- `127.0.0.1`
- `::1`
- optionally a Unix-domain/local CLI path if used later

Rejected sources:

- LAN clients
- Tailscale clients
- internet/non-local addresses

Unit tests:

- allows localhost IPv4
- allows localhost IPv6
- rejects LAN address
- rejects Tailscale address
- rejects missing/unknown remote address

### Step 1.4: Add YubiKey Registration Challenge Flow

Add a registration flow:

1. Pi-local admin asks DataHub to start YubiKey registration.
2. DataHub creates a one-time challenge.
3. YubiKey-backed credential signs/proves the challenge.
4. DataHub stores the credential public key if verification succeeds.

Unit tests:

- creates a challenge
- challenge expires
- challenge cannot be reused
- valid proof registers credential
- invalid proof does not register credential
- non-local request cannot start registration

### Step 1.5: Add YubiKey-Backed Request Verification

Add request verification for admin/onboarding requests.

Incoming request includes:

- `adminId`
- `credentialId`
- `timestamp`
- `challengeId` or nonce
- `signature`

DataHub verifies:

- credential exists
- credential is not revoked
- timestamp is fresh
- challenge/nonce is valid
- signature matches stored public key

Unit tests:

- accepts valid signed request
- rejects unknown credential ID
- rejects revoked credential
- rejects stale timestamp
- rejects reused challenge
- rejects invalid signature
- rejects tampered payload

### Step 1.6: Add Health And Diagnostics For Admin Registry

Expose safe diagnostics, not secrets.

Allowed output:

- number of registered admin credentials
- number of revoked credentials
- service status

Never output:

- private keys
- raw signatures
- full challenge secrets

Unit tests:

- health endpoint does not expose secrets
- registry count is correct
- revoked count is correct

## Stage 2: Client Public Key Whitelist

Goal: allow DataHub to store authorized client public keys, but only after approval by a registered YubiKey admin credential.

### Step 2.1: Add Client Credential Model

Fields:

- `clientId`
- `keyId`
- `publicKey`
- `allowedServices`
- `permissions`
- `createdAt`
- `approvedByAdminId`
- `revoked`

Unit tests:

- creates valid client credential
- rejects blank key ID
- rejects blank public key
- rejects empty service list
- rejects empty permissions

### Step 2.2: Add Client Whitelist Repository

Support:

- add client
- find by key ID
- list clients
- revoke client
- rotate client key

Unit tests:

- stores and retrieves client by key ID
- rejects duplicate key ID
- revokes client
- rotated key replaces old key
- unknown key lookup returns empty

### Step 2.3: Add Client Onboarding Request Flow

Flow:

1. Client submits public key and requested access.
2. DataHub creates a pending onboarding request.
3. Registered YubiKey admin approves it.
4. DataHub stores the client in the whitelist.

Unit tests:

- creates pending request
- pending request does not authorize client yet
- valid YubiKey admin approval authorizes client
- invalid admin proof rejects approval
- revoked admin cannot approve
- rejected request never authorizes client

## Stage 3: Signed Client Request Verification

Goal: allow whitelisted clients to call DataHub without YubiKey every time.

### Step 3.1: Define Canonical Request Payload

Canonical payload includes:

- HTTP method
- path or service/action
- timestamp
- body hash
- sorted request params

Unit tests:

- canonical payload is stable
- params are sorted
- body hash changes when body changes
- whitespace differences do not cause accidental mismatch where inappropriate

### Step 3.2: Verify Signed Client Requests

Incoming headers:

- `X-DataHub-Key-Id`
- `X-DataHub-Timestamp`
- `X-DataHub-Signature`

DataHub verifies:

- key ID exists
- client is not revoked
- timestamp is fresh
- signature matches stored public key
- client has service permission

Unit tests:

- accepts valid request
- rejects unknown key
- rejects revoked key
- rejects stale timestamp
- rejects tampered body
- rejects insufficient service permission

## Stage 4: Dashboard Read-Only Access

Goal: Dashboard is external-facing but read/view only, and access is mediated by DataHub.

### Step 4.1: Add Dashboard Challenge Flow

Flow:

1. Browser requests Dashboard.
2. Dashboard asks DataHub for a challenge.
3. Browser/client signs challenge.
4. Dashboard sends signature back to DataHub.
5. DataHub confirms or denies access.

Unit tests:

- creates Dashboard challenge
- valid client signature grants view access
- unknown client is rejected
- revoked client is rejected
- reused challenge is rejected
- expired challenge is rejected

### Step 4.2: Enforce View-Only Dashboard Permissions

Dashboard must not expose write operations.

Unit tests:

- read/view request succeeds for authorized client
- write command is rejected
- device-control mutation is rejected
- whitelist admin request is rejected

## Stage 5: Network Boundary Enforcement

Goal: DataHub, Dashboard, and SSH only honor LAN/VPN/localhost traffic.

### Step 5.1: Add Request Source Guard

Allowed:

- localhost
- configured LAN CIDR
- configured Tailscale CIDR

Rejected:

- anything else

Unit tests:

- allows localhost
- allows LAN IP
- allows Tailscale IP
- rejects public IP
- rejects missing source address

### Step 5.2: Apply Guard To DataHub Admin And Client Paths

Unit tests:

- non-LAN/VPN admin request rejected
- non-LAN/VPN client request rejected
- LAN/VPN request still requires signature
- network allowlist alone never grants access

## Stage 6: Operations And Audit

Goal: make authorization changes observable and recoverable.

### Step 6.1: Add Audit Events

Log:

- admin YubiKey registration
- client onboarding request
- client approval
- client revocation
- failed signature verification
- failed network boundary check

Unit tests:

- approval writes audit event
- revocation writes audit event
- failed verification writes audit event
- audit event does not include private keys or raw secrets

### Step 6.2: Add Revocation And Rotation Commands

Support Pi-local admin operations:

- revoke YubiKey admin credential
- revoke client key
- rotate client key
- list active clients
- list revoked clients

Unit tests:

- revoked admin cannot approve clients
- revoked client cannot make requests
- rotated client old key fails
- rotated client new key succeeds
