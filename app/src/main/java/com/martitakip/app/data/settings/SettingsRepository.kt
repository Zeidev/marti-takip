package com.martitakip.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * Operator preferences: the two unit prices and the daily battery goal.
 *
 * DataStore (instead of SharedPreferences) gives a cold [Flow] with no
 * listener plumbing, which fits the unidirectional MVVM data flow used by the
 * ViewModel.
 */
data class UserSettings(
    val scooterPrice: Double = DEFAULT_SCOOTER_PRICE,
    val mopedPrice: Double = DEFAULT_MOPED_PRICE,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val hapticsEnabled: Boolean = true,
    val autoGps: Boolean = true,
    /** Short confirmation beep on every recorded swap. */
    val soundEnabled: Boolean = true,
) {
    fun priceFor(type: com.martitakip.app.data.local.VehicleType): Double = when (type) {
        com.martitakip.app.data.local.VehicleType.SCOOTER -> scooterPrice
        com.martitakip.app.data.local.VehicleType.MOPED -> mopedPrice
    }

    /** Progress towards the daily goal, clamped to 0f..1f. */
    fun goalProgress(current: Int): Float =
        if (dailyGoal <= 0) 0f else (current.toFloat() / dailyGoal).coerceIn(0f, 1f)

    fun isGoalReached(current: Int): Boolean = dailyGoal > 0 && current >= dailyGoal

    fun remainingToGoal(current: Int): Int = (dailyGoal - current).coerceAtLeast(0)

    /** Estimated earnings if the goal is completed with the current mix. */
    fun projectedGoalEarnings(): Double {
        val averagePrice = (scooterPrice + mopedPrice) / 2.0
        return averagePrice * dailyGoal
    }

    companion object {
        const val DEFAULT_SCOOTER_PRICE = 15.0
        const val DEFAULT_MOPED_PRICE = 25.0
        const val DEFAULT_DAILY_GOAL = 40

        const val MIN_PRICE = 0.0
        const val MAX_PRICE = 1_000.0
        const val MIN_GOAL = 1
        const val MAX_GOAL = 500

        /** Rounds a price to the nearest kuruş so the DB never stores 14.999999. */
        fun normalizePrice(value: Double): Double =
            (value.coerceIn(MIN_PRICE, MAX_PRICE) * 100).roundToInt() / 100.0
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "marti_takip_settings",
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SCOOTER_PRICE = doublePreferencesKey("scooter_price")
        val MOPED_PRICE = doublePreferencesKey("moped_price")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val HAPTICS_ENABLED = intPreferencesKey("haptics_enabled")
        val AUTO_GPS = intPreferencesKey("auto_gps")
        val SOUND_ENABLED = intPreferencesKey("sound_enabled")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            scooterPrice = prefs[Keys.SCOOTER_PRICE] ?: UserSettings.DEFAULT_SCOOTER_PRICE,
            mopedPrice = prefs[Keys.MOPED_PRICE] ?: UserSettings.DEFAULT_MOPED_PRICE,
            dailyGoal = prefs[Keys.DAILY_GOAL] ?: UserSettings.DEFAULT_DAILY_GOAL,
            hapticsEnabled = (prefs[Keys.HAPTICS_ENABLED] ?: 1) == 1,
            autoGps = (prefs[Keys.AUTO_GPS] ?: 1) == 1,
            soundEnabled = (prefs[Keys.SOUND_ENABLED] ?: 1) == 1,
        )
    }

    suspend fun updatePrices(scooterPrice: Double, mopedPrice: Double) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SCOOTER_PRICE] = UserSettings.normalizePrice(scooterPrice)
            prefs[Keys.MOPED_PRICE] = UserSettings.normalizePrice(mopedPrice)
        }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL] = goal.coerceIn(UserSettings.MIN_GOAL, UserSettings.MAX_GOAL)
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.HAPTICS_ENABLED] = if (enabled) 1 else 0 }
    }

    suspend fun setAutoGpsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.AUTO_GPS] = if (enabled) 1 else 0 }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.SOUND_ENABLED] = if (enabled) 1 else 0 }
    }

    suspend fun save(settings: UserSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SCOOTER_PRICE] = UserSettings.normalizePrice(settings.scooterPrice)
            prefs[Keys.MOPED_PRICE] = UserSettings.normalizePrice(settings.mopedPrice)
            prefs[Keys.DAILY_GOAL] = settings.dailyGoal.coerceIn(UserSettings.MIN_GOAL, UserSettings.MAX_GOAL)
            prefs[Keys.HAPTICS_ENABLED] = if (settings.hapticsEnabled) 1 else 0
            prefs[Keys.AUTO_GPS] = if (settings.autoGps) 1 else 0
            prefs[Keys.SOUND_ENABLED] = if (settings.soundEnabled) 1 else 0
        }
    }

    suspend fun resetToDefaults() {
        save(UserSettings())
    }
}
