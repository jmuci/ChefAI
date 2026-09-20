package com.tenmilelabs.chefai.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Zero radius, everywhere.
 *
 * "Radius: 0px everywhere — no rounded corners, by design-system rule." Setting it once here means
 * every Material component that resolves its shape from the theme — `Button`, `Card`, `Surface`,
 * `TextField`, `Chip`, `AlertDialog`, `DropdownMenu`, `FloatingActionButton` — comes out square
 * without a single `shape =` argument at any call site. A screen that passes
 * `RoundedCornerShape(0.dp)` by hand is duplicating the theme, not obeying it.
 *
 * The one exception in the whole design is the user avatar, which stays a circle. It is a circle
 * because it is an avatar, not because it is a container: use `CircleShape` there explicitly.
 */
val ChefShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
