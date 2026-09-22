package xie.fa.gram.helpers.lastfm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig

object LastFmStorage {
    private const val TAG = "LastFmStorage"
    private const val PREFS_FILE = "lastfm_secure_prefs"
    private const val FALLBACK_PREFS_FILE = "lastfm_plain_prefs"

    private const val KEY_USERNAME = "lastfm_username"
    private const val KEY_SHOW_ON_PROFILE = "lastfm_show_on_profile"
    private const val KEY_USE_OWN_KEY = "lastfm_use_own_key"
    private const val KEY_PERSONAL_API_KEY = "lastfm_personal_api_key"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val p = try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to plain", e)
                context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
            }
            prefs = p
            return p
        }
    }

    private fun safePrefs(): SharedPreferences =
        getPrefs(ApplicationLoader.applicationContext)

    var username: String
        get() = safePrefs().getString(KEY_USERNAME, "") ?: ""
        set(value) {
            safePrefs().edit().putString(KEY_USERNAME, value.trim()).apply()
        }

    var showOnProfile: Boolean
        get() = safePrefs().getBoolean(KEY_SHOW_ON_PROFILE, true)
        set(value) {
            safePrefs().edit().putBoolean(KEY_SHOW_ON_PROFILE, value).apply()
        }

    var useOwnApiKey: Boolean
        get() = safePrefs().getBoolean(KEY_USE_OWN_KEY, false)
        set(value) {
            safePrefs().edit().putBoolean(KEY_USE_OWN_KEY, value).apply()
        }

    var personalApiKey: String
        get() = safePrefs().getString(KEY_PERSONAL_API_KEY, "") ?: ""
        set(value) {
            safePrefs().edit().putString(KEY_PERSONAL_API_KEY, value.trim()).apply()
        }

    /**
     * Resolves the API key per desktop semantics:
     * A non-empty trimmed personal key wins; otherwise the shared hardcoded key.
     * Personal key is only ever used when the profile being viewed is the user's OWN
     * and their "use own API key" toggle is on — viewing anyone else's profile always
     * uses the shared key, even if the viewer has a personal key set.
     */
    @JvmStatic
    fun resolveApiKey(isSelf: Boolean): String {
        if (isSelf && useOwnApiKey) {
            val key = personalApiKey.trim()
            if (key.isNotEmpty()) {
                return key
            }
        }
        return BuildConfig.LASTFM_API_KEY ?: ""
    }
}
