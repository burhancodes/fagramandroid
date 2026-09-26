package xie.fa.gram.helpers.update

import android.os.Build
import xie.fa.gram.InuConfig
import xie.fa.gram.helpers.InuUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BetaUpdate
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import kotlin.math.max
import kotlin.math.min
import xie.fa.gram.helpers.security.ParanoiaHelper

object UpdateHelper {
    const val USERNAME = "fagramci"
    private const val CHANNEL_ID = 3924906222L
    private const val CHECK_INTERVAL_MS = 4L * 60 * 60 * 1000
    private const val INFLIGHT_TIMEOUT_MS = 60L * 1000

    private val pInfo by lazy {
        ApplicationLoader.applicationContext.packageManager.getPackageInfo(
            ApplicationLoader.applicationContext.packageName,
            0
        )
    }
    @JvmStatic
    val stockVersionName by lazy {
        pInfo.versionName?.replace(Regex("-[0-9a-f]{7}$"), "") ?: ""
    }

    fun getVersionInfoString(): String {
        return LocaleController.formatString(
            R.string.InuVersion,
            pInfo.versionCode,
            stockVersionName,
            BuildConfig.STOCK_VERSION_CODE
        )
    }

    fun getAboutVersionString(): String {
        return LocaleController.formatString(
            R.string.InuAboutVersionFormat,
            pInfo.versionCode,
            stockVersionName,
            BuildConfig.STOCK_VERSION_CODE
        )
    }

    @JvmStatic
    fun getFullVersionInfo(): String {
        if (ParanoiaHelper.isDisguised()) {
            return "Telegram for Android v${stockVersionName} (${BuildConfig.STOCK_VERSION_CODE})\ndirect ${Build.CPU_ABI} ${Build.CPU_ABI2}"
        }
        return "${getVersionInfoString()}\nBuilt on: ${BuildVars.BUILD_DATE}"
    }

    private val VERSION_RE = Regex("""FAgram Android v(?<version>[\d.\-]+)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_VERSION_RE = Regex("""v(?<version>[\d.\-]+)""", RegexOption.IGNORE_CASE)
    private val FILENAME_BUILD_RE = Regex("""fagram-.*-(?<build>\d+)\.apk""", RegexOption.IGNORE_CASE)
    private val BASE_RE = Regex("""Base:\s*(?<base>[^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val BUILD_TYPE_RE = Regex("""Build Type:\s*(?<buildType>\w+)""", RegexOption.IGNORE_CASE)
    private val SHA256_RE = Regex("""SHA256:\s*(?<hash>[a-fA-F0-9]{64})""", RegexOption.IGNORE_CASE)

    @Volatile
    private var inflight = false

    @Volatile
    private var inflightSince = 0L

    @Volatile
    var pendingBetaUpdate: BetaUpdate? = null
        private set

    @Volatile
    var pendingSha256: String? = null
        get() = field ?: InuConfig.UPDATE_PENDING_SHA256.value.takeIf { it.isNotBlank() }
        private set

    // cached source message of the current pending update, set by applyUpdate. lets
    // startDownload skip the resolver+RPC dance when the update was detected this session.
    @Volatile
    private var pendingSourceMessage: TLRPC.Message? = null

    // true between the click on Update and FileLoader.loadFile actually firing. lets the row
    // show the Downloading state immediately even while the async file-ref refresh dance is
    // still running.
    @Volatile
    var isPendingStart: Boolean = false
        private set

    // applyUpdate doesn't post appUpdateAvailable itself — the caller does it, via
    // revealPendingUpdate, once the changelog dialog is on screen (so the bar slides in behind
    // the dialog instead of visibly popping into the page underneath).
    @JvmStatic
    fun revealPendingUpdate() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable, true)
    }

    fun checkForCustomUpdate(force: Boolean, whenDone: Runnable?) {
        if (!InuConfig.UPDATES_ENABLED.value) {
            whenDone?.run()
            return
        }
        if (!force && System.currentTimeMillis() - InuConfig.UPDATE_LAST_CHECK_MS.value < CHECK_INTERVAL_MS) {
            whenDone?.run()
            return
        }
        check { whenDone?.run() }
    }

    fun clearPending() {
        pendingBetaUpdate = null
        pendingSourceMessage = null
        pendingSha256 = null
        InuConfig.UPDATE_PENDING_SHA256.value = ""
        isPendingStart = false
        SharedConfig.pendingAppUpdate = null
        SharedConfig.saveConfig()
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable, false)
    }

