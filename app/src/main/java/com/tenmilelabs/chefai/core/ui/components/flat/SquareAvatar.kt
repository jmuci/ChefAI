package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/** Who the tile is: the accent fill marks the household owner, neutral marks everyone else. */
enum class AvatarTone {
    /** Accent fill, ground-colored initials. The owner, or the signed-in user. */
    Accent,

    /** Neutral-200 fill, deep-neutral initials. Other members. */
    Neutral,
}

/**
 * A 36dp **square** initials tile — the member avatar used in lists.
 *
 * Square, because in this system a container has no radius. The circle is reserved for the one
 * avatar that is genuinely a portrait slot: see [CircleAvatar].
 *
 * ```
 * SquareAvatar(
 *     initials = avatarInitials(member.displayName),
 *     tone = if (member.isOwner) AvatarTone.Accent else AvatarTone.Neutral,
 * )
 * ```
 *
 * The tile is decorative — the name is always next to it in the row — so it is hidden from
 * accessibility services rather than making TalkBack spell out two letters.
 *
 * Used on household (11) and accept invite (12), where the stack overlaps tiles by −2dp.
 */
@Composable
fun SquareAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    tone: AvatarTone = AvatarTone.Neutral,
    size: Dp = SquareAvatarSize,
) {
    AvatarTile(
        initials = initials,
        containerColor = tone.containerColor(),
        contentColor = tone.contentColor(),
        size = size,
        modifier = modifier,
    )
}

/**
 * The 40dp **circular** avatar — the one exception to zero radius in the entire design.
 *
 * It is a circle because it is an avatar, not because it is a container. Use it only for the
 * signed-in user's own avatar in a header or the profile menu (screens 01 and 09). Every other
 * initials tile is a [SquareAvatar].
 */
@Composable
fun CircleAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = CircleAvatarSize,
) {
    AvatarTile(
        initials = initials,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        size = size,
        modifier = modifier,
        circular = true,
    )
}

@Composable
private fun AvatarTile(
    initials: String,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    modifier: Modifier,
    circular: Boolean = false,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .size(size)
            .then(
                if (circular) {
                    Modifier.background(containerColor, CircleShape)
                } else {
                    Modifier.background(containerColor)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            // Initials are uppercase by construction, so the uppercase label role — 13/800 with
            // .08em of tracking — is the right one: two letters need the air.
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * One or two uppercase letters for [name] — the first letter of the first and last words, or the
 * first two characters of a single word. Blank input gives `"?"` rather than an empty tile.
 *
 * Feed it a display name. An email address gives its local part's first letters, which is the
 * right answer for an account that has no name yet.
 */
fun avatarInitials(name: String): String {
    val words = name.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

@Composable
private fun AvatarTone.containerColor(): Color = when (this) {
    AvatarTone.Accent -> MaterialTheme.colorScheme.primary
    AvatarTone.Neutral -> MaterialTheme.chefColors.neutral.s200
}

@Composable
private fun AvatarTone.contentColor(): Color = when (this) {
    AvatarTone.Accent -> MaterialTheme.colorScheme.onPrimary
    AvatarTone.Neutral -> MaterialTheme.chefColors.neutral.s800
}

private val SquareAvatarSize = 36.dp
private val CircleAvatarSize = 40.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun AvatarPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Square — accent (owner) / neutral")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareAvatar(initials = avatarInitials("Ana Muci"), tone = AvatarTone.Accent)
            SquareAvatar(initials = avatarInitials("Jose Mucientes"))
            SquareAvatar(initials = avatarInitials("sam@example.com"))
        }
        PreviewStateLabel("Square — 34dp stack, overlapped (accept invite)")
        Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            SquareAvatar(initials = "AM", tone = AvatarTone.Accent, size = 34.dp)
            SquareAvatar(initials = "JM", size = 34.dp)
            SquareAvatar(initials = "SK", size = 34.dp)
        }
        PreviewStateLabel("Circle — the one exception to zero radius")
        CircleAvatar(initials = avatarInitials("Ana Muci"))
    }
}
