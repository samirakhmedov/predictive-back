# predictive-back

Android 17 Settings-style predictive back for Navigation 3. The library draws the system cross-activity
back gesture for a Navigation 3 `NavDisplay`: the previous screen slides in behind the current one, the
gesture card scales and fades with the back progress, and a committed back plays the matching transition.

## Requirements

- `minSdk 33`
- Navigation 3 `1.2.x` (`androidx.navigation3:navigation3-runtime` and `navigation3-ui`, pulled in
  transitively as `api` dependencies)
- Predictive back enabled in the app manifest:

```xml
<application
    android:enableOnBackInvokedCallback="true"
    ... >
```

## Installation

Add the JitPack repository to `settings.gradle.kts`, scoped to the group so no other artifact is resolved
from it:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.samirakhmedov")
            }
        }
    }
}
```

Then add the dependency to the module that hosts the `NavDisplay`:

```kotlin
dependencies {
    implementation("com.github.samirakhmedov:predictive-back:v0.1.0")
}
```

## Usage

The recipe hoists the `NavDisplay` state so the predictive back decorator can observe the gesture. The
saveable-state decorator must be listed first in `entryDecorators`; the predictive back decorator comes
after it.

```kotlin
@Composable
fun AppNavHost() {
    val darkTheme = isSystemInDarkTheme()
    val predictiveBack = rememberPredictiveBack()
    val backStack = remember { mutableStateListOf<NavKey>(Home) }
    val onBack: () -> Unit = { backStack.removeLastOrNull() }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            predictiveBack.entryDecorator
        ),
        entryProvider = entryProvider {
            entry<Home> {
                PredictiveBackSurface(containerColor = if (darkTheme) Color.Black else Color.White) {
                    HomeScreen(onOpenDetail = { backStack.add(Detail) })
                }
            }
            entry<Detail> {
                PredictiveBackSurface(containerColor = if (darkTheme) Color.Black else Color.White) {
                    DetailScreen()
                }
            }
        }
    )
    val sceneStrategies = remember { listOf(SinglePaneSceneStrategy<NavKey>()) }
    val sceneState = rememberSceneState(entries, sceneStrategies, onBack = onBack)
    val navState = rememberNavigationEventState(sceneState)
    SideEffect { predictiveBack.attach(navState) }
    NavigationBackHandler(sceneState, navState, onBackCompleted = onBack)
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navState,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = predictiveBack.transitionSpec,
        popTransitionSpec = predictiveBack.popTransitionSpec,
        predictivePopTransitionSpec = predictiveBack.predictivePopTransitionSpec
    )
}
```

Each screen is wrapped in `PredictiveBackSurface` so the gesture card never shows the content behind it
through the screen. A complete three-screen example is in the `sample` module.

## API reference

- `rememberPredictiveBack(animationsEnabled: Boolean = true, darkTheme: Boolean = isSystemInDarkTheme()): PredictiveBack`
  remembers the predictive back state for the composition. `animationsEnabled = false` replaces the motion
  with plain cuts. `darkTheme` selects the scrim strength behind a gesture.
- `PredictiveBack`
  - `entryDecorator`: the entry decorator. Pass it to `rememberDecoratedNavEntries`, after the saveable-state
    decorator.
  - `transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`: pass them to `NavDisplay`.
  - `attach(state)`: hands the hoisted `NavigationEventState` of the display to the decorator. Call it from a
    `SideEffect` in the composable that owns the state.
- `PredictiveBackSurface(containerColor: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit)`:
  an opaque, full-size surface behind each screen.

## Versioning

The library follows [Semantic Versioning](https://semver.org/). Releases are git tags named `vX.Y.Z`, which
JitPack resolves as the version `vX.Y.Z`. A published tag is never moved or re-pushed; a fix is released
under a new version.

## Credits

The motion constants and curves are ported from the Android Open Source Project, branch `android17-release`
of `frameworks/base`:
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/

Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0.

## License

Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
https://www.apache.org/licenses/LICENSE-2.0
