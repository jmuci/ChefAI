package com.tenmilelabs.chefai.household.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.ui.components.flat.AvatarTone
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTag
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTagTone
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.SquareAvatar
import com.tenmilelabs.chefai.core.ui.components.flat.avatarInitials
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import java.util.UUID

/**
 * One row in a household's member list — screen 11.
 *
 * @param canRemove `myRole == OWNER && member.role != OWNER` — an owner can't remove themselves
 *   here; that's [HouseholdContent]'s separate leave action, available to every role.
 * @param isCurrentUser drives the "you" subtitle. The handoff shows the member's email there, but
 *   [HouseholdMember] never carries one — the server denormalizes only `displayName`/`avatarUrl`
 *   onto a roster entry — so every other row falls back to its role label instead of a fabricated
 *   address.
 */
@Composable
fun HouseholdMemberRow(
    member: HouseholdMember,
    isCurrentUser: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOwner = member.role == HouseholdRole.OWNER

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinHitTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareAvatar(
            initials = avatarInitials(member.displayName),
            tone = if (isOwner) AvatarTone.Accent else AvatarTone.Neutral,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(
                    if (isCurrentUser) R.string.household_you_label else R.string.household_member_label,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            isOwner -> FlatTag(text = stringResource(R.string.household_owner_label), tone = FlatTagTone.Accent)
            canRemove -> FlatButton(
                text = stringResource(R.string.household_remove_member_button),
                onClick = onRemove,
                variant = FlatButtonVariant.Secondary,
            )
        }
    }
}

@Preview(name = "Member row – owner, light", showBackground = true)
@Composable
private fun HouseholdMemberRowOwnerPreview() {
    ChefAITheme {
        HouseholdMemberRow(
            member = HouseholdMember(
                userId = UUID.randomUUID(),
                displayName = "JM Muci",
                avatarUrl = "",
                role = HouseholdRole.OWNER,
            ),
            isCurrentUser = true,
            canRemove = false,
            onRemove = {},
        )
    }
}

@Preview(
    name = "Member row – removable member, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdMemberRowRemovableDarkPreview() {
    ChefAITheme {
        HouseholdMemberRow(
            member = HouseholdMember(
                userId = UUID.randomUUID(),
                displayName = "Ana Muci",
                avatarUrl = "",
                role = HouseholdRole.MEMBER,
            ),
            isCurrentUser = false,
            canRemove = true,
            onRemove = {},
        )
    }
}
