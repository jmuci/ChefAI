package com.tenmilelabs.chefai.household.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import java.util.UUID

/**
 * One row in a household's member list.
 *
 * @param canRemove `myRole == OWNER && member.role != OWNER` — an owner can't remove themselves
 *   here; that's [HouseholdContent]'s separate leave action, available to every role.
 */
@Composable
fun HouseholdMemberRow(
    member: HouseholdMember,
    canRemove: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.padding_medium),
                vertical = dimensionResource(R.dimen.padding_small),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = member.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (member.role == HouseholdRole.OWNER) {
                        R.string.household_owner_label
                    } else {
                        R.string.household_member_label
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = stringResource(R.string.household_remove_member_button),
                )
            }
        }
    }
}

@Preview(name = "Member row – light", showBackground = true)
@Composable
private fun HouseholdMemberRowPreview() {
    ChefAITheme {
        Surface {
            HouseholdMemberRow(
                member = HouseholdMember(
                    userId = UUID.randomUUID(),
                    displayName = "Chef Owner",
                    avatarUrl = "",
                    role = HouseholdRole.OWNER,
                ),
                canRemove = false,
                onRemove = {},
            )
        }
    }
}

@Preview(
    name = "Member row – dark, removable",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdMemberRowRemovableDarkPreview() {
    ChefAITheme {
        Surface {
            HouseholdMemberRow(
                member = HouseholdMember(
                    userId = UUID.randomUUID(),
                    displayName = "Chef Member",
                    avatarUrl = "",
                    role = HouseholdRole.MEMBER,
                ),
                canRemove = true,
                onRemove = {},
            )
        }
    }
}
