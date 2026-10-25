# ScyllaDeck

Android app to manage your **Stremio add-on list**: reorder, switch off, install, configure and discover add-ons.
*Unofficial. Not affiliated with or endorsed by Stremio.*

## Features
- Grid of add-on cards (1, 2 or 3 columns) with a priority number on each card
- Reorder by drag and drop, or tap a card and use the arrow keys (one spot, one row, top, bottom, exact position)
- Nothing reaches your account until you tap **Apply**; Undo and Discard are available
- A backup of your list is saved before every Apply, with one-tap restore
- Turn any add-on off without losing its place (turned-off add-ons are stored on the phone), including Cinemeta; protected add-ons can't be deleted
- Install from a manifest link, a `stremio://` link, a QR code, or by sharing a link to the app
- **Discover**: browse the community add-on list (stremio-addons.net) inside the app and add add-ons in one tap
- Configure add-ons in an in-app browser; if the page produces a new link, replace the old add-on and keep its position
- Health check (ok / slow / dead), duplicate finder, search, multi-select, profiles, layout export/import, optional auto-apply
- Dark and light themes

## Privacy
Your Stremio login key is stored encrypted on the phone. The app talks only to Stremio's API and to the add-on links you give it. No analytics.

## Limits
- Uses Stremio's **unofficial** API, which may change without notice.
- Turned-off add-ons exist only on the phone where you turned them off.
- Apply writes the full list, so the app fetches your latest list first and keeps a backup.
- The Discover list is community-made and not reviewed by Stremio. ScyllaDeck does not vet what it contains.

## Build
Open the folder in Android Studio (or AndroidIDE), let Gradle sync, and run. Requires Android 8.0+ (minSdk 26).
AGP 9.x with built-in Kotlin, Compose, Room, OkHttp, Coil, Reorderable and ZXing.

## License
MIT. See `LICENSE`.
