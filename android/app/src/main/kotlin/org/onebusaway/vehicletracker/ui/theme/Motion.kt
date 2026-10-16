package org.onebusaway.vehicletracker.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.dp
import kotlin.math.sign

/**
 * The springs of Material's standard motion scheme that the app uses. material3 1.4.0 keeps `MotionScheme`
 * internal, so the values are copied from its `StandardMotionTokens`; on material3 1.5, where it is public,
 * these become `MaterialTheme.motionScheme.defaultSpatialSpec()` and `defaultEffectsSpec()`.
 */
internal object AppMotion {
    /** For what moves or changes size, such as the problem line opening under the status card. */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** For colour and opacity, such as the status card changing tone. It never overshoots. */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** The quicker of the two, for a swap the driver asked for, such as the departure board changing direction. */
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}

/** Material's emphasized decelerate curve, copied for the same reason as [AppMotion]; on material3 1.5 it is public. */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val SWITCH_MILLIS = 300
private const val OUT_MILLIS = 90
private const val IN_MILLIS = SWITCH_MILLIS - OUT_MILLIS

/** How far a setup screen slides as it comes in or goes. */
internal val SharedAxisSlide = 30.dp

/**
 * Material's shared axis X, for the steps of choosing a trip: a short slide, [slidePx] of it, under a quick
 * cross-fade, the old screen fading out before the new one fades in. The incoming screen slides [towards] the
 * start going forward and the end going back, so the motion follows the reading direction.
 */
internal fun AnimatedContentTransitionScope<*>.sharedAxisIn(
    towards: AnimatedContentTransitionScope.SlideDirection,
    slidePx: Int,
): EnterTransition =
    slideIntoContainer(towards, tween(SWITCH_MILLIS, easing = EmphasizedDecelerate)) { full -> full.sign * slidePx } +
        fadeIn(tween(IN_MILLIS, delayMillis = OUT_MILLIS))

/** The outgoing half of [sharedAxisIn]. */
internal fun AnimatedContentTransitionScope<*>.sharedAxisOut(
    towards: AnimatedContentTransitionScope.SlideDirection,
    slidePx: Int,
): ExitTransition =
    slideOutOfContainer(towards, tween(SWITCH_MILLIS, easing = EmphasizedDecelerate)) { full -> full.sign * slidePx } +
        fadeOut(tween(OUT_MILLIS))

/** Material's fade through, for a change of task rather than a step, such as starting the trip. */
internal fun fadeThroughIn(): EnterTransition = fadeIn(tween(IN_MILLIS, delayMillis = OUT_MILLIS))

/** The outgoing half of [fadeThroughIn]. */
internal fun fadeThroughOut(): ExitTransition = fadeOut(tween(OUT_MILLIS))
