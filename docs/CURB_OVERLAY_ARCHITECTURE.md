# Curb overlay adapter (PR7)

## Integration point

The app uses **Jetpack Compose** with a layered driver screen in `DriverViewScreen` (`PanoramaView` → transparent `SceneView` → `FluidLevelOverlay`). The curb package is integrated in `MainActivity` by stacking `CurbOverlayLayer` on top of `OrientationAdaptiveContainer` / `DriverViewScreen` when a middleware host IP is known.

No changes were made to steering, wheel alignment, `PanoramaView`, `MJPEGRepository`, or `DriverViewScreen` internals.

## Middleware contract

Source of truth: `fake_vehicle_ultrasonic_integration` → `CurbModel.snapshot()` SSE event `curb`:

- Endpoint: `GET http://<host>:9091/api/v1/signals` (SSE)
- Event name: `curb`
- Fields: `type`, `seq`, `ts`, `thresholds.dangerM`, `thresholds.warningM`, `zones[]` (`pos`, `distanceM`, `zone`, `source`, `color`, `pano`), `vision`

Sides are already `LEFT` / `RIGHT` / `FRONT` / `REAR`. The mapper also accepts ultrasonic-style IDs (`FRONT_LEFT`, `REAR_CENTER`, …) and aggregates to the nearest distance per side.

## Package layout

`com.edag.seethroughapplication.curboverlay`

| Module | Role |
|--------|------|
| `model` | `CurbSide`, `CurbZone`, `CurbState` |
| `parser/CurbJsonParser` | Pure JSON → `MiddlewareCurbEvent` |
| `mapper/CurbZoneMapper` | Four-side normalization + stale grey-out |
| `network` | `CurbEventSource`, `SseCurbEventSource` |
| `controller/CurbOverlayController` | SSE → state, UI thread |
| `renderer/CurbOverlayRenderer` | Compose vehicle-view overlay |
| `CurbOverlayIntegration` | `CurbOverlayLayer` composable |

## Configuration

```kotlin
CurbOverlayConfig.forHost(
    host = "10.44.26.147",
    port = 9091,
    enabled = true,
)
```

Default `enabled = false` keeps behaviour identical until the host opts in.

## Manual verification

1. Start middleware (`VideoStreamingServer` / WP5) on port **9091** with curb SSE active.
2. Run the Android app, enter the middleware PC IP, enable curb overlay in `MainActivity` (`curbOverlayEnabled = true`).
3. Use WP5 viewer or `tools/fake_vehicle.py` to post vision curbs; confirm four badges update with distance and colour.
4. Stop SSE or wait >3 s without updates; badges should show grey unknown (`—`).
5. Set `curbOverlayEnabled = false` and rebuild; screen should match pre-PR7 behaviour.

## Tests

JVM unit tests under `app/src/test/.../curboverlay` cover parser, mapper, and controller with fixtures from the middleware schema.
