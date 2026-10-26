# BlackVault protocol v0 (development scaffold)

Transport target: UTF-8 JSON lines terminated by LF, maximum 512 bytes. This describes a future application protocol; actual USB serial transport is not yet implemented. Current firmware supports only identity/status over its configured console and does not perform wallet operations.

Request: `{"v":0,"id":1,"cmd":"hello"}`

Example response:
`{"v":0,"id":1,"ok":true,"device":"BlackVault","fw":"0.1.0-dev","role":"offline_signer","chains":[],"capabilities":["identity","status"]}`

Initial commands planned:
- `hello`: device identity, protocol version, firmware version and implemented chain capabilities.
- `status`: safe device status, no secrets.
- Future `get_address`: only for an explicitly supported chain/path; return public address data only.
- Future `sign_transaction`: accept a strictly bounded, chain-specific structured transaction/PSBT, parse and validate it on-device, show destination/amount/fees on the OLED, require physical confirmation, then return only the signature or signed payload.

Security rules:
- The Android host is untrusted. Never sign opaque bytes or trust host-provided transaction descriptions.
- Do not return seed/private-key material over USB.
- Reject unknown chain IDs, unsupported transaction types, malformed input, oversized messages, replayed request IDs and unsupported derivation paths.
- Require physical confirmation on the device after displaying the actual parsed destination, amount, network and fee.
- Chain support must be advertised only after its parser, signer, test vectors, and tests are implemented.
