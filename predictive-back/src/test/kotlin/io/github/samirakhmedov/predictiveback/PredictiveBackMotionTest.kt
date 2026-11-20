package io.github.samirakhmedov.predictiveback

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigationevent.NavigationEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val W = 1080f
private const val H = 2400f
private const val DENSITY = 2.625f
private const val TOLERANCE = 0.01f
private const val EASING_TOLERANCE = 1e-3f
private const val MARGIN_PX = 21f
private const val OFFSET_PX = 252f

class PredictiveBackMotionTest {
    private fun assertFrame(frame: BackFrame, scale: Float, left: Float, top: Float) {
        assertThat(frame.scale).isWithin(EASING_TOLERANCE).of(scale)
        assertThat(frame.left).isWithin(TOLERANCE).of(left)
        assertThat(frame.top).isWithin(TOLERANCE).of(top)
    }

    @Test
    fun `the gesture progress follows the back gesture curve`() {
        assertThat(SystemBackMotion.gestureProgress(0.3f)).isWithin(EASING_TOLERANCE).of(0.7437f)
        assertThat(SystemBackMotion.gestureProgress(0.1f)).isWithin(EASING_TOLERANCE).of(0.3717f)
    }

    @Test
    fun `the emphasized easing hits the reference points`() {
        assertThat(
            SystemBackMotion.EmphasizedEasing.transform(0.2f)
        ).isWithin(EASING_TOLERANCE).of(0.6357f)
        assertThat(
            SystemBackMotion.EmphasizedEasing.transform(0.5f)
        ).isWithin(EASING_TOLERANCE).of(0.9506f)
        assertThat(
            SystemBackMotion.EmphasizedEasing.transform(0f)
        ).isWithin(EASING_TOLERANCE).of(0f)
        assertThat(
            SystemBackMotion.EmphasizedEasing.transform(1f)
        ).isWithin(EASING_TOLERANCE).of(1f)
    }

    @Test
    fun `the emphasized easing never decreases`() {
        var previous = SystemBackMotion.EmphasizedEasing.transform(0f)
        for (step in 1..100) {
            val value = SystemBackMotion.EmphasizedEasing.transform(step / 100f)
            assertThat(value).isAtLeast(previous - EASING_TOLERANCE)
            previous = value
        }
    }

    @Test
    fun `the closing card shrinks toward the swipe edge`() {
        val full = SystemBackMotion.closingFrame(1f, NavigationEvent.EDGE_LEFT, W, H, 0f, MARGIN_PX)
        assertFrame(full, 0.85f, 141.0f, 180.0f)
        val half = SystemBackMotion.closingFrame(
            0.5f,
            NavigationEvent.EDGE_LEFT,
            W,
            H,
            0f,
            MARGIN_PX
        )
        assertFrame(half, 0.925f, 70.5f, 90.0f)
        val right = SystemBackMotion.closingFrame(
            1f,
            NavigationEvent.EDGE_RIGHT,
            W,
            H,
            0f,
            MARGIN_PX
        )
        assertThat(right.left).isWithin(TOLERANCE).of(81.0f)
        val none = SystemBackMotion.closingFrame(1f, NavigationEvent.EDGE_NONE, W, H, 0f, MARGIN_PX)
        assertFrame(none, 0.85f, 141.0f, 180.0f)
        val left = SystemBackMotion.closingFrame(1f, NavigationEvent.EDGE_LEFT, W, H, 0f, MARGIN_PX)
        assertThat(left.left + left.scale * W).isWithin(TOLERANCE).of(1059.0f)
    }

    @Test
    fun `the entering card grows from behind the edge`() {
        assertFrame(
            SystemBackMotion.enteringFrame(0f, W, H, 0f, 0f, MARGIN_PX, OFFSET_PX),
            0.95f,
            -225.0f,
            60.0f
        )
        assertFrame(
            SystemBackMotion.enteringFrame(1f, W, H, 0f, 0f, MARGIN_PX, OFFSET_PX),
            0.8075f,
            -148.05f,
            231.0f
        )
        assertThat(
            SystemBackMotion.enteringStartScale(H, 73.5f)
        ).isWithin(EASING_TOLERANCE).of(0.93875f)
        val wide = SystemBackMotion.enteringFrame(0f, 2000f, H, 0f, 0f, MARGIN_PX, OFFSET_PX)
        assertThat(wide.left).isWithin(TOLERANCE).of(0.025f * 2000f - 285f)
    }

    @Test
    fun `the vertical follow is clamped by the free space`() {
        assertThat(
            SystemBackMotion.yOffset(H, 0.85f * H, 600f, MARGIN_PX)
        ).isWithin(TOLERANCE).of(119.25f)
        assertThat(
            SystemBackMotion.yOffset(H, 0.85f * H, -300f, MARGIN_PX)
        ).isWithin(TOLERANCE).of(-69.5625f)
        assertThat(
            SystemBackMotion.yOffset(H, 0.85f * H, 1200f, MARGIN_PX)
        ).isWithin(TOLERANCE).of(159.0f)
        assertThat(
            SystemBackMotion.yOffset(H, 0.85f * H, 5000f, MARGIN_PX)
        ).isWithin(TOLERANCE).of(159.0f)
        assertThat(SystemBackMotion.yOffset(H, 0.85f * H, 0f, MARGIN_PX)).isWithin(TOLERANCE).of(0f)
    }

