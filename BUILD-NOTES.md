# ZakatCalc PK — Build Notes (v1.0)

**APK:** `ZakatCalcPK-v1.0-debug.apk` (689 KB, debug-signed)
**Package:** `com.zakatcalc.pk` · minSdk 24 (Android 7.0) · targetSdk 34
**Code:** 100% original Kotlin, Android framework APIs only (no appcompat/material)

## Gold-rate mechanism (verified working, no API key)
Live rates come from two free keyless APIs, fetched on "Refresh rates" tap:
1. **gold-api.com** — `GET /price/XAU` and `/price/XAG` → USD per troy ounce
   (verified 2026-10-18: XAU $4,166.10/oz, XAG $57.12/oz)
2. **open.er-api.com** — `GET /v6/latest/USD` → USD→PKR rate
   (verified 2026-10-18: PKR 277.00/USD)

Per-gram PKR = (USD/oz ÷ 31.1034768) × USD→PKR.
- Nisab gold = 87.48 g × gold/g | Nisab silver = 612.36 g × silver/g
- Threshold used = **lower of the two** (precautionary; shown in-app with note)

**Offline fallback:** bundled rates (gold Rs 37,100/g, silver Rs 510/g — computed from
the verified live values above), cached in SharedPreferences with "last updated"
timestamp. **Manual edit:** "Edit rates" dialog lets the user override anytime
(marked as manual in the UI).

## What was verified
- APK builds clean, installs signature-verified (`apksigner verify` OK)
- `aapt dump badging`: correct package, label, SDK levels, only INTERNET +
  ACCESS_NETWORK_STATE permissions (no storage/location/contacts)
- Zakat math hand-checked: Rs 500k cash → nisab Rs 312,304 → zakat Rs 12,500 (2.5%);
  Rs 100k harvest → ushr Rs 10,000 (natural) / Rs 5,000 (artificial)
- Urdu strings: full `values-ur` translation; EN⇄اردو toggle recreates activity
  with new locale (RTL supported via `supportsRtl=true`)

## Limitations / cut
- **No on-device UI test** — needs a real phone; logic is straightforward
- Agriculture = simplified ushr only (no crop-specific fiqh details)
- No ads, no login, no backend (per MVP scope)
- Gradle unusable in this sandbox (daemon IPC blocked + dep downloads fail) —
  built via manual pipeline: `build-apk.sh`
  (aapt2 → javac → kotlinc → d8 → zipalign → apksigner).
  Two sandbox fixes future Android builds need:
  1. `GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"` (daemon listens on IPv4 only,
     client tried IPv6 ::1 first)
  2. `update-ca-certificates -f` (Java cacerts symlink was dangling → all
     HTTPS downloads failed)
- Release signing: debug key only; needs a real keystore before Play Store
