package com.tenmilelabs.chefai.core.domain.model

/**
 * A user's role within a household — see ADR-014. Only `OWNER` may invite, remove members, rename
 * or delete the household; both roles may edit the household's shared meal plan and grocery list.
 *
 * Lives in `core/domain/model/` rather than `household/domain/model/` because [UserSession]
 * (in `auth/`) needs it too — see ADR-005's rule: start in a feature package, promote to `core/`
 * only once a second feature needs the type.
 */
enum class HouseholdRole { OWNER, MEMBER }
