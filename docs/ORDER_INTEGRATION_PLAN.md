# Order-verification integration: analysis of 123RFID Mobile 2.0.5.297

Package root: `app/src/main/java/com/zebra/demo` (213 Java files, about 62k lines). Everything below is read from the source in this repo.

## 1. How the app works

**Reader connection**
- `rfidreader/rfid/ConnectionController.AutoConnectDevice()` picks the reader from `RFIDController.readers.GetAvailableRFIDReaderList()`, calls `connect()`, then registers an event listener with `Events.addEventsListener(...)`.
- `updateReaderConnection()` turns on the events it needs: tag read, handheld trigger, battery, inventory start/stop, etc. It also loads the active profile (antenna power, link profile, session) onto the reader.
- The connected reader is held in static fields: `RFIDController.mConnectedReader`, `mConnectedDevice`, `mIsInventoryRunning`.

**Inventory start/stop**
- `rfidreader/rfid/InventoryController.performInventory()` calls `mConnectedReader.Actions.Inventory.perform(null, null, antennaInfo)`. `stopInventory()` calls `Actions.Inventory.stop()`.
- Variants exist for reading a memory bank during inventory (`inventoryWithMemoryBank`).
- The physical trigger arrives as a `HANDHELD_TRIGGER_EVENT` status event, handled in `RFIDBaseActivity.notificationFromGenericReader()` (around line 2758) and forwarded to the current fragment's `triggerPressEventRecieved()` / `triggerReleaseEventRecieved()`.

**Tag-read pipeline (the part you will hook into)**
1. The SDK fires `eventReadNotify` in `RFIDBaseActivity.EventHandler` (around line 3173).
2. It pulls tags with `mConnectedReader.Actions.getReadTags(100)`, an array of `TagData`.
3. For each tag it runs `ResponseHandlerTask` (normal mode) or `MatchingTagsResponseHandlerTask` (tag-list match mode).
4. `ResponseHandlerTask` dedupes by `tagData.getTagID()` (the EPC hex string) into the static `Application.inventoryList` (a `TreeMap` of EPC to index) and `Application.tagsReadInventory` (a list of `InventoryListItem` holding EPC, count, RSSI, etc.). It also updates `TOTAL_TAGS` and `UNIQUE_TAGS`.
5. The result is pushed to the visible fragment through `ResponseHandlerInterfaces.ResponseTagHandler.handleTagResponse(item, isNew)`.

**Existing expected-list feature: Tag List Match Mode**
- The inventory screen can load a CSV of expected tags (`MatchModeFileLoader`, populates `Application.tagListMap`).
- Scanned tags are then marked `MATCH`, `UNKNOWN` (not in the list) or missing (`MatchingTagsResponseHandlerTask`, around line 3605 of `RFIDBaseActivity.java`).
- This is already close to "verify scanned items against an order".

## 2. Where an order flow fits

**Fastest test with no code changes**
- Export the order's expected EPCs from your order system as a CSV.
- Load it via the inventory screen's match-mode button, scan, and see matched, unknown and missing tags.
- Use this to check that your tag-to-SKU mapping and read quality work before writing anything.

**Real integration: new order fragment, reuse the existing pipeline**
- Add an `OrderVerifyFragment` that implements `ResponseTagHandler`, like `RFIDInventoryFragment` does.
- The existing `ResponseHandlerTask` keeps feeding `tagsReadInventory`. In `handleTagResponse()`, look up the EPC in an order map (EPC to SKU), tick off lines, flag extras, update counts.
- Reuse `InventoryController.performInventory()/stopInventory()` and the trigger callbacks for start/stop. Do not rewrite these.
- On "Complete order", POST results to your backend (INTERNET permission already present).

**Better long-term: lift the core out of the demo**
- Copy only: connection (`ConnectionController`, `reader_connection/`), `InventoryController`, `RFIDController` essentials, the event handler, and `InventoryListItem`.
- Drop the rest. The result is a small app that depends on `rfidapi3lib-2.0.5.297.aar`.

## 3. Keep / strip / modify

**Keep (core)**
- `rfidreader/rfid/*` (RFIDController, ConnectionController, InventoryController, RfidListeners, UpdateUIListener)
- `rfidreader/reader_connection/*` (discover, pair, connect)
- `rfidreader/inventory/InventoryListItem.java`, `ModifiedInventoryAdapter.java`
- `rfidreader/home/RFIDBaseActivity.java` event handler and `ResponseHandlerTask` (extract rather than keep the whole 3.9k-line file)
- `application/Application.java` shared state
- `rfidreader/settings/` profile and antenna power parts, regulatory settings (needed for India's band)

**Strip (not needed for ordering)**
- `scanner/*` (barcode scanner module, firmware update UI, symbologies, scale)
- `rfidreader/access_operations/*` (write, lock, kill)
- `rfidreader/locate_tag/*` (single and multi-tag locate)
- `rfidreader/settings/certificates`, `endpoint`, `adminlogin`
- `wifi/*`, `rfidreader/data_export/*`, NFC pairing, `rfidreader/manager/*` (factory reset)

**Modify / add**
- New `order/` package: `OrderRepository` (API calls), `OrderVerifyFragment`, `OrderLine` model, EPC-to-SKU lookup.
- Decide the unit of tagging (roll or box) and the EPC-to-SKU mapping. This is a data question, not an SDK one.
- `app/build.gradle`: change `applicationId`, remove the Sonar block and the signing config tied to Zebra's keystore, add your own.
- Manifest: remove permissions you don't use after stripping (NFC `required=true` feature, location, Wi-Fi network settings).

## 4. Risks and gotchas

- **Static global state.** Reader, inventory list and counters are all `static`. Fine for one screen, but an order flow must call `RFIDController.getInstance().clearAllInventoryData()` between orders or counts will bleed over.
- **`RFIDBaseActivity` is a god class.** The event handler, trigger handling, tag processing and UI all live in one 3.9k-line file with nested AsyncTasks. Extract rather than extend.
- **Deprecated patterns.** `AsyncTask` is deprecated; it works for now but plan to replace it.
- **Same tag read many times.** Dedupe is per EPC; make order ticking idempotent (count by unique EPC, not by read count).
- **Missing build files.** The project has no root `settings.gradle` or root `build.gradle`, and `app/build.gradle` references a keystore and env vars (`KEY_STORE_PWD`, `VERSION_CODE`). Needs a Gradle wrapper and root files before it builds.
- **Library hosting.** The SDK is the local `app/libs/rfidapi3lib-2.0.5.297.aar` (min SDK 29, compile SDK 36). Check Zebra's license terms before redistributing.

## 5. Suggested next steps

1. Do the CSV match-mode test on a real reader to validate tag reads and mapping.
2. Add Gradle root files and build the unmodified app in Android Studio.
3. Build `OrderVerifyFragment` against the existing pipeline (smallest change).
4. Once working, extract the core into a slim app and drop the unused modules.

## Open questions

- Which reader: RFD40 / RFD8500 sled on a phone, or an MC3300xR handheld?
- Is the order app native Android, PWA or Flutter? This decides whether the SDK goes into it directly or a separate scanner app talks to it.
- What is tagged (roll, box, carton), and where does EPC-to-SKU live today?
