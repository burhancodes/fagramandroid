package xie.fa.gram.ui.settings

import xie.fa.gram.InuConfig
import xie.fa.gram.helpers.menu.ChatMenuConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

class ChatMenuOrderActivity : MenuOrderActivity<ChatMenuConfig.Item>() {
    override val config get() = InuConfig.CHAT_MENU_ITEMS
    override val infoStringRes = R.string.InuChatMenuOrderInfo
    override val headerStringRes = R.string.InuChatMenuItems
    override val resetStringRes = R.string.InuChatMenuReset

    override fun getTitle(): CharSequence = LocaleController.getString(R.string.InuChatMenuOrder)
}
