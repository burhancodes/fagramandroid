package xie.fa.gram.helpers.lastfm

import android.text.TextUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account

object LastFmBioSyncHelper {

    private val TAG_REGEX = Regex("""(?:^|\s+)#np:[a-zA-Z0-9_\-]+(?=\s|$)""")
    private const val DEBOUNCE_MS = 1000L

    private val syncRunnable = Runnable {
        syncNow()
    }

    /**
     * Called when the username setting changes.
     * Trailing-edge debounce with 1000ms delay.
     */
    @JvmStatic
    fun onUsernameChanged() {
        AndroidUtilities.cancelRunOnUIThread(syncRunnable)
        AndroidUtilities.runOnUIThread(syncRunnable, DEBOUNCE_MS)
    }

    /**
     * Called when the "Show on my profile" toggle flips.
     * Executes immediately without debounce.
     */
    @JvmStatic
    fun onShowOnProfileToggled() {
        AndroidUtilities.cancelRunOnUIThread(syncRunnable)
        syncNow()
    }

    fun cleanBio(bio: String?): String {
        if (bio.isNullOrEmpty()) return ""
        return bio.replace(TAG_REGEX, " ").trim()
    }

    private fun truncateToCodePoints(text: String, maxCodePoints: Int): String {
        if (maxCodePoints <= 0) return ""
        var codePoints = 0
        var offset = 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            codePoints++
            if (codePoints > maxCodePoints) {
                return text.substring(0, offset)
            }
            offset += Character.charCount(cp)
        }
        return text
    }

    private fun codePointCount(text: String): Int {
        return Character.codePointCount(text, 0, text.length)
    }

    fun computeTargetBio(currentBio: String, username: String, showOnProfile: Boolean, limit: Int): String {
        val cleaned = cleanBio(currentBio)
        val trimmedUser = username.trim()

        if (!showOnProfile || trimmedUser.isEmpty()) {
            return cleaned
        }

        val tag = "#np:$trimmedUser"
        if (cleaned.isEmpty()) {
            return if (codePointCount(tag) <= limit) tag else truncateToCodePoints(tag, limit)
        }

        val candidate = "$cleaned $tag"
        if (codePointCount(candidate) <= limit) {
            return candidate
        }

        val tagLen = codePointCount(tag)
        val availableForBio = limit - 1 - tagLen // 1 for space
        if (availableForBio > 0) {
            val truncatedBio = truncateToCodePoints(cleaned, availableForBio).trimEnd()
            if (truncatedBio.isNotEmpty()) {
                return "$truncatedBio $tag"
            }
        }
        return truncateToCodePoints(tag, limit)
    }

    @JvmStatic
    fun syncNow() {
        val currentAccount = UserConfig.selectedAccount
        val clientUserId = UserConfig.getInstance(currentAccount).clientUserId
        if (clientUserId == 0L) return

        val messagesController = MessagesController.getInstance(currentAccount)
        val userFull = messagesController.getUserFull(clientUserId) ?: return
        val currentBio = userFull.about ?: ""

        val username = LastFmStorage.username
        val showOnProfile = LastFmStorage.showOnProfile
        val limit = messagesController.aboutLimit

        val targetBio = computeTargetBio(currentBio, username, showOnProfile, limit)
        if (TextUtils.equals(currentBio, targetBio)) {
            return
        }

        val req = TL_account.updateProfile().apply {
            about = targetBio
            flags = flags or 4
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
            if (error == null && response is TLRPC.User) {
                AndroidUtilities.runOnUIThread {
                    userFull.about = targetBio
                    NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.userInfoDidLoad, response.id, userFull
                    )
                    val users = arrayListOf(response)
                    messagesController.putUsers(users, false)
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true)
                }
            }
        }
    }
}
