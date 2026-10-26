# Security policy and current limitations

BlackVault is experimental software, not a production wallet. Never use real funds or enter a valuable seed.

The current ESP32-C3 SuperMini + phone-only setup has no independent display or physical confirmation buttons. A compromised Android app can misrepresent a destination or amount. Therefore this hardware arrangement is suitable only for learning and testnet development until an independent trusted confirmation mechanism is designed and reviewed.

No private-key generation, seed recovery, chain-specific transaction parsing, or signing is implemented in this scaffold. Do not treat device USB detection as authentication. Do not store secrets in source, logs, or app storage.

Production requirements include audited cryptographic libraries, secure entropy, chain-specific transaction parsing, independent transaction confirmation, recovery tests, secure boot/flash encryption provisioning, threat modeling, and independent security review.
