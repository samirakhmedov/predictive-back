package io.github.samirakhmedov.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

internal const val SYSTEM_BACK_DURATION_MS = 450
internal const val FADE_DURATION_MS = 83
internal const val OPEN_FADE_DELAY_MS = 50
internal const val CLOSE_FADE_DELAY_MS = 35

/**
 * One frame of a back card. A frame is applied as a scale about the top-left corner, then a translation
 * in px by [left] and [top].
 */
internal data class BackFrame(val scale: Float, val left: Float, val top: Float) {
    companion object {
        val Identity = BackFrame(1f, 0f, 0f)
    }
}

/**
 * Pure numbers of the system cross-activity back: the closing card, the entering card, the vertical
 * follow, the frames after the commit, the scrim and the corners.
 */
internal object SystemBackMotion {
    const val MAX_SCALE = 0.85f
    const val ENTERING_START_SCALE = 0.95f
    const val ENTERING_OFFSET_DP = 96f
    const val MARGIN_DP = 8f
    const val SCRIM_LIGHT = 0.2f
    const val SCRIM_DARK = 0.8f
    const val CLOSING_FADE_RATE = 5f

    val GestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    private const val EMPHASIZED_KNOT_X = 0.166666f
    private const val EMPHASIZED_KNOT_Y = 0.4f
    private val EmphasizedHead = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val EmphasizedTail = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** The emphasized path as two normalised segments that meet at the knot. */
    val EmphasizedEasing: Easing = Easing { fraction ->
        val f = fraction.coerceIn(0f, 1f)
        if (f < EMPHASIZED_KNOT_X) {
            EMPHASIZED_KNOT_Y * EmphasizedHead.transform(f / EMPHASIZED_KNOT_X)
        } else {
            EMPHASIZED_KNOT_Y +
                (1f - EMPHASIZED_KNOT_Y) *
                EmphasizedTail.transform((f - EMPHASIZED_KNOT_X) / (1f - EMPHASIZED_KNOT_X))
        }
    }

    /** The time curve after the commit: the platform accelerate-decelerate interpolator. */
    val PostCommitEasing: Easing =
        Easing { t ->
            (
                cos((t.coerceIn(0f, 1f) + 1f) * PI.toFloat()) / 2f +
                    0.5f
                )
        }

    fun gestureProgress(progress: Float): Float = GestureEasing.transform(progress.coerceIn(0f, 1f))

    fun dpToPx(dp: Float, density: Float): Float = dp * density

    fun yOffset(heightPx: Float, cardHeightPx: Float, dy: Float, marginPx: Float): Float {
        val half = heightPx / 2f
        val r = min(half, abs(dy)) / half
        return max(0f, (heightPx - cardHeightPx) / 2f - marginPx) * (1f - (1f - r) * (1f - r)) *
            sign(dy)
    }

    fun closingFrame(
        q: Float,
        edge: Int,
        widthPx: Float,
        heightPx: Float,
        dy: Float,
        marginPx: Float
    ): BackFrame {
        val scale = 1f - (1f - MAX_SCALE) * q
        val shift = if (edge == NavigationEvent.EDGE_RIGHT) {
            (1f - MAX_SCALE) / 2f * widthPx
        } else {
            (1f - MAX_SCALE) * widthPx - marginPx
        }
        val top =
            (1f - MAX_SCALE) / 2f * heightPx * q + yOffset(heightPx, scale * heightPx, dy, marginPx)
        return BackFrame(scale, q * shift, top)
    }

    fun enteringStartScale(heightPx: Float, cornerPx: Float): Float =
        min(ENTERING_START_SCALE, (heightPx - 2f * cornerPx) / heightPx)

    fun enteringFrame(
        q: Float,
        widthPx: Float,
        heightPx: Float,
        dy: Float,
        cornerPx: Float,
        marginPx: Float,
        offsetPx: Float
    ): BackFrame {
        val s0 = enteringStartScale(heightPx, cornerPx)
        val off = max((1f - MAX_SCALE) * s0 * widthPx, offsetPx)
        val startLeft = (1f - s0) * widthPx / 2f - off
        val startTop = (1f - s0) * heightPx / 2f
        val start = BackFrame(s0, startLeft, startTop)
        val target = BackFrame(
            MAX_SCALE * s0,
            startLeft + s0 * widthPx * (1f - MAX_SCALE) / 2f,
            startTop + s0 * heightPx * (1f - MAX_SCALE) / 2f
        )
        val frame = interpolate(start, target, q)
        val follow = yOffset(heightPx, (1f - (1f - MAX_SCALE) * q) * heightPx, dy, marginPx)
        return frame.copy(top = frame.top + follow)
    }

