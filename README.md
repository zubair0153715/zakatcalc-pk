# ZakatCalc PK

Zakat + Ushr calculator for Pakistan — Android app, Urdu/English with RTL.

**Package:** `com.zakatcalc.pk` · **Min SDK:** 24 · **Kotlin**

## Features (v1.0)

- Cash, gold, silver, business assets, liabilities
- Ushr (10% / 5%)
- Live gold/silver rates (gold-api.com) + USD→PKR (open.er-api.com)
- Nisab auto-calculated from the lower of 87.48g gold / 612.36g silver
- Offline fallback rate + manual override
- Urdu ⇄ English toggle (RTL)

## Build

```bash
./build-apk.sh
```

Needs: JDK 21, Android SDK (build-tools, platform-34). See `BUILD-NOTES.md`.

Debug APKs and keystores are build artifacts and are not committed here.
Release signing needs a real keystore before Play Store upload.
