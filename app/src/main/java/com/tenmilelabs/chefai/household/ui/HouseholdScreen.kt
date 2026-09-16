package com.tenmilelabs.chefai.household.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.household.domain.model.Household
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
            onInviteClick = viewModel::onInviteByLink,
            onInviteByEmail = viewModel::onInviteByEmail,
            onRemoveMember = viewModel::onRemoveMember,
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
    onInviteClick: () -> Unit,
    onInviteByEmail: (String) -> Unit,
    onRemoveMember: (UUID) -> Unit,
    onLeaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInviteEmailDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<HouseholdMember?>(null) }
    val isOwner = myRole == HouseholdRole.OWNER

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = household.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.padding_medium)),
        ) {
            items(household.members, key = { it.userId }) { member ->
                HouseholdMemberRow(
                    member = member,
                    canRemove = isOwner && member.role != HouseholdRole.OWNER,
                    onRemove = { memberPendingRemoval = member },
                )
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            if (isOwner) {
                Button(onClick = onInviteClick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Text(
                        text = stringResource(R.string.household_invite_link_button),
                        modifier = Modifier.padding(start = dimensionResource(R.dimen.padding_small)),
                    )
                }
                OutlinedButton(
                    onClick = { showInviteEmailDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Text(
                        text = stringResource(R.string.household_invite_email_button),
                        modifier = Modifier.padding(start = dimensionResource(R.dimen.padding_small)),
                    )
                }
            }
            OutlinedButton(onClick = { showLeaveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Text(
                    text = stringResource(R.string.household_leave_button),
                    modifier = Modifier.padding(start = dimensionResource(R.dimen.padding_small)),
                )
            }
        }
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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.household_invite_email_dialog_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
            .verticalScroll(rememberScrollState())
            .padding(dimensionResource(R.dimen.padding_large)),
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_medium)),
        )
        Text(
            text = stringResource(R.string.household_no_household_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.household_no_household_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = dimensionResource(R.dimen.padding_small),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.household_create_name_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.padding_medium)),
        )
        Button(
            onClick = { onCreateHousehold(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.household_create_button))
        }

        OutlinedButton(
            onClick = onEnterInviteCode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_small)),
        ) {
            Text(stringResource(R.string.household_enter_code_button))
        }

        if (pendingInvites.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = dimensionResource(R.dimen.padding_large)),
            )
            Text(
                text = stringResource(R.string.household_pending_invites_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small)),
            )
            pendingInvites.forEach { invite ->
                PendingInviteRow(
                    invite = invite,
                    onAccept = { onAcceptInvite(invite.inviteId) },
                    onDecline = { onDeclineInvite(invite.inviteId) },
                )
            }
        }
    }
}

@Composable
private fun PendingInviteRow(
    invite: PendingHouseholdInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.padding_small)),
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
        )
        Column(
            modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_extra_small)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_extra_small)),
        ) {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.household_accept_button))
            }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.household_decline_button))
            }
        }
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
            .padding(dimensionResource(R.dimen.padding_large)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.household_error_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = dimensionResource(R.dimen.padding_small),
                bottom = dimensionResource(R.dimen.padding_medium),
            ),
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.household_retry_button))
        }
    }
}

private fun previewHousehold() = Household(
    uuid = UUID.randomUUID(),
    name = "The Test Kitchen",
    ownerId = UUID.randomUUID(),
    members = listOf(
        HouseholdMember(UUID.randomUUID(), "Chef Owner", "", HouseholdRole.OWNER),
        HouseholdMember(UUID.randomUUID(), "Chef Member", "", HouseholdRole.MEMBER),
    ),
)

@Preview(name = "Household – owner, light", showBackground = true)
@Composable
private fun HouseholdContentOwnerPreview() {
    ChefAITheme {
        Surface {
            HouseholdContent(
                household = previewHousehold(),
                myRole = HouseholdRole.OWNER,
                onInviteClick = {},
                onInviteByEmail = {},
                onRemoveMember = {},
                onLeaveClick = {},
            )
        }
    }
}

@Preview(
    name = "Household – member, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HouseholdContentMemberDarkPreview() {
    ChefAITheme {
        Surface {
            HouseholdContent(
                household = previewHousehold(),
                myRole = HouseholdRole.MEMBER,
                onInviteClick = {},
                onInviteByEmail = {},
                onRemoveMember = {},
                onLeaveClick = {},
            )
        }
    }
}

@Preview(name = "Household – no household, light", showBackground = true)
@Composable
private fun HouseholdCreateContentPreview() {
    ChefAITheme {
        Surface {
            HouseholdCreateContent(
                pendingInvites = emptyList(),
                onCreateHousehold = {},
                onAcceptInvite = {},
                onDeclineInvite = {},
                onEnterInviteCode = {},
            )
        }
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
        Surface {
            HouseholdCreateContent(
                pendingInvites = emptyList(),
                onCreateHousehold = {},
                onAcceptInvite = {},
                onDeclineInvite = {},
                onEnterInviteCode = {},
            )
        }
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
        Surface {
            HouseholdCreateContent(
                pendingInvites = listOf(previewPendingInvite()),
                onCreateHousehold = {},
                onAcceptInvite = {},
                onDeclineInvite = {},
                onEnterInviteCode = {},
            )
        }
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
        Surface {
            HouseholdCreateContent(
                pendingInvites = listOf(previewPendingInvite()),
                onCreateHousehold = {},
                onAcceptInvite = {},
                onDeclineInvite = {},
                onEnterInviteCode = {},
            )
        }
    }
}
