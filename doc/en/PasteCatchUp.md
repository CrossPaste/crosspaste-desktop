# Reconnect catch-up compatibility

Catch-up imports at most the latest 50 records per peer and does not drain older
backlog. The clipboard write setting affects only this device; history receipts
never create relay sync tasks, including after file-download retries or restart.

## Wire compatibility

- `/pull/pasteBatch?createTime=T&limit=N` keeps its existing time-based semantics.
  Omitting `createTime` returns a bounded recent window, as before.
- `/pull/pasteBatchById?afterId=ID&limit=N` uses the serving device's database IDs.
  `afterId` is required and nonnegative; `limit` is clamped to 1–50 (default 10).
  The response is the existing PasteData JSON array. Authentication, target-device,
  send-permission, type/size policy, and response-byte limits are shared with the
  legacy route. Neither SyncApi.VERSION nor PasteData's wire fields change.
- A new receiver probes the ID route. Only an explicit missing-API response permits
  fallback to the legacy route **without a time cursor**. The receiver filters
  completed remote IDs before storing, downloading files, or writing the clipboard.
  The missing capability is cached for the peer's advertised version/address/port;
  an upgrade or endpoint change triggers a new probe. Errors such as timeouts,
  denied permissions, and decryption failures do not trigger fallback.
- Legacy fallback retransmits the recent JSON window (including text bodies), but
  does not redownload completed files. The existing response-byte ceiling still
  applies. Upgrading both peers enables the more efficient ID-filtered response.

## Local state and upgrades

The durable receipt key is `(authenticated peer instance ID, remote paste ID)`.
Local row IDs and local receive/display times are never used as remote cursors.
New cursor and outstanding-receive tables are introduced by migration 7. Existing
local-time cursors cannot be converted; automatic catch-up waits for a new settled
live receipt to establish a baseline. History predating that baseline is not
backfilled. A peer database reset must also rotate its app instance identity;
remote IDs are not transferable between database generations.

Selected receipts are reserved before batch ingest. Failed receipts and unfinished
file downloads keep the effective query cursor below their remote IDs, even when
newer receipts complete. An active local download is not scheduled again; a failed
or startup-discarded row can be retried if it is still in the peer's recent window.
Completion atomically advances the remote high-water mark and removes its pending
receipt. User deletion or clearing history terminates the matching receipts; failure cleanup
retains them for retry. Live and historical receive decisions are serialized per
peer, and history is rechecked at ingest to avoid races with live delivery.
Removing a peer clears its receipt state.

## Mobile adoption

Already released mobile clients can continue calling the legacy endpoint. Their
own time-based catch-up cursor is not fixed by a desktop upgrade. Mobile can adopt
the same ID receipt scheme later, including the SQL migration, receive/finalize
hooks, and new client route. Common interfaces retain the legacy list callback and
provide forwarding overloads for platforms not yet carrying history task metadata.
When porting the catch-up implementation, also port the task builder and file-finalize
history flag; do not depend on the forwarding defaults for silent history semantics.
