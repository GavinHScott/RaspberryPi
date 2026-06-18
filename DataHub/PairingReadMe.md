# DataHub Device Pairing

DataHub uses asymmetric request signing.

Each trusted device or internal application owns a private key. DataHub stores the matching public key and uses it to verify signed requests. DataHub must never receive or store the device private key.

## First-Time Device Authorization

Use this flow when a new device needs access to DataHub.

1. Enable pairing on DataHub.

   In `DataHubBE/src/main/resources/application.properties`, set:

   ```properties
   datahub.auth.yubikey.enabled=true
   datahub.auth.yubikey.accepted-proof=CHANGE_ME_YUBIKEY_PAIRING_PROOF
   ```

   The `accepted-proof` value is the current lightweight stand-in for real YubiKey verification. Replace this later with OTP, WebAuthn/FIDO2, or PIV verification.

2. On the new device, require YubiKey presence before generating the device key pair.

   The intended device-side behavior is:

   ```text
   ask for YubiKey proof
   verify/take proof
   generate Ed25519 key pair locally
   store private key locally
   send public key to DataHub
   ```

3. Generate an Ed25519 key pair on the new device.

   The private key stays on the device. Export the public key as base64-encoded X.509 bytes. That value becomes `publicKey` in the pairing request.

4. Call DataHub's pairing endpoint.

   ```http
   POST /pair
   Content-Type: application/json
   ```

   Example body:

   ```json
   {
     "clientName": "KitchenPi",
     "keyId": "kitchenpi-main",
     "publicKey": "BASE64_X509_ED25519_PUBLIC_KEY",
     "permissions": ["READ", "WRITE"],
     "yubiKeyProof": "CHANGE_ME_YUBIKEY_PAIRING_PROOF"
   }
   ```

5. DataHub validates the YubiKey proof gate.

   If the proof is accepted, DataHub registers:

   ```text
   keyId -> client name -> public key -> permissions
   ```

   DataHub persists that authorization to:

   ```text
   /home/gavinsco/apps/DataHub/data/authorized-clients.txt
   ```

   DataHub does not receive the private key.

6. Disable pairing when setup is complete unless you are actively pairing devices.

   ```properties
   datahub.auth.yubikey.enabled=false
   ```

## Querying After Pairing

After pairing, the device signs every request with its private key. The YubiKey is not required for normal DataHub requests after the client has been authorized. Only new client authorization through `/pair` requires the YubiKey proof gate.

Required headers:

```http
X-DataHub-Key-Id: kitchenpi-main
X-DataHub-Timestamp: 2026-06-01T17:10:00Z
X-DataHub-Signature: BASE64_SIGNATURE
```

For reads, the device signs this canonical payload:

```text
READ
{timestamp}
{query}
{sorted params}
```

For writes, the device signs this canonical payload:

```text
WRITE
{timestamp}
{command}
{sorted params}
```

DataHub rejects the request if the key is unknown, the signature is invalid, the timestamp is outside the allowed window, or the key does not have the required permission.

## Current YubiKey Status

The pairing endpoint and registration path are wired now, but the YubiKey proof check is deliberately lightweight. It currently compares the submitted proof with `datahub.auth.yubikey.accepted-proof`.

The next security step is to replace that comparison with a real YubiKey verifier. Good options are:

- YubiKey OTP: easiest tap-based pairing flow, usually validated through Yubico's validation service or your own validation server.
- WebAuthn/FIDO2: strongest browser-style ceremony, better if pairing happens through a local web UI.
- PIV: certificate-based, good for admin workstation flows.

Until that verifier is replaced, treat `/pair` as an internal-only setup endpoint and keep `datahub.auth.yubikey.enabled=false` except during controlled local pairing.
