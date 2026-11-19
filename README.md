# WattWarden

**Battery & charging guardian for Android.** Real-time wattage, charger quality testing, battery-health estimation, smart-plug automation and phantom-drain detection, built with Jetpack Compose.

The shield is the *warden*; the bolt is the *watt*.

## Build & run

1. Open this folder in **Android Studio (Koala or newer)**, JDK 17.
2. Let Gradle sync, then run on a **real device** (emulators report no useful current/charge data).
3. Grant the notification permission, then switch **Guardian** on in the Live tab.

Min SDK 26 · Target SDK 34 · Kotlin 2.0.20 · Compose BOM 2024.09

## Features

| Area | What it does |
|---|---|
| Live | Wattage gauge, mA, voltage, temperature, ETA to 80% / 100%, session power curve |
| Charger tester | Peak/avg W per session; name sessions ("Anker 65W + blue cable") and rank them in Health |
| Thermal | Heat alert above your limit (default 42 °C) |
| Health | Capacity from the coulomb counter across ≥20% charges (median of last 5) vs design capacity; cycle counter (20→70% = 0.5) |
| Charge alarm | Notification at your limit (default 80%) + optional smart-plug OFF webhook |
| Trickle guard | Alert when pinned at 100% for N minutes |
| Phantom drain | Screen-off %/h from periodic + screen-event samples; alert above threshold |
| Notification | Ongoing live watts, temp, and ETAs |
| Widget | Glance home-screen widget: level, status, temp, health, screen-on time left |
| Calibration | Auto/µA/mA unit and sign-invert toggle for OEM quirks |

## Architecture

```
data/      BatteryReader (BatteryManager + ACTION_BATTERY_CHANGED), Room DB, DataStore settings,
           ChargeSessionTracker, Analytics + DrainAnalyzer
service/   GuardianService (foreground, event-driven), Notifier, Webhook
worker/    DrainWorker (15-min WorkManager sampler + phantom-drain check)
widget/    Jetpack Glance widget
ui/        Compose screens: Live, Health, History, Settings (+ custom Canvas gauge/chart)
```

Doze strategy: the service polls every 3 s only while plugged in and screen on (15 s screen off), and otherwise sleeps until a battery/power/screen broadcast. WorkManager keeps sampling even if the service is killed.

## Smart-plug webhooks

Both URLs receive a JSON `POST`.

- **Home Assistant:** create an automation with a *Webhook* trigger, then use `http://<ha-ip>:8123/api/webhook/<id>`.
- **IFTTT:** `https://maker.ifttt.com/trigger/<event>/json/with/key/<key>`

Payload example: `{"event":"charge_limit","level":80,"temp_c":34.2}`. Events: `charge_limit`, `trickle_guard`, `low_level`, `test`.

## Notes & limitations

- Current and charge-counter data quality varies by manufacturer. If numbers look 1000× off or the sign is reversed, use **Settings → Calibration**.
- Capacity estimates have roughly ±5% error (battery level is whole-percent).
- Phantom drain needs about an hour of unplugged screen-off samples before it reports.
- Charts are drawn with a small custom Canvas component, so there is no Vico/MPAndroidChart dependency.
- Some OEMs (Xiaomi, Oppo, Huawei…) kill background services; use the in-app battery-optimisation shortcut and the vendor's auto-start setting.
- `usesCleartextTraffic` is enabled so local `http://` Home Assistant URLs work; restrict it if you only use HTTPS.

## Logo

`branding/wattwarden-logo.svg` (source), plus 512 and 1024 px PNGs. The in-app adaptive icon (with a themed monochrome variant) lives in `res/drawable/ic_launcher_*.xml`.