    @JvmStatic
    fun clearPendingIfInstalled() {
        val pending = SharedConfig.pendingAppUpdate ?: return
        val current = currentBuild()
        val pendingBuild = pending.version?.substringAfterLast('-')?.toIntOrNull()
            ?: pending.version?.toIntOrNull()
            ?: return
        if (current.versionCode >= pendingBuild) {
            clearPending()
        }
    }

    fun startDownload(account: Int) {
        val update = SharedConfig.pendingAppUpdate ?: return
        val doc = update.document ?: return

        isPendingStart = true
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading)

        val cached = pendingSourceMessage
        if (cached != null) {
            beginLoad(account, doc, MessageObject(account, cached, false, false))
            return
        }

        val messageId = update.id
        if (messageId <= 0) {
            refreshPendingAndStart(account)
            return
        }
        val mc = MessagesController.getInstance(account)
        // resolve first so the channel (with access_hash) is cached for getInputChannel
        mc.userNameResolver.resolve(USERNAME) { peerId ->
            AndroidUtilities.runOnUIThread {
                if (!isPendingStart) return@runOnUIThread
                if (peerId == null || peerId == 0L || peerId == Long.MAX_VALUE) {
                    stopPendingStart()
                    return@runOnUIThread
                }
                val req = TLRPC.TL_channels_getMessages().apply {
                    channel = mc.getInputChannel(CHANNEL_ID)
                    id.add(messageId)
                }
                ConnectionsManager.getInstance(account).sendRequest(req) { resp, _ ->
                    AndroidUtilities.runOnUIThread {
                        if (!isPendingStart) return@runOnUIThread
                        val msg = (resp as? TLRPC.messages_Messages)?.messages
                            ?.firstOrNull { it.id == messageId }
                        val freshInfo = msg?.let { extractApkInfo(it) }
                        val freshDoc = freshInfo?.document
                        if (msg == null || freshDoc == null) {
                            beginLoad(account, doc, sourceMessageParent(messageId))
                        } else {
                            pendingSourceMessage = msg
                            if (freshInfo.sha256 != null) {
                                pendingSha256 = freshInfo.sha256
                                InuConfig.UPDATE_PENDING_SHA256.value = freshInfo.sha256
                            }
                            beginLoad(account, freshDoc, MessageObject(account, msg, false, false))
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload(account: Int) {
        if (isPendingStart) {
            isPendingStart = false
        } else {
            SharedConfig.pendingAppUpdate?.document?.let {
                FileLoader.getInstance(account).cancelLoadFile(it)
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading)
    }

    private fun refreshPendingAndStart(account: Int) {
        check {
            AndroidUtilities.runOnUIThread {
                if (!isPendingStart) return@runOnUIThread
                if ((SharedConfig.pendingAppUpdate?.id ?: 0) > 0) {
                    startDownload(account)
                } else {
                    stopPendingStart()
                }
            }
        }
    }

    private fun sourceMessageParent(messageId: Int) = "sent_${CHANNEL_ID}_${messageId}"

    private fun stopPendingStart() {
        isPendingStart = false
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading)
    }

    private fun beginLoad(account: Int, document: TLRPC.Document, parent: Any) {
        isPendingStart = false
        FileLoader.getInstance(account).loadFile(document, parent, FileLoader.PRIORITY_NORMAL, 1)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading)
    }

    fun isCompatibleBuildType(remoteBuildType: String?): Boolean {
        if (remoteBuildType.isNullOrBlank()) return true
        val local = BuildConfig.INU_BUILD_TYPE.lowercase().trim()
        val remote = remoteBuildType.lowercase().trim()
        val localNorm = when (local) {
            "rel", "release" -> "release"
            "debug", "dev" -> "debug"
            else -> local
        }
        val remoteNorm = when (remote) {
            "rel", "release" -> "release"
            "debug", "dev" -> "debug"
            else -> remote
        }
        return localNorm == remoteNorm
    }

    fun check(callback: ((CheckResult) -> Unit)?) {
        val account = UserConfig.selectedAccount
        if (!UserConfig.getInstance(account).isClientActivated) {
            callback?.invoke(CheckResult.Error("Not logged in"))
            return
        }
        val now = System.currentTimeMillis()
        if (inflight && now - inflightSince < INFLIGHT_TIMEOUT_MS) {
            callback?.invoke(CheckResult.InFlight)
            return
        }
        inflight = true
        inflightSince = now
        MessagesController.getInstance(account).userNameResolver.resolve(USERNAME) { id ->
            if (id == null || id == 0L || id == Long.MAX_VALUE) {
                finish(callback, CheckResult.Error("resolve failed"))
                return@resolve
            }
            performSearch(account, id, callback)
        }
    }

    private fun performSearch(account: Int, peerId: Long, callback: ((CheckResult) -> Unit)?) {
        val mc = MessagesController.getInstance(account)
        val req = TLRPC.TL_messages_search().apply {
            peer = mc.getInputPeer(peerId)
            q = ""
            filter = TLRPC.TL_inputMessagesFilterDocument()
            limit = 10
        }
        ConnectionsManager.getInstance(account).sendRequest(req) { resp, err ->
            AndroidUtilities.runOnUIThread {
                if (err != null || resp !is TLRPC.messages_Messages) {
                    finish(callback, CheckResult.Error(err?.text ?: "no response"))
                    return@runOnUIThread
                }
                val match = resp.messages.firstNotNullOfOrNull { msg ->
                    extractApkInfo(msg)?.takeIf { isCompatibleBuildType(it.buildType) }?.let { msg to it }
                }
                val current = currentBuild()
                if (match == null || !isNewer(match.second, current)) {
                    clearPending()
                    finish(callback, CheckResult.UpToDate)
                    return@runOnUIThread
                }
                val (msg, info) = match
                val updateObj = applyUpdate(msg, info, current)
                finish(callback, CheckResult.Updated(updateObj))
            }
        }
    }

    fun onNewMessage(msg: TLRPC.Message) {
        if (!InuConfig.UPDATES_ENABLED.value) return
        val channelId = msg.peer_id?.channel_id ?: 0L
        if (channelId != CHANNEL_ID && channelId != (CHANNEL_ID % 1000000000000L)) return
        val info = extractApkInfo(msg) ?: return
        if (!isCompatibleBuildType(info.buildType)) return
        val current = currentBuild()
        if (!isNewer(info, current)) return
        AndroidUtilities.runOnUIThread {
            applyUpdate(msg, info, current)
            revealPendingUpdate()
            InuConfig.UPDATE_LAST_CHECK_MS.value = System.currentTimeMillis()
        }
    }

    private fun extractChangelog(messageText: String, rawEntities: ArrayList<TLRPC.MessageEntity>?): Pair<String, ArrayList<TLRPC.MessageEntity>> {
        val entities = cloneEntities(rawEntities)
        val blockquote = entities.firstOrNull { it is TLRPC.TL_messageEntityBlockquote }

        if (blockquote != null) {
            val start = blockquote.offset
            val end = min(messageText.length, blockquote.offset + blockquote.length)
            if (start < end) {
                val newEntities = arrayListOf<TLRPC.MessageEntity>()
                for (entity in entities) {
                    if (entity === blockquote) continue
                    if (entity.offset + entity.length <= start) continue
                    if (entity.offset >= end) continue
                    val clippedStart = max(entity.offset, start)
                    val clippedEnd = min(entity.offset + entity.length, end)
                    entity.offset = clippedStart - start
                    entity.length = clippedEnd - clippedStart
                    newEntities.add(entity)
                }
                val text = messageText.substring(start, end)
                return text to newEntities
            }
        }

        // Fallback: extract body text below metadata lines
        val metadataEndIndex = listOfNotNull(
            SHA256_RE.find(messageText)?.range?.last,
            BUILD_TYPE_RE.find(messageText)?.range?.last,
            BASE_RE.find(messageText)?.range?.last,
            VERSION_RE.find(messageText)?.range?.last,
        ).maxOrNull()

        if (metadataEndIndex != null && metadataEndIndex + 1 < messageText.length) {
            val rawBody = messageText.substring(metadataEndIndex + 1)
            val firstNonWs = rawBody.indexOfFirst { !it.isWhitespace() }
            val start = if (firstNonWs >= 0) metadataEndIndex + 1 + firstNonWs else metadataEndIndex + 1
            val end = messageText.length

            val newEntities = arrayListOf<TLRPC.MessageEntity>()
            for (entity in entities) {
                if (entity.offset + entity.length <= start) continue
                if (entity.offset >= end) continue
                val clippedStart = max(entity.offset, start)
                val clippedEnd = min(entity.offset + entity.length, end)
                entity.offset = clippedStart - start
                entity.length = clippedEnd - clippedStart
                newEntities.add(entity)
            }
            var text = messageText.substring(start, end).trim()
            if (newEntities.isEmpty()) {
                text = text.lines().joinToString("\n") { it.removePrefix(">").trimStart() }
            }
            return text to newEntities
        }

        return messageText to entities
    }

    private fun applyUpdate(msg: TLRPC.Message, info: ApkInfo, current: CurrentBuild): TLRPC.TL_help_appUpdate {
        val updateObj = TLRPC.TL_help_appUpdate().apply {
            flags = flags or 2
            // stash the source channel message id in the otherwise-unused `id` field
            id = msg.id
            version = info.version
            text = info.changelog
            entities = info.changelogEntities
            document = info.document
        }

        SharedConfig.pendingAppUpdate = updateObj
        SharedConfig.pendingAppUpdateBuildVersion = current.versionCode
        SharedConfig.saveConfig()
        val safeBaseVersion = info.base.substringBefore('-').ifEmpty { info.version.substringBefore('-') }
        pendingBetaUpdate = InuBetaUpdate(safeBaseVersion, info.buildNum, updateObj.text)
        pendingSourceMessage = msg
        pendingSha256 = info.sha256
        InuConfig.UPDATE_PENDING_SHA256.value = info.sha256 ?: ""
        return updateObj
    }

    private fun cloneEntities(entities: ArrayList<TLRPC.MessageEntity>?): ArrayList<TLRPC.MessageEntity> {
        val out = ArrayList<TLRPC.MessageEntity>(entities?.size ?: 0)
        entities?.forEach { entity ->
            InuUtils.cloneTLObject(entity, TLRPC.MessageEntity::TLdeserialize)?.let(out::add)
        }
        return out
    }

    private fun finish(callback: ((CheckResult) -> Unit)?, result: CheckResult) {
        inflight = false
        InuConfig.UPDATE_LAST_CHECK_MS.value = System.currentTimeMillis()
        callback?.invoke(result)
    }

    @Suppress("DEPRECATION")
    private fun currentBuild(): CurrentBuild = CurrentBuild(
        versionCode = pInfo.versionCode,
    )

    private fun extractApkInfo(msg: TLRPC.Message): ApkInfo? {
        val media = msg.media as? TLRPC.TL_messageMediaDocument ?: return null
        val doc = media.document ?: return null
        val nameAttr = doc.attributes.filterIsInstance<TLRPC.TL_documentAttributeFilename>().firstOrNull()
        val fileName = FileLoader.getDocumentFileName(doc) ?: nameAttr?.file_name ?: ""
        val isApk = doc.mime_type == "application/vnd.android.package-archive" || fileName.endsWith(".apk", ignoreCase = true)
        if (!isApk) return null

        val caption = msg.message ?: ""
        val version = VERSION_RE.find(caption)?.groups?.get("version")?.value
            ?: FALLBACK_VERSION_RE.find(caption)?.groups?.get("version")?.value
            ?: return null

        val buildFromVer = version.substringAfterLast('-').toIntOrNull()
        val buildFromFilename = FILENAME_BUILD_RE.find(fileName)?.groups?.get("build")?.value?.toIntOrNull()
        val buildNum = buildFromVer ?: buildFromFilename
        if (buildNum == null) {
            android.util.Log.d("UpdateHelper", "Cannot parse build number from version '$version' or filename '$fileName', skipping")
            return null
        }

        val base = BASE_RE.find(caption)?.groups?.get("base")?.value?.trim() ?: ""
        val buildType = BUILD_TYPE_RE.find(caption)?.groups?.get("buildType")?.value?.trim() ?: ""
        val sha256 = SHA256_RE.find(caption)?.groups?.get("hash")?.value?.trim()

        val (changelog, changelogEntities) = extractChangelog(caption, msg.entities)

        return ApkInfo(
            version = version,
            buildNum = buildNum,
            base = base,
            buildType = buildType,
            sha256 = sha256,
            changelog = changelog,
            changelogEntities = changelogEntities,
            document = doc,
        )
    }

    private fun isNewer(remote: ApkInfo, current: CurrentBuild): Boolean {
        return remote.buildNum > current.versionCode
    }

    class InuBetaUpdate(
        version: String,
        versionCode: Int,
        changelog: String?,
    ) : BetaUpdate(version, versionCode, changelog) {
        override fun higherThan(update: BetaUpdate?): Boolean {
            return update == null || versionCode > update.versionCode
        }
    }

    sealed class CheckResult {
        object InFlight : CheckResult()
        object UpToDate : CheckResult()
        data class Updated(val update: TLRPC.TL_help_appUpdate) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    private data class ApkInfo(
        val version: String,
        val buildNum: Int,
        val base: String,
        val buildType: String,
        val sha256: String?,
        val changelog: String,
        val changelogEntities: ArrayList<TLRPC.MessageEntity>,
        val document: TLRPC.Document,
    )

    private data class CurrentBuild(
        val versionCode: Int,
    )
}