    fun interpolate(from: BackFrame, to: BackFrame, fraction: Float): BackFrame = BackFrame(
        scale = from.scale + (to.scale - from.scale) * fraction,
        left = from.left + (to.left - from.left) * fraction,
        top = from.top + (to.top - from.top) * fraction
    )

    fun closingPostCommitFrame(release: BackFrame, x: Float, offsetPx: Float): BackFrame =
        interpolate(
            release,
            BackFrame(1f, release.left + offsetPx, 0f),
            EmphasizedEasing.transform(x)
        )

    fun enteringPostCommitFrame(release: BackFrame, x: Float): BackFrame =
        interpolate(release, BackFrame.Identity, EmphasizedEasing.transform(x))

    fun closingPostCommitAlpha(x: Float): Float = max(1f - CLOSING_FADE_RATE * x, 0f)

    fun scrimAlpha(darkTheme: Boolean): Float = if (darkTheme) SCRIM_DARK else SCRIM_LIGHT

    fun postCommitScrimAlpha(darkTheme: Boolean, x: Float): Float =
        scrimAlpha(darkTheme) * (1f - x.coerceIn(0f, 1f))

    fun postCommitCornerPx(cornerPx: Float, x: Float): Float =
        cornerPx * (1f - EmphasizedEasing.transform(x))
}

internal fun reducedTransform(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

/** The three specs of the navigation display, built once per animation setting. */
internal class NavTransitionSpecs(
    val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    val predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform
)

/**
 * Builds the specs for forward, pop and predictive pop. [animationsEnabled] and [density] are read in
 * composition by the caller, never inside the scope lambdas.
 */
internal fun navTransitionSpecs(animationsEnabled: Boolean, density: Density): NavTransitionSpecs {
    if (!animationsEnabled) {
        return NavTransitionSpecs(
            transitionSpec = { reducedTransform() },
            popTransitionSpec = { reducedTransform() },
            predictivePopTransitionSpec = { reducedTransform() }
        )
    }
    val offsetPx = SystemBackMotion.dpToPx(
        SystemBackMotion.ENTERING_OFFSET_DP,
        density.density
    ).roundToInt()
    return NavTransitionSpecs(
        transitionSpec = { activityOpenTransform(offsetPx) },
        popTransitionSpec = { activityCloseTransform(offsetPx) },
        predictivePopTransitionSpec = { predictivePopTransform() }
    )
}

private fun activityOpenTransform(offsetPx: Int): ContentTransform {
    val enter =
        slideInHorizontally(
            tween(SYSTEM_BACK_DURATION_MS, easing = SystemBackMotion.EmphasizedEasing)
        ) {
            offsetPx
        } + fadeIn(tween(FADE_DURATION_MS, delayMillis = OPEN_FADE_DELAY_MS, easing = LinearEasing))
    val exit =
        slideOutHorizontally(
            tween(SYSTEM_BACK_DURATION_MS, easing = SystemBackMotion.EmphasizedEasing)
        ) {
            -offsetPx
        }
    return enter togetherWith exit
}

private fun activityCloseTransform(offsetPx: Int): ContentTransform {
    val enter =
        slideInHorizontally(
            tween(SYSTEM_BACK_DURATION_MS, easing = SystemBackMotion.EmphasizedEasing)
        ) {
            -offsetPx
        }
    val exit =
        slideOutHorizontally(
            tween(SYSTEM_BACK_DURATION_MS, easing = SystemBackMotion.EmphasizedEasing)
        ) {
            offsetPx
        } +
            fadeOut(
                tween(FADE_DURATION_MS, delayMillis = CLOSE_FADE_DELAY_MS, easing = LinearEasing)
            )
    return enter togetherWith exit
}

/**
 * The transform of the predictive back. The gesture visuals are drawn by the system-back entry
 * decorator, so the seeked transform holds both screens unchanged.
 */
internal fun predictivePopTransform(): ContentTransform = reducedTransform()
