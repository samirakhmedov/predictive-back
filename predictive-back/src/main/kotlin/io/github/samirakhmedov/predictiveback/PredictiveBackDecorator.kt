package io.github.samirakhmedov.predictiveback

import android.view.RoundedCorner
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventState

/** What the decorated entries read from the navigation host. */
@Stable
internal class SystemBackInputs {
    /**
     * The hoisted gesture state. The display creates it after the decorated entries (`NavDisplay.kt:540-551`), so
     * the host hands it over in a `SideEffect`.
     */
    var navState: NavigationEventState<SceneInfo<NavKey>>? by mutableStateOf(null)
}

private enum class BackRole { Closing, Entering }

/** Per-entry memory of the last gesture frame. Plain fields: they are written and read inside the layer lambda. */
private class EntryBackState {
    var role: BackRole? = null
    var release: BackFrame? = null
    var edge: Int = NavigationEvent.EDGE_NONE

    /** The top content key seen when [release] was stored. */
    var top: Any? = null
}

/** The first touch of the running gesture, shared by the closing and the entering entry. */
private class GestureOrigin {
    var firstTouchY: Float = Float.NaN
}

/** One applied layer: the frame, its alpha and its corner radius in px. */
private class LayerFrame(val frame: BackFrame, val alpha: Float, val cornerPx: Float)

/**
 * The platform cross-activity back (the look of the system Settings app), drawn per navigation entry: the closing
 * screen shrinks into a card pinned near the far edge, the previous screen waits behind it under a scrim, and after
 * the commit the card fades out while the previous screen grows back to full size. Every number lives in
 * [SystemBackMotion]; this file only reads the gesture state and applies frames in layer and draw lambdas.
 */
@Composable
internal fun rememberSystemBackDecorator(
    inputs: SystemBackInputs,
    animationsEnabled: Boolean,
    darkTheme: Boolean
): NavEntryDecorator<NavKey> {
    val enabled = rememberUpdatedState(animationsEnabled)
    val dark = rememberUpdatedState(darkTheme)
    val origin = remember { GestureOrigin() }
    return remember(inputs) {
        NavEntryDecorator<NavKey>(
            decorate = { entry ->
                SystemBackEntry(entry, inputs, origin, enabled.value, dark.value)
            }
        )
    }
}

@Composable
private fun SystemBackEntry(
    entry: NavEntry<NavKey>,
    inputs: SystemBackInputs,
    origin: GestureOrigin,
    animationsEnabled: Boolean,
    darkTheme: Boolean
) {
    // One call position for the content whatever the motion setting, so the entry state survives a toggle.
    val motion = if (animationsEnabled) {
        systemBackMotion(
            entry,
            inputs,
            origin,
            darkTheme
        )
    } else {
        Modifier
    }
    Box(Modifier.fillMaxSize().then(motion)) { entry.Content() }
}

