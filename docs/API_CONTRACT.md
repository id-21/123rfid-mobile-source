# Order API contract

What the app expects from a backend (any database behind it). `tools/mock-server/server.js` implements it in memory.
Set the server URL in the app: menu, "Server settings". Leave it empty to use the built-in test data.

All bodies are JSON. If a token is set in the app it is sent as `Authorization: Bearer <token>`.

## Order shape

```json
{
  "id": "2001",
  "customer": "Studio name",
  "status": "new",              // new | in_progress | completed
  "claimedBy": null,            // device id once claimed
  "updatedAt": "2026-10-23T08:00:00Z",
  "lines": [
    {
      "sku": "CM-2201",
      "description": "Free text",
      "qty": 3,
      "epcs": ["E28011010000000000000065", "..."]   // the exact rolls to pick, 24 hex chars (96 bit)
    }
  ]
}
```

EPCs are compared case-insensitively with spaces/colons/dashes ignored.

## Endpoints

| Call | Purpose | Responses |
|---|---|---|
| `GET /orders?status=new,in_progress` | Open orders. The app polls this every 20 s while the list is open. | `200 {"orders":[Order,...]}` |
| `POST /orders/{id}/claim` body `{"device":"Pixel-7a-3f2c"}` | Reserve the order for one picker. Must be atomic (`update ... where status='new'`). Claiming an order you already hold is OK. | `200` ok, `409` already taken |
| `POST /orders/{id}/complete` | Finished pick (below). **Idempotent on `completionId`.** | `200` ok (also for a repeat of the same `completionId`), `409` order already completed by another completion |

### Completion body

```json
{
  "completionId": "uuid (generated on the device, stable across retries)",
  "orderId": "2001",
  "device": "Pixel-7a-3f2c",
  "startedAt": "2026-10-23T08:00:00Z",
  "completedAt": "2026-10-23T08:05:00Z",
  "matchedEpcs": ["..."],      // every expected roll was scanned
  "unexpectedEpcs": ["..."]    // tags scanned that are not on the order (informational)
}
```

The app writes each completion to a local outbox before uploading and retries until the server answers 2xx (or 409), so a dropped connection never loses a finished order.

## Mapping to a real database later

- `orders`, `order_lines`, and one row per roll (`epc`, `sku`, `order_id`) cover the shape above.
- `claim` = a single `UPDATE orders SET status='in_progress', claimed_by=$device WHERE id=$id AND status='new'`.
- `complete` = insert into a `completions` table with a unique `completion_id`, set the order `completed`, in one transaction.
- Do not put database credentials in the app; keep an API (or row-level security) in front.
