package xie.fa.gram.helpers.dialogs

import org.telegram.messenger.AndroidUtilities
import xie.fa.gram.InuConfig
import xie.fa.gram.helpers.theme.NonIslandHelper

object FoldersHelper {
    @JvmStatic
    fun isBottom(): Boolean =
        InuConfig.FOLDERS_BAR_POSITION.value == InuConfig.FoldersBarPositionItem.BOTTOM

    // Vertical distance from the very bottom of the dialogs fragment to the folder bar's
    // bottom edge: nav bar + main-tabs slot + a small gap above the tabs.
    @JvmStatic
    fun bottomSlotPx(navigationBarHeight: Int, additionNavigationBarHeight: Int): Int =
        navigationBarHeight + additionNavigationBarHeight + AndroidUtilities.dp(4f)

    // Height of the folder bar itself (dp), used to reserve space above it (FABs, bulletins).
    @JvmStatic
    fun barHeightDp(): Int =
        if (NonIslandHelper.foldersBar()) NonIslandHelper.FOLDERS_BAR_HEIGHT_DP else 36 + 14

    @JvmStatic
    fun barHeightPx(): Int = AndroidUtilities.dp(barHeightDp().toFloat())
}