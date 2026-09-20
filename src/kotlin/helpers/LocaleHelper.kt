package xie.fa.gram.helpers

import android.content.res.Configuration
import android.content.res.Resources
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import java.util.Locale
import xie.fa.gram.helpers.security.ParanoiaHelper

// resolves fork ("local-only") strings against the Telegram-selected locale instead of the
// system locale. builds candidate Locales from LocaleInfo (pluralLangCode/baseLangCode/shortName,
// to cover official + third-party packs) and picks the first whose value differs from values/.
object LocaleHelper {
    @Volatile
    private var cachedKey: String? = null
    private var cachedDefault: Resources? = null
    private var cachedCandidates: List<Resources> = emptyList()

    @JvmStatic
    fun isLocalOnlyString(key: String?): Boolean {
        if (key == null) return false
        return key.startsWith("Inu") ||
            key.startsWith("fa_") ||
            key.startsWith("Fa") ||
            key == "AppName" ||
            key == "AppNameBeta" ||
            key == "AppUpdate" ||
            key == "AppUpdateBeta"
    }

    @JvmStatic
    fun getLocalString(key: String?, res: Int): String? {
        if (res == 0) return null
        val resolvedKey = key ?: try {
            ApplicationLoader.applicationContext?.resources?.getResourceEntryName(res)
        } catch (_: Exception) {
            null
        }
        if (resolvedKey != null) {
            if (!isLocalOnlyString(resolvedKey)) return null
            disguiseName(resolvedKey)?.let { return it }
        }
        return resolve(res)
    }

    @JvmStatic
    fun getLocalString(key: String?): String? {
        if (!isLocalOnlyString(key)) return null
        disguiseName(key)?.let { return it }
        val ctx = ApplicationLoader.applicationContext ?: return null
        val id = ctx.resources.getIdentifier(key, "string", ctx.packageName)
        if (id == 0) return null
        return resolve(id)
    }

    // when disguised, the app name must read as stock Telegram regardless of locale.
    private fun disguiseName(key: String?): String? {
        if (!ParanoiaHelper.isDisguised()) return null
        return when (key) {
            "AppName" -> "Telegram"
            "AppNameBeta" -> "Telegram Beta"
            "AppUpdate" -> "Update Telegram"
            "AppUpdateBeta" -> "Update Telegram Beta"
            else -> null
        }
    }

    private fun resolve(res: Int): String? {
        val ctx = ApplicationLoader.applicationContext
        if (!ensureCache()) {
            return try {
                ctx?.getString(res)
            } catch (_: Exception) {
                null
            }
        }
        val def = try {
            cachedDefault?.getString(res) ?: ctx?.getString(res)
        } catch (_: Exception) {
            try {
                ctx?.getString(res)
            } catch (_: Exception) {
                null
            }
        } ?: return null

        for (r in cachedCandidates) {
            val v = try {
                r.getString(res)
            } catch (_: Exception) {
                continue
            }
            if (v != def) return v
        }
        return def
    }

    @Synchronized
    private fun ensureCache(): Boolean {
        val info = try {
            LocaleController.getInstance().currentLocaleInfo
        } catch (_: Throwable) {
            null
        } ?: return false
        val key = "${info.shortName}|${info.baseLangCode}|${info.pluralLangCode}|${info.isLocal}"
        if (key == cachedKey) return cachedCandidates.isNotEmpty()
        cachedKey = key
        cachedDefault = null
        cachedCandidates = emptyList()
        val locales = candidatesFor(info)
        if (locales.isEmpty()) return false
        cachedDefault = buildResources(Locale.ROOT) ?: return false
        cachedCandidates = locales.mapNotNull { buildResources(it) }
        return cachedCandidates.isNotEmpty()
    }

    private fun buildResources(locale: Locale): Resources? {
        val ctx = ApplicationLoader.applicationContext ?: return null
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(locale)
        return try {
            ctx.createConfigurationContext(cfg).resources
        } catch (_: Throwable) {
            null
        }
    }

    private fun candidatesFor(info: LocaleController.LocaleInfo): List<Locale> {
        val out = LinkedHashSet<Locale>()
        addFromRaw(out, info.pluralLangCode)
        addFromRaw(out, info.baseLangCode)
        addFromRaw(out, info.shortName)
        return out.toList()
    }

    private fun addFromRaw(out: MutableSet<Locale>, raw: String?) {
        if (raw.isNullOrEmpty()) return
        val norm = raw.lowercase().replace('_', '-')
        when {
            norm.startsWith("zh-hans") || norm.startsWith("zh-cn") -> {
                out.add(Locale.SIMPLIFIED_CHINESE); return
            }

            norm.startsWith("zh-hant") || norm.startsWith("zh-tw") -> {
                out.add(Locale.TRADITIONAL_CHINESE); return
            }
        }
        if (norm.startsWith("classic-")) {
            addFromRaw(out, norm.substring("classic-".length))
            return
        }
        val parts = norm.split("-").filter { p -> p.isNotEmpty() && p != "raw" && p != "beta" && p.all { it.isLetterOrDigit() } }
        if (parts.isEmpty()) return
        if (parts.size >= 2 && parts[1].length in 2..3) {
            out.add(Locale(parts[0], parts[1].uppercase()))
        }
        if (parts[0].length in 2..3) {
            out.add(Locale(parts[0]))
        }
    }
}