@Composable
private fun systemBackMotion(
    entry: NavEntry<NavKey>,
    inputs: SystemBackInputs,
    origin: GestureOrigin,
    darkTheme: Boolean
): Modifier {
    val transition = LocalNavAnimatedContentScope.current.transition
    // Keeps the closing entry composed after the commit, long enough for its card to fade out.
    transition.animateFloat(
        transitionSpec = { tween(SYSTEM_BACK_DURATION_MS, easing = LinearEasing) },
        label = "systemBackKeepAlive"
    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
    val settled = transition.currentState == transition.targetState
    val topKey = inputs.navState?.currentInfo?.scene?.entries?.lastOrNull()?.contentKey
    val back = remember { EntryBackState() }
    val post = remember { Animatable(0f) }
    val view = LocalView.current
    val density = LocalDensity.current.density

    val closingNow = transition.targetState == EnterExitState.PostExit
    val enteringNow =
        transition.currentState == EnterExitState.PreEnter &&
            transition.targetState == EnterExitState.Visible
    // An entering entry that starts to close is no longer committed, so its post-commit stops and it can become
    // the next closing card.
    val committed = when (back.role) {
        BackRole.Closing -> entry.contentKey != topKey
        BackRole.Entering -> entry.contentKey == topKey && !closingNow
        null -> false
    }

    val runPost = committed && back.release != null
    LaunchedEffect(runPost) {
        if (runPost) {
            try {
                post.snapTo(0f)
                post.animateTo(
                    1f,
                    tween(SYSTEM_BACK_DURATION_MS, easing = SystemBackMotion.PostCommitEasing)
                )
                post.snapTo(0f)
            } finally {
                // Also runs on cancellation: the entry may be reused later, so it never keeps a stale frame.
                back.release = null
                back.role = null
                back.top = null
            }
        }
    }

    fun backInProgress(state: NavigationEventTransitionState?): Boolean =
        state is NavigationEventTransitionState.InProgress &&
            state.direction == NavigationEventTransitionState.TRANSITIONING_BACK

    fun scrim(): Float {
        val gesture = backInProgress(inputs.navState?.transitionState)
        val entering = back.role == BackRole.Entering
        return if (committed && back.release != null && entering) {
            SystemBackMotion.postCommitScrimAlpha(darkTheme, post.value)
        } else if (entering &&
            (gesture || (back.release != null && !settled && topKey == back.top))
        ) {
            SystemBackMotion.scrimAlpha(darkTheme)
        } else {
            0f
        }
    }

    // The scrim sits outside the layer, so it covers the entering entry's full unscaled bounds.
    return Modifier
        .drawWithContent {
            drawContent()
            val a = scrim()
            if (a > 0f) drawRect(Color.Black, alpha = a)
        }
        .graphicsLayer {
            val state = inputs.navState?.transitionState
            if (state !is NavigationEventTransitionState.InProgress) {
                origin.firstTouchY = Float.NaN
            }
            val cornerPx = view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius
                ?.toFloat() ?: 0f
            val marginPx = SystemBackMotion.dpToPx(SystemBackMotion.MARGIN_DP, density)
            val offsetPx = SystemBackMotion.dpToPx(SystemBackMotion.ENTERING_OFFSET_DP, density)
            val release = back.release
            val layer = if (committed && release != null) {
                val x = post.value
                if (back.role == BackRole.Closing) {
                    LayerFrame(
                        SystemBackMotion.closingPostCommitFrame(release, x, offsetPx),
                        SystemBackMotion.closingPostCommitAlpha(x),
                        cornerPx
                    )
                } else {
                    LayerFrame(
                        SystemBackMotion.enteringPostCommitFrame(release, x),
                        1f,
                        SystemBackMotion.postCommitCornerPx(cornerPx, x)
                    )
                }
            } else if (
                state is NavigationEventTransitionState.InProgress &&
                state.direction == NavigationEventTransitionState.TRANSITIONING_BACK &&
                !committed &&
                (closingNow || enteringNow)
            ) {
                if (origin.firstTouchY.isNaN()) {
                    origin.firstTouchY = state.latestEvent.touchY
                }
                val q = SystemBackMotion.gestureProgress(state.latestEvent.progress)
                val dy = state.latestEvent.touchY - origin.firstTouchY
                // The role comes from the current transition, not from the stored one: a rapid second back turns
                // the entry that was entering into the closing card.
                val frame = if (closingNow) {
                    SystemBackMotion.closingFrame(
                        q,
                        state.latestEvent.swipeEdge,
                        size.width,
                        size.height,
                        dy,
                        marginPx
                    )
                } else {
                    SystemBackMotion.enteringFrame(
                        q,
                        size.width,
                        size.height,
                        dy,
                        cornerPx,
                        marginPx,
                        offsetPx
                    )
                }
                back.role = if (closingNow) BackRole.Closing else BackRole.Entering
                back.release = frame
                back.edge = state.latestEvent.swipeEdge
                back.top = topKey
                LayerFrame(frame, 1f, cornerPx)
            } else if (release != null && !settled && topKey == back.top) {
                LayerFrame(release, 1f, cornerPx)
            } else {
                if (!committed && (settled || topKey != back.top)) {
                    // A stale release frame is never drawn under a later navigation.
                    back.release = null
                    back.role = null
                    back.top = null
                }
                LayerFrame(BackFrame.Identity, 1f, 0f)
            }
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = layer.frame.scale
            scaleY = layer.frame.scale
            translationX = layer.frame.left
            translationY = layer.frame.top
            alpha = layer.alpha
            shape = if (layer.cornerPx > 0f) RoundedCornerShape(layer.cornerPx) else RectangleShape
            clip = layer.cornerPx > 0f
        }
}
