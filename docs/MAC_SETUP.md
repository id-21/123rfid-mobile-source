# Build and test on your Mac (Apple silicon) + Android phone

The order flow (fetch order, pick, submit) works without the RFID reader. Scans are simulated with buttons or typed EPCs.

## 1. Install the tools

1. Install **Android Studio** (Apple silicon build) from https://developer.android.com/studio, or `brew install --cask android-studio`.
2. Open it once and finish the setup wizard (it installs the Android SDK and accepts the licences).
3. Optional, only for the test server: **Node.js** (`brew install node`).

## 2. Get the code and build

1. Clone: `git clone https://github.com/id-21/123rfid-mobile-source.git` (private repo: sign in with `gh auth login` or GitHub Desktop).
2. Android Studio: **Open** the cloned folder (the one containing `settings.gradle`).
3. Wait for the Gradle sync. The first one downloads a lot and takes several minutes.
   - If it offers to upgrade the Android Gradle Plugin, you can accept.
   - If sync or the build fails, copy the red error text from the **Build** panel and send it over. The Android code was written without being able to compile it in my environment, so a first-build error or two is possible.

## 3. Connect your phone

1. Phone: Settings, About phone, tap **Build number** 7 times. Then Settings, Developer options, turn on **USB debugging**.
2. Plug into the Mac, tap **Allow** on the phone's prompt. The phone appears in Android Studio's device dropdown.
3. Run configuration: **Edit Configurations, app, Launch Options: Specified Activity**, choose `com.zebra.demo.order.OrderListActivity`. (The app has two launcher icons: "Daga Orders", which is the new flow, and Zebra's original screen.)
4. Press **Run** (the green triangle). The app installs as **Daga Orders**.

## 4. What to test

**A. Built-in test data (no server)**
1. The list shows orders 1001 to 1003. Tap **#1001**. It is claimed and the pick screen opens.
2. Press **TEST: next roll** repeatedly. Rolls turn green and the counter climbs. **TEST: wrong roll** adds a red "Not in this order" entry. You can also type or paste an EPC and press Add.
3. **Submit order** is enabled only when every roll is matched. After submitting, the order disappears from the list.
4. Menu, **Add test order** simulates a new order arriving.
5. Close the app in the middle of a pick and reopen it: progress is restored.

**B. Against the mock server (tests fetching, claiming and syncing)**
1. On the Mac: `node tools/mock-server/server.js`. It prints a phone URL such as `http://192.168.1.20:8080`.
2. Phone on the **same Wi-Fi**: menu, **Server settings**, enter that URL, Save.
3. Add an order while the app is open: `curl -X POST http://localhost:8080/admin/orders`. It shows up within 20 seconds ("1 new order").
4. Pick and submit it, then check what the server received: `curl http://localhost:8080/admin/completions`.
5. Offline test: stop the server, finish a pick, submit. The app says it saved the order and will upload. Start the server again and it uploads on the next refresh.

## 5. Debugging basics

- **Logcat** (bottom panel): filter by your app. Crashes show a red stack trace.
- A **breakpoint** (click the gutter) plus the **Debug** button (bug icon) pauses the app on the phone.
- Useful starting points: `OrderService` (all network work), `OrderFulfillmentActivity.onTagScanned` (every scan goes through it).

## 6. When the reader arrives

`onTagScanned(epc)` is the single entry point. The reader's tag-read event only has to call it. The existing Zebra code that receives tags is `ResponseHandlerTask` in `RFIDBaseActivity` (see `ORDER_INTEGRATION_PLAN.md`). The reader connection, antenna power and India regulatory setup can be done once through the original Zebra screens (menu, **RFID reader tools**).
