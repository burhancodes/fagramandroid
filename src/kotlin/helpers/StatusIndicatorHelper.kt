package xie.fa.gram.helpers

import org.telegram.messenger.ContactsController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import xie.fa.gram.InuConfig

object StatusIndicatorHelper {

    @JvmStatic
    fun getOnlineColor(user: TLRPC.User?, resourcesProvider: Theme.ResourcesProvider?): Int {
        if (!InuConfig.AVATAR_ONLINE_STATUS.value) return 0
        if (user == null || user.status == null || user.bot == true || user.self == true) return 0
        val currentTime = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime()

        if (user.status.expires <= 0) {
            val allowed = MessagesController.getInstance(UserConfig.selectedAccount)
                .onlinePrivacy.containsKey(user.id)
            if (allowed) {
                return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider)
            }
            return 0
        }

        val diff = user.status.expires - currentTime
        return when {
            diff > 0 -> Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider)
            diff > -15 * 60 -> Theme.getColor(Theme.key_chats_onlineCircleRecencyTier1, resourcesProvider)
            diff > -30 * 60 -> Theme.getColor(Theme.key_chats_onlineCircleRecencyTier2, resourcesProvider)
            diff > -60 * 60 -> Theme.getColor(Theme.key_chats_onlineCircleRecencyTier3, resourcesProvider)
            else -> 0
        }
    }

}
