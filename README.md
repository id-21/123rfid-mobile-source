# Daga RFID order fulfilment

Zebra's 123RFID Mobile demo app (v2.0.5.297, RFID API3 SDK) plus an order fulfilment flow, kept as a private working copy.

- **Order flow** (new): `app/src/main/java/com/zebra/demo/order/`. Fetch orders, claim, pick by EPC, submit, offline outbox.
- **Mac setup and test steps**: [docs/MAC_SETUP.md](docs/MAC_SETUP.md)
- **Backend contract**: [docs/API_CONTRACT.md](docs/API_CONTRACT.md); a runnable mock is in `tools/mock-server/`
- **How the Zebra code works and where the reader hooks in**: [docs/ORDER_INTEGRATION_PLAN.md](docs/ORDER_INTEGRATION_PLAN.md)

Notes
- Package: `com.dagawallpapers.fulfilment`, minSdk 29, compileSdk 36. SDK library: `app/libs/rfidapi3lib-2.0.5.297.aar`.
- Zebra's APK, signing keystore and SonarQube token are not included (the token was redacted).
- Zebra's code is under Zebra's licence terms. Keep this repo private.
