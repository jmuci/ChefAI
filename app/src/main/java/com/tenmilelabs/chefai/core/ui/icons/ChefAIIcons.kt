package com.tenmilelabs.chefai.core.ui.icons

import androidx.annotation.DrawableRes
import com.tenmilelabs.chefai.R

/**
 * Semantic lookup for the vendored Lucide icon set (see NOTICE, tools/lucide-import.sh).
 * Call sites must reference these, never `R.drawable.ic_lucide_*` directly, so a later
 * icon swap is a one-line change here instead of a grep-and-replace across the app.
 */
object ChefAIIcons {
    @DrawableRes val ArrowLeft: Int = R.drawable.ic_lucide_arrow_left
    @DrawableRes val ArrowDown: Int = R.drawable.ic_lucide_arrow_down
    @DrawableRes val ArrowUp: Int = R.drawable.ic_lucide_arrow_up
    @DrawableRes val Bell: Int = R.drawable.ic_lucide_bell
    @DrawableRes val BookOpen: Int = R.drawable.ic_lucide_book_open
    @DrawableRes val Bookmark: Int = R.drawable.ic_lucide_bookmark
    @DrawableRes val BookmarkFilled: Int = R.drawable.ic_lucide_bookmark_filled
    @DrawableRes val Check: Int = R.drawable.ic_lucide_check
    @DrawableRes val ChefHat: Int = R.drawable.ic_lucide_chef_hat
    @DrawableRes val ChevronDown: Int = R.drawable.ic_lucide_chevron_down
    @DrawableRes val CircleAlert: Int = R.drawable.ic_lucide_circle_alert
    @DrawableRes val CircleUserRound: Int = R.drawable.ic_lucide_circle_user_round
    @DrawableRes val Clock: Int = R.drawable.ic_lucide_clock
    @DrawableRes val CloudAlert: Int = R.drawable.ic_lucide_cloud_alert
    @DrawableRes val CloudCheck: Int = R.drawable.ic_lucide_cloud_check
    @DrawableRes val CloudCog: Int = R.drawable.ic_lucide_cloud_cog
    @DrawableRes val CloudOff: Int = R.drawable.ic_lucide_cloud_off
    @DrawableRes val CookingPot: Int = R.drawable.ic_lucide_cooking_pot
    @DrawableRes val Download: Int = R.drawable.ic_lucide_download
    @DrawableRes val Eye: Int = R.drawable.ic_lucide_eye
    @DrawableRes val EyeOff: Int = R.drawable.ic_lucide_eye_off
    @DrawableRes val Globe: Int = R.drawable.ic_lucide_globe
    @DrawableRes val GripVertical: Int = R.drawable.ic_lucide_grip_vertical
    @DrawableRes val House: Int = R.drawable.ic_lucide_house
    @DrawableRes val Image: Int = R.drawable.ic_lucide_image
    @DrawableRes val ImageOff: Int = R.drawable.ic_lucide_image_off
    @DrawableRes val ImagePlus: Int = R.drawable.ic_lucide_image_plus
    @DrawableRes val Link: Int = R.drawable.ic_lucide_link
    @DrawableRes val Lock: Int = R.drawable.ic_lucide_lock
    @DrawableRes val LogOut: Int = R.drawable.ic_lucide_log_out
    @DrawableRes val Mail: Int = R.drawable.ic_lucide_mail
    @DrawableRes val Minus: Int = R.drawable.ic_lucide_minus
    @DrawableRes val Pause: Int = R.drawable.ic_lucide_pause
    @DrawableRes val Pencil: Int = R.drawable.ic_lucide_pencil
    @DrawableRes val Play: Int = R.drawable.ic_lucide_play
    @DrawableRes val Plus: Int = R.drawable.ic_lucide_plus
    @DrawableRes val Printer: Int = R.drawable.ic_lucide_printer
    @DrawableRes val Search: Int = R.drawable.ic_lucide_search
    @DrawableRes val Settings: Int = R.drawable.ic_lucide_settings
    @DrawableRes val ShoppingCart: Int = R.drawable.ic_lucide_shopping_cart
    @DrawableRes val Sparkles: Int = R.drawable.ic_lucide_sparkles
    @DrawableRes val SquarePen: Int = R.drawable.ic_lucide_square_pen
    @DrawableRes val Timer: Int = R.drawable.ic_lucide_timer
    @DrawableRes val Trash: Int = R.drawable.ic_lucide_trash
    @DrawableRes val UserMinus: Int = R.drawable.ic_lucide_user_minus
    @DrawableRes val Users: Int = R.drawable.ic_lucide_users
    @DrawableRes val X: Int = R.drawable.ic_lucide_x
}
