# BlackVault — Multichain Hardware Wallet

**Development scaffold only. Not audited; never use with real funds.**

## Architecture
- Android app: portfolio UI, chain-specific address/balance/fee lookup, transaction construction and broadcast.
- ESP32-C3: offline hardware signer only. It must own private keys, independently parse supported transaction formats, display critical transaction details on the OLED, and require physical button confirmation before signing.
- Transport: USB serial protocol. The app is an untrusted host; never trust host-supplied display strings or sign opaque bytes.
- Chain support is modular. Planned chain adapters are not yet implemented; this repository currently has no key generation, derivation, address generation, or signing implementation.

## Hardware
ESP32-C3 SuperMini. Proposed prototype wiring (verify pinout and boot-strapping constraints before wiring): SSD1306 I2C OLED SDA GPIO4/SCL GPIO5; confirm GPIO0; cancel GPIO1. Do not wire hardware before checking the exact board variant. ESP32-C3 SuperMini USB behavior depends on its USB Serial/JTAG implementation; validate Android USB host and CDC/serial compatibility on the actual board.

## Build
Firmware requires ESP-IDF 5.x installed and exported:
```sh
cd firmware
idf.py set-target esp32c3
idf.py build
```
Android: open `android/` in Android Studio or use Gradle in a configured environment.

## Security status
No real wallet yet. Missing: audited cryptographic dependencies, entropy/seed generation, BIP39/BIP32 derivation where applicable, per-chain key derivation, chain-specific transaction parsing and signing, OLED transaction display, button-confirmation state machine, USB serial transport/handshake, recovery tests, secure boot/flash encryption provisioning, threat model, and independent security review. Do not enter a seed or use real funds.
