# Hospital Indoor Map (Android, Jetpack Compose)

Indoor wayfinding app for **Hoan My General Hospital**. It shows the floor plans of one
building, finds a walking route between two rooms (even across floors, using elevators or
stairs), and guides the user step by step.

Everything runs offline. Floor data comes from embedded JSON (`BuildingJsonRawData.kt`).
There's also a copy in `app/src/main/assets/building/`.

> **Note for AI assistants (Google AI Studio / Gemini):** this README is the source of truth
> for the current UI architecture. The routing and data layers are stable. Most work
> happens in `hospital/presentation/`. Please keep the conventions in
> [Conventions](#conventions-please-keep).

---

## Tech stack

- Kotlin, Jetpack Compose (Material 3, material-icons-extended), single Activity
- MVVM: `ViewModel` exposes a `StateFlow<HospitalMapUiState>`, and the UI collects it with
  `collectAsStateWithLifecycle`
- Manual DI: `di/AppContainer` → `DefaultAppContainer` → `ViewModelFactory`
- Coroutines. Routing runs on `Dispatchers.Default`
- The map is drawn with OpenGL ES 2.0 (`MapGl.kt`, a port of the web's `gl.js`) under a Compose
  `Canvas` overlay for labels, pins and the dot. There is no map SDK and no WebView

## Package map

```
com.example
├── MainActivity.kt                 edge-to-edge, sets HospitalMapScreen
├── MapApplication.kt / di/         manual DI container + ViewModelFactory
├── data/…                          legacy generic "MapFlow" data layer (still wired in DI, not used by the UI)
├── ui/theme/                       Color.kt (includes the indoor map palette), Theme.kt, Type.kt
└── hospital/
    ├── data/datasource/            BuildingJsonParser (JSON → Floor/Room), raw JSON, Mock data source
    ├── data/repository/            HospitalMapRepository(+Impl)
    ├── domain/model/               Floor, Room, Point2D, FloorLabel, HospitalRoute, RouteSegment, TransferInfo, RouteStep
    ├── domain/pathfinding/         GridAStarPathfinder (grid raster + A*), Dijkstra fallback, PathSimplifier
    ├── domain/usecase/             GetHospitalMap, SearchRooms, FindHospitalRoute (supports preferredTransferKind)
    └── presentation/
        ├── state/HospitalMapUiState.kt     UI state + TransferPreference + RouteStepUi
        ├── viewmodel/HospitalMapViewModel  all user intents (select floor, pick rooms, navigate, search, back)
        ├── util/HospitalLocalization       VI/EN strings, roomLabel()/sentenceCase()/shaftLabel()
        ├── util/RouteStepFormatter         domain route → short mobile step rows
        └── ui/
            ├── HospitalMapScreen.kt        full-screen map + floating overlays (layout below)
            └── components/
                ├── HospitalCanvasMap.kt    GL view + Canvas overlay, gestures, 3D↔2D camera animation
                ├── MapCamera.kt            MapCamera, MapProjector, fit/zoom/lerp math
                ├── MapGl.kt                OpenGL renderer: room blocks, route ribbon with chevrons, beams; one draw call per floor
                ├── FloorRail.kt            vertical floor picker (3D, 3, 2, 1)
                ├── RouteSelectorCard.kt    From/To card + TransferPreferenceChips
                ├── RouteBottomSheet.kt     ETA, step chips, expandable step list, start button
                ├── RoomDetailSheet.kt      sheet after tapping a room ("Đến đây" / "Đi từ đây")
                ├── NavigationSheet.kt      NavigationBanner (top) + NavigationSheet (bottom)
                ├── RoomSearchDialog.kt     full-screen search with accent-insensitive matching + quick chips
                └── MapOverlays.kt          SearchPill, ViewTitleChip, MapFab, FloorEmptyCard, MapBottomSheetContainer
```

## Building data

Each floor is one JSON object: `width` / `height` in plan units (currently 1795 × 734),
`rooms[]` and `labels[]`.

- Each room has `polygon` (a list of `[x, y]` points), `name` (VI, uppercase),
  `name_en`, `color`, `text_color` and an optional `label_at`.
- Stairs and elevators also have `kind` (`"elevator"` / `"stair"`) and `link`. Two floors
  are connected wherever the **same `link`** (e.g. `elevator-EF`) appears on both.
- `labels[]` are zone captions: `at` is the centre, `w` / `h` is the box that caption
  must fit in.
- Floor 1 is a placeholder with `isTodo = true`: it has no data yet and is drawn as a
  dashed plate.

Room ids look like `tang-2_48`. Distances in the domain are plan units × 0.1 m.

## UI design (mobile redesign)

The earlier layout stacked a header, route selector and floor tabs on top of the map.
On phones that pushed the map into a small strip, truncated the tabs and made labels
overlap. The new layout follows the pattern of common map apps:

1. **The map is full-screen, edge-to-edge.** Every control floats over it.
2. **Top block**, which changes with the state:
   - no destination → `SearchPill`
   - route selected → `RouteSelectorCard` (From and To stacked vertically, ✕ close, ⇅ swap)
     plus `TransferPreferenceChips` (Fastest / Elevator only / Stairs only)
   - navigating → `NavigationBanner` showing the current step in large text
3. **Right edge:** `FloorRail` (3D, then floors from top to bottom, like elevator buttons).
   A blue dot marks floors used by the route. Below it: recenter button and VI/EN toggle.
4. **Bottom:** only one sheet at a time. Priority: navigation → room details → route.
5. **Search** opens as a full-screen overlay with the keyboard already open.
6. **System back** closes layers in order: search → navigation → room sheet → expanded
   list. It never switches to 3D. See `HospitalMapViewModel.onBack()`.

The measured heights of the top block and bottom sheet are passed to the map as
`MapViewportInsets`. The camera then fits the plan into the part of the screen that is
actually visible.

### The two map views and the transition

`selectedFloorLevel == null` means the **"Tất cả" (all floors) isometric stack**. Any
other value means that floor is shown as a **2D plan**.

Both views are drawn by one orthographic `MapCamera(tilt, spin, scale, tx, ty)` and a
per-floor `FloorVisual(z, alpha)`:

| View | tilt | spin | floors |
|---|---|---|---|
| All floors (3D) | 56° (drag: 28–78°) | −32° (drag to orbit) | spread on Z by `FLOOR_GAP` |
| Single floor (2D) | 0° | **90° on portrait**, 0° on landscape | selected z = 0, others pushed away and faded out |

When the view changes, `HospitalCanvasMap` interpolates all camera fields and every
floor's z/alpha over **900 ms** (`CubicBezierEasing(0.6, 0, 0.2, 1)`). The selected floor
flies out of the stack and flattens into the 2D plan, and the reverse happens when going
back. Labels, pins and floor tags are hidden during the animation and fade in afterwards.

**Auto-rotation:** the plan is about 2.4 : 1, so on a portrait phone the 2D view rotates
it 90°, which makes it about 2.5× larger. The rotation happens only at render time
(`spin`). Plan coordinates, JSON and routing are never rotated.

**Labels** are drawn upright in screen space. The font scales with zoom (9–13 sp).
A label is drawn only if it fits inside its room's box without breaking a word;
otherwise it is hidden, not overlapped.

**Gestures:**
- 3D: drag = orbit, pinch = zoom, tap a floor = open that floor.
- 2D: drag = pan, pinch = zoom (0.6×–7× of the fitted scale), tap a room = room sheet,
  tap empty space = close the sheet.

No automatic 3D (web rule G6, 3D jumps are jarring): the app opens on the start floor in 2D,
every route (cross-floor too) is shown on its start floor, and a floor-change step shows
the floor the user stands on (the elevator/stairs cell). Only the 3D button on `FloorRail`
or "Xem tất cả tầng" opens the 3D stack.

## Routing (unchanged except one parameter)

`FindHospitalRouteUseCase(start, dest, language, preferredTransferKind)`:
- Same floor: `GridAStarPathfinder.findPathOnFloor`.
- Different floors: try every shaft `link` shared by both floors (optionally limited to
  `"elevator"` or `"stair"`) and keep the shortest `segment A + segment B`.
- If a preference is set and no such shaft exists, it returns `null` and the UI shows
  "no route".
- Otherwise it falls back to Dijkstra on the mock graph.

`RouteStepFormatter` turns the result into 1 step (same floor) or 3 steps
(walk → floor change → walk).

## Conventions (please keep)

- **Colors:** use the tokens in `ui/theme/Color.kt` (`MapBluePrimary`, `MapInk`,
  `MapInkMuted`, `MapCanvasBackground`…). Don't add new hex values in components.
  Dynamic color is off on purpose (`Theme.kt`) so route, start and destination colors
  stay fixed.
- **Strings:** use `HospitalLocalization.t(lang, "vi", "en")` for short UI copy.
  Room names always go through `HospitalLocalization.roomLabel(room, lang)`, because raw
  names are UPPERCASE.
- **State:** camera, zoom and pan are UI-only state inside `HospitalCanvasMap`. The
  ViewModel only decides *which* view is shown (`selectedFloorLevel`). Don't move camera
  state back into the ViewModel.
- **Overlays:** keep the map interactive. Use `MapBottomSheetContainer`, not
  `ModalBottomSheet`, for map sheets.
- **Touch targets:** at least 44 dp.
- **Test tags:** keep the existing tags (`hospital_map_canvas`, `route_selector_card`,
  `swap_rooms_button`, `btn_navigation_action`, `search_room_input`, `room_item_<id>`,
  `btn_reset_view`, `btn_toggle_language`, `btn_positioning_mode`, `sheet_handle`,
  `sheet_hide_toggle`, `search_floor_all`, `search_floor_<floorId>`).
- **Bottom sheets** (`MapBottomSheetContainer`): swipe the handle down or tap the chevron to hide
  the sheet completely (only a thin handle strip stays); swipe up or tap to show it again. A new
  room or a new route shows it again.
- **"Tôi bị lạc"** in the route and navigation sheets shows only when the sheet is expanded
  (list button or tap the handle), or while a help request is active.
- **Search floor filter** (web C4b): chips "Tất cả tầng" + one per mapped floor (`short` from the
  server: B1, 1, 2…), combined with the query; at most 60 results; Enter picks the first one. New tags follow the same snake_case style.

## Indoor positioning (`hospital/sensor/`)

Same algorithm and numbers as the web (`MLAI_Hackathon/src/web/pdr.js`). Every constant comes
from the server: `GET /api/floors` -> `positioning` (`plan_north_deg`, `step_k`, `reroute`,
`ronin`, `pdr.step/learner/fusion/tracker`), parsed into `domain/model/Positioning.kt`.
Nothing is hardcoded: if the server cannot be reached (offline map data) the sensors are
ignored and the dot stays at the start, like the web.

- **Baseline** (default): `StepDetector` (accelerometer only, like the web; the hardware step
  counter is shown in the diagnostics panel but never moves the dot) + `HeadingFusion` (gyro + compass, `plan_north_deg`) + `IndoorPdrTracker` (slides the
  dot along the route, corners, walking back, off-route). `StepLearner` learns the step length
  at each corner and keeps it in SharedPreferences.
- **RoNIN**: accelerometer, gyroscope and game rotation at 200 Hz are streamed to
  `wss://…/api/ronin` (`data/api/RoninClient.kt`); each velocity the server answers becomes one
  step into the same tracker. North is learned from the compass when the mode is switched on.
- The map button under "recenter" switches Baseline / RoNIN; the choice is kept.
- **Directions camera** (web D29): during directions the 2D map follows the dot, zoomed 2.4x,
  tilted 52°, turned so the route piece under the dot points up (by the route, never by the
  compass, which shakes indoors), dot at 70% of the free height. A drag lets go; the recenter
  button turns blue ("Về giữa") and follows again. A pinch while following only changes zoom.
  The screen stays on during directions.
- The telemetry line in the navigation sheet (expand it) shows the mode, compass, plan north
  from the server, steps and the learned step factor `k`.

## Build / run

Open the project in Android Studio (compileSdk 36, minSdk 24, JDK 21) and run the `app`
configuration. Unit tests: `./gradlew testDebugUnitTest` (Robolectric).

## Known gaps / TODO

- Floor 1 has no data yet (`isTodo`).
- The legacy `data/` + `MapRepository` layer ("MapFlow" template) is still wired in DI
  and used by `ExampleRobolectricTest`. It can be removed together with that test.
- Walking speed (~1.1 m/s) and the 40 s cost of a floor change are rough guesses.
- RoNIN mode is not yet tested on a real Android phone (team numbers come from phyphox at 100 Hz).
