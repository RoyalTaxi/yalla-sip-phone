package uz.yalla.sipphone.showcase

import androidx.compose.runtime.Composable

/**
 * One preview variant of a composable — a named state rendered in isolation.
 *
 * Kept deliberately dumb: just a name and a `@Composable () -> Unit`. All the setup (fake
 * engines, theme providers, test scopes) is done once in [ShowcaseApp]; individual cases just
 * render with static props.
 */
data class Case(
    val name: String,
    val content: @Composable () -> Unit,
)

/** A catalog entry — one composable component with a list of state variants to preview. */
data class ComponentEntry(
    val name: String,
    val cases: List<Case>,
)