    @Test
    fun `the post commit time curve is accelerate decelerate`() {
        assertThat(
            SystemBackMotion.PostCommitEasing.transform(0f)
        ).isWithin(EASING_TOLERANCE).of(0f)
        assertThat(
            SystemBackMotion.PostCommitEasing.transform(0.25f)
        ).isWithin(EASING_TOLERANCE).of(0.1464f)
        assertThat(
            SystemBackMotion.PostCommitEasing.transform(0.5f)
        ).isWithin(EASING_TOLERANCE).of(0.5f)
        assertThat(
            SystemBackMotion.PostCommitEasing.transform(1f)
        ).isWithin(EASING_TOLERANCE).of(1f)
        val x = SystemBackMotion.PostCommitEasing.transform(0.2952f)
        assertThat(SystemBackMotion.closingPostCommitAlpha(x)).isWithin(EASING_TOLERANCE).of(0f)
    }

    @Test
    fun `the closing card fades out within a fifth of the post commit`() {
        assertThat(SystemBackMotion.closingPostCommitAlpha(0f)).isWithin(EASING_TOLERANCE).of(1f)
        assertThat(
            SystemBackMotion.closingPostCommitAlpha(0.1f)
        ).isWithin(EASING_TOLERANCE).of(0.5f)
        assertThat(SystemBackMotion.closingPostCommitAlpha(0.2f)).isWithin(EASING_TOLERANCE).of(0f)
        assertThat(SystemBackMotion.closingPostCommitAlpha(0.5f)).isWithin(EASING_TOLERANCE).of(0f)
    }

    @Test
    fun `the post commit frames end at their targets`() {
        val release = BackFrame(0.9f, 40f, 30f)
        assertFrame(
            SystemBackMotion.closingPostCommitFrame(release, 1f, OFFSET_PX),
            1f,
            release.left + 252f,
            0f
        )
        assertFrame(
            SystemBackMotion.enteringPostCommitFrame(release, 1f),
            BackFrame.Identity.scale,
            0f,
            0f
        )
    }

    @Test
    fun `the scrim and the corners follow the theme and the post commit`() {
        assertThat(SystemBackMotion.scrimAlpha(false)).isWithin(EASING_TOLERANCE).of(0.2f)
        assertThat(SystemBackMotion.scrimAlpha(true)).isWithin(EASING_TOLERANCE).of(0.8f)
        assertThat(
            SystemBackMotion.postCommitScrimAlpha(false, 0.25f)
        ).isWithin(EASING_TOLERANCE).of(0.15f)
        assertThat(
            SystemBackMotion.postCommitScrimAlpha(true, 0.25f)
        ).isWithin(EASING_TOLERANCE).of(0.6f)
        assertThat(SystemBackMotion.postCommitCornerPx(73.5f, 1f)).isWithin(EASING_TOLERANCE).of(0f)
    }

    @Test
    fun `the constants match the platform`() {
        assertThat(SYSTEM_BACK_DURATION_MS).isEqualTo(450)
        assertThat(FADE_DURATION_MS).isEqualTo(83)
        assertThat(CLOSE_FADE_DELAY_MS).isEqualTo(35)
        assertThat(OPEN_FADE_DELAY_MS).isEqualTo(50)
        assertThat(SystemBackMotion.ENTERING_OFFSET_DP).isEqualTo(96f)
        assertThat(SystemBackMotion.MARGIN_DP).isEqualTo(8f)
        assertThat(SystemBackMotion.MAX_SCALE).isEqualTo(0.85f)
        assertThat(SystemBackMotion.ENTERING_START_SCALE).isEqualTo(0.95f)
        assertThat(
            SystemBackMotion.dpToPx(SystemBackMotion.MARGIN_DP, DENSITY)
        ).isWithin(TOLERANCE).of(MARGIN_PX)
        val offset = SystemBackMotion.dpToPx(SystemBackMotion.ENTERING_OFFSET_DP, DENSITY)
        assertThat(offset).isWithin(TOLERANCE).of(OFFSET_PX)
    }

    @Test
    fun `reduced and predictive transforms have no enter and no exit`() {
        assertThat(reducedTransform().targetContentEnter).isEqualTo(EnterTransition.None)
        assertThat(reducedTransform().initialContentExit).isEqualTo(ExitTransition.None)
        assertThat(predictivePopTransform().targetContentEnter).isEqualTo(EnterTransition.None)
        assertThat(predictivePopTransform().initialContentExit).isEqualTo(ExitTransition.None)
    }
}
