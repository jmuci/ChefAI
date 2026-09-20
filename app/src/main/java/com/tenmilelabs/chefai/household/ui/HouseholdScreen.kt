package com.tenmilelabs.chefai.household.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.RuledGroup
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import java.util.UUID

@Composable
fun HouseholdScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onEnterInviteCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HouseholdViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingInvites.collectAsStateWithLifecycle()
    val outstandingInvites by viewModel.outstandingInvites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HouseholdEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is HouseholdEvent.ShareInviteLink -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.link.url)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            sendIntent,
                            context.getString(R.string.household_share_chooser_title),
                        )
                    )
                }
                HouseholdEvent.InviteSent ->
                    snackbarHostState.showSnackbar(context.getString(R.string.household_invite_sent))
                HouseholdEvent.LeftHousehold -> onNavigateBack()
                // The pending-invite list and (on accept) the household itself are both live Room
                // flows already driving this screen's state — nothing further to do here.
                HouseholdEvent.PendingInviteResolved -> Unit
            }
        }
    }

    when (val state = uiState) {
        HouseholdUiState.Loading -> LoadingContent(modifier = modifier)
        HouseholdUiState.NoHousehold -> HouseholdCreateContent(
            pendingInvites = pendingInvites,
            onCreateHousehold = viewModel::onCreateHousehold,
            onAcceptInvite = viewModel::onAcceptPendingInvite,
            onDeclineInvite = viewModel::onDeclinePendingInvite,
            onEnterInviteCode = onEnterInviteCode,
            modifier = modifier,
        )
        is HouseholdUiState.Success -> HouseholdContent(
            household = state.household,
            myRole = state.myRole,
            currentUserId = state.currentUserId,
            outstandingInvites = outstandingInvites,
            onInviteClick = viewModel::onInviteByLink,
            onInviteByEmail = viewModel::onInviteByEmail,
            onRemoveMember = viewModel::onRemoveMember,
            onRevokeInvite = viewModel::onRevokeInvite,
            onLeaveClick = viewModel::onLeaveHousehold,
            modifier = modifier,
        )
        is HouseholdUiState.Error -> HouseholdErrorContent(
            message = state.message,
            onRetry = viewModel::onRefresh,
            modifier = modifier,
        )
    }
}

@Composable
fun HouseholdContent(
    household: Household,
    myRole: HouseholdRole,
    currentUserId: UUID?,
    outstandingInvites: List<HouseholdInvite>,
    onInviteClick: () -> Unit,
    onInviteByEmail: (String) -> Unit,
    onRemoveMember: (UUID) -> Unit,
    onRevokeInvite: (UUID) -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInviteEmailDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<HouseholdMember?>(null) }
    val isOwner = myRole == HouseholdRole.OWNER

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = household.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.household_members_count,
                        household.members.size,
                        household.members.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RuledGroup(items = household.members, key = { it.userId }) { member ->
                HouseholdMemberRow(
                    member = member,
                    isCurrentUser = member.userId == currentUserId,
                    canRemove = isOwner && member.role != HouseholdRole.OWNER,
                    onRemove = { memberPendingRemoval = member },
                )
            }

            if (isOwner) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlatBlockButton(
                        text = stringResource(R.string.household_invite_link_button),
                        onClick = onInviteClick,
                        leadingIcon = ChefAIIcons.Link,
                        modifier = Modifier.weight(1f),
                    )
                    FlatBlockButton(
                        text = stringResource(R.string.household_invite_email_button),
                        onClick = { showInviteEmailDialog = true },
                        variant = FlatButtonVariant.Secondary,
                        leadingIcon = ChefAIIcons.Mail,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (outstandingInvites.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.household_pending_invites_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.chefColors.accentText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    RuledGroup(items = outstandingInvites, key = { it.inviteId }) { invite ->
                        OutstandingInviteRow(
                            invite = invite,
                            onRevoke = { onRevokeInvite(invite.inviteId) },
                        )
                    }
                }
            }
        }

        FlatBlockButton(
            text = stringResource(R.string.household_leave_button),
            onClick = { showLeaveConfirm = true },
            variant = FlatButtonVariant.Destructive,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (showInviteEmailDialog) {
        InviteByEmailDialog(
            onConfirm = { email ->
                onInviteByEmail(email)
                showInviteEmailDialog = false
            },
            onDismiss = { showInviteEmailDialog = false },
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.household_leave_confirm_title)) },
            text = { Text(stringResource(R.string.household_leave_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { showLeaveConfirm = false; onLeaveClick() }) {
                    Text(
                        stringResource(R.string.household_leave_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }

    memberPendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text(stringResource(R.string.household_remove_member_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.household_remove_member_confirm_message,
                        member.displayName,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { memberPendingRemoval = null; onRemoveMember(member.userId) }) {
                    Text(
                        stringResource(R.string.household_remove_member_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }
}

@Composable
private fun InviteByEmailDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_invite_email_dialog_title)) },
        text = {
            FlatField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.household_invite_email_dialog_label),
                leadingIcon = ChefAIIcons.Mail,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim()) },
                enabled = email.isNotBlank(),
            ) {
                Text(stringResource(R.string.household_invite_email_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        },
    )
}

