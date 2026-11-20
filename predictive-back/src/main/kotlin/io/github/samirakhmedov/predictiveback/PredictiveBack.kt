package io.github.samirakhmedov.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigationevent.compose.NavigationEventState

/**
 * The predictive back state of a Navigation 3 display: the entry decorator that draws the system
 * cross-activity back, the three transition specs of the display, and the hook that hands the gesture
 * state to the decorator. Create it with [rememberPredictiveBack].
 */
@Stable
public class PredictiveBack internal constructor(
    private val inputs: SystemBackInputs,
    /** The entry decorator. Pass it to the display's entry decorators, after the saveable-state decorator. */
    public val entryDecorator: NavEntryDecorator<NavKey>,
    specs: NavTransitionSpecs
) {
    /** The transition of a forward navigation. Pass it as the display's `transitionSpec`. */
    public val transitionSpec:
        AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        specs.transitionSpec

    /** The transition of a pop. Pass it as the display's `popTransitionSpec`. */
    public val popTransitionSpec:
        AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        specs.popTransitionSpec

    /** The transition of a predictive pop. Pass it as the display's `predictivePopTransitionSpec`. */
    public val predictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
        specs.predictivePopTransitionSpec

    /**
     * Hands the hoisted gesture state of the display to the decorator. The display creates the state after
     * the decorated entries, so call this from a `SideEffect` in the composable that owns the state.
     */
    public fun attach(state: NavigationEventState<SceneInfo<NavKey>>) {
        inputs.navState = state
    }
}

/**
 * Remembers a [PredictiveBack] for the composition. [animationsEnabled] turns the motion off when false
 * (the transitions and the gesture visuals are replaced by plain cuts). [darkTheme] selects the scrim
 * strength behind a gesture.
 */
@Composable
public fun rememberPredictiveBack(
    animationsEnabled: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme()
): PredictiveBack {
    val density = LocalDensity.current
    val inputs = remember { SystemBackInputs() }
    val specs =
        remember(animationsEnabled, density) { navTransitionSpecs(animationsEnabled, density) }
    val decorator = rememberSystemBackDecorator(inputs, animationsEnabled, darkTheme)
    return remember(inputs, specs, decorator) { PredictiveBack(inputs, decorator, specs) }
}

/**
 * An opaque, full-size surface painted with [containerColor] behind [content]. Wrap each screen in it so
 * the gesture card never shows the content behind it through the screen.
 */
@Composable
public fun PredictiveBackSurface(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().background(containerColor)) { content() }
}
