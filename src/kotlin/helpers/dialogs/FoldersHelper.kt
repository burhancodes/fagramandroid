package xie.fa.gram.helpers.dialogs

import org.telegram.messenger.AndroidUtilities
import xie.fa.gram.InuConfig
import xie.fa.gram.helpers.theme.M3MainTabsHelper
import xie.fa.gram.helpers.theme.NonIslandHelper

object FoldersHelper {
    @JvmStatic
    fun isBottom(): Boolean =
        MainTabsHelper.isMaterial &&
            InuConfig.FOLDERS_BAR_POSITION.value == InuConfig.FoldersBarPositionItem.BOTTOM

    // Flush with M3 tabs (no gap); non-M3 still uses a small gap for visual separation
    @JvmStatic
    fun bottomSlotPx(navigationBarHeight: Int, additionNavigationBarHeight: Int): Int {
        val gap = if (MainTabsHelper.isMaterial) 0 else AndroidUtilities.dp(4f)
        return navigationBarHeight + additionNavigationBarHeight + gap
    }

    // M3 mode: match M3 bar height; classic/non-island: existing folder bar heights
    @JvmStatic
    fun barHeightDp(): Int = when {
        MainTabsHelper.isMaterial -> M3MainTabsHelper.barHeight
        NonIslandHelper.foldersBar() -> NonIslandHelper.FOLDERS_BAR_HEIGHT_DP
        else -> 36 + 14
    }

    @JvmStatic
    fun barHeightPx(): Int = AndroidUtilities.dp(barHeightDp().toFloat())
}