/** The owner's-eye row for one of their own outstanding invites — email, expiry, ghost Revoke. */
@Composable
private fun OutstandingInviteRow(
    invite: HouseholdInvite,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.inviteeEmail ?: stringResource(R.string.household_pending_invite_link_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(
                    R.string.household_pending_invite_expires,
                    DateUtils.formatDateTime(
                        context,
                        invite.expiresAt,
                        DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlatButton(
            text = stringResource(R.string.household_revoke_invite_button),
            onClick = onRevoke,
            variant = FlatButtonVariant.Ghost,
        )
    }
}

@Composable
private fun HouseholdCreateContent(
    pendingInvites: List<PendingHouseholdInvite>,
    onCreateHousehold: (String) -> Unit,
    onAcceptInvite: (UUID) -> Unit,
    onDeclineInvite: (UUID) -> Unit,
    onEnterInviteCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.household_no_household_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.household_no_household_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        FlatField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.household_create_name_label),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FlatBlockButton(
            text = stringResource(R.string.household_create_button),
            onClick = { onCreateHousehold(name) },
            enabled = name.isNotBlank(),
        )

        FlatBlockButton(
            text = stringResource(R.string.household_enter_code_button),
            onClick = onEnterInviteCode,
            variant = FlatButtonVariant.Ghost,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (pendingInvites.isNotEmpty()) {
            Text(
                text = stringResource(R.string.household_pending_invites_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.chefColors.accentText,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            RuledGroup(items = pendingInvites, key = { it.inviteId }) { invite ->
                InboxInviteRow(
                    invite = invite,
                    onAccept = { onAcceptInvite(invite.inviteId) },
                    onDecline = { onDeclineInvite(invite.inviteId) },
                )
            }
        }
    }
}

/** The invitee's-eye row for an in-app invite addressed to this user — see [PendingHouseholdInvite]. */
@Composable
private fun InboxInviteRow(
    invite: PendingHouseholdInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.household_pending_invite_expires,
                DateUtils.getRelativeTimeSpanString(
                    invite.expiresAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        FlatButton(
            text = stringResource(R.string.household_decline_button),
            onClick = onDecline,
            variant = FlatButtonVariant.Ghost,
        )
        FlatButton(
            text = stringResource(R.string.household_accept_button),
            onClick = onAccept,
        )
    }
}

@Composable
private fun HouseholdErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.household_error_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        FlatBlockButton(
            text = stringResource(R.string.household_retry_button),
            onClick = onRetry,
        )
    }
}

// ── previews ──────────────────────────────────────────────────────────────────────────────────

private fun previewHousehold() = Household(
    uuid = UUID.randomUUID(),
    name = "The Muci Kitchen",
    ownerId = UUID.randomUUID(),
    members = listOf(
        HouseholdMember(UUID.randomUUID(), "JM Muci", "", HouseholdRole.OWNER),
        HouseholdMember(UUID.randomUUID(), "Ana Muci", "", HouseholdRole.MEMBER),
        HouseholdMember(UUID.randomUUID(), "Luca Pini", "", HouseholdRole.MEMBER),
    ),
)

private fun previewOutstandingInvite() = HouseholdInvite(
    inviteId = UUID.randomUUID(),
    inviteeEmail = "sam@example.com",
    singleUse = true,
    maxUses = null,
    useCount = 0,
    expiresAt = System.currentTimeMillis() + 4 * DateUtils.DAY_IN_MILLIS,
    createdAt = System.currentTimeMillis(),
)

@Preview(name = "Household – owner, light", showBackground = true)
@Composable
private fun HouseholdContentOwnerPreview() {
    val household = previewHousehold()
    ChefAITheme {
        HouseholdContent(
            household = household,
            myRole = HouseholdRole.OWNER,
            currentUserId = household.members.first().userId,
            outstandingInvites = listOf(previewOutstandingInvite()),
            onInviteClick = {},
            onInviteByEmail = {},
            onRemoveMember = {},
            onRevokeInvite = {},
            onLeaveClick = {},
        )
    }
}

@Preview(
    name = "Household – member, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdContentMemberDarkPreview() {
    val household = previewHousehold()
    ChefAITheme {
        HouseholdContent(
            household = household,
            myRole = HouseholdRole.MEMBER,
            currentUserId = household.members[1].userId,
            outstandingInvites = emptyList(),
            onInviteClick = {},
            onInviteByEmail = {},
            onRemoveMember = {},
            onRevokeInvite = {},
            onLeaveClick = {},
        )
    }
}

@Preview(name = "Household – no household, light", showBackground = true)
@Composable
private fun HouseholdCreateContentPreview() {
    ChefAITheme {
        HouseholdCreateContent(
            pendingInvites = emptyList(),
            onCreateHousehold = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
            onEnterInviteCode = {},
        )
    }
}

@Preview(
    name = "Household – no household, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdCreateContentDarkPreview() {
    ChefAITheme {
        HouseholdCreateContent(
            pendingInvites = emptyList(),
            onCreateHousehold = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
            onEnterInviteCode = {},
        )
    }
}

private fun previewPendingInvite() = PendingHouseholdInvite(
    inviteId = UUID.randomUUID(),
    householdId = UUID.randomUUID(),
    expiresAt = System.currentTimeMillis() + 3 * DateUtils.DAY_IN_MILLIS,
    createdAt = System.currentTimeMillis(),
)

@Preview(name = "Household – pending invites, light", showBackground = true)
@Composable
private fun HouseholdCreateContentWithInvitesPreview() {
    ChefAITheme {
        HouseholdCreateContent(
            pendingInvites = listOf(previewPendingInvite()),
            onCreateHousehold = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
            onEnterInviteCode = {},
        )
    }
}

@Preview(
    name = "Household – pending invites, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdCreateContentWithInvitesDarkPreview() {
    ChefAITheme {
        HouseholdCreateContent(
            pendingInvites = listOf(previewPendingInvite()),
            onCreateHousehold = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
            onEnterInviteCode = {},
        )
    }
}
