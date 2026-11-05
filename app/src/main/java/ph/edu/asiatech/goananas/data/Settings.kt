package ph.edu.asiatech.goananas.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "go_ananas_settings")

/** user roles described in the use case of the study */
val ROLES = listOf("Farmer / Farm worker", "Vendor", "Agricultural technician", "Farm owner")

data class UserSettings(
    val name: String = "",
    val role: String = ROLES.first(),
    val farmCode: String = "",
    val onboarded: Boolean = false,
    val recheckReminders: Boolean = true,
    val monthlyReminder: Boolean = true,
    val outbreakAlerts: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("name")
        val role = stringPreferencesKey("role")
        val farm = stringPreferencesKey("farm_code")
        val onboarded = booleanPreferencesKey("onboarded")
        val recheck = booleanPreferencesKey("recheck_reminders")
        val monthly = booleanPreferencesKey("monthly_reminder")
        val outbreak = booleanPreferencesKey("outbreak_alerts")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p: Preferences ->
        UserSettings(
            name = p[K.name] ?: "",
            role = p[K.role] ?: ROLES.first(),
            farmCode = p[K.farm] ?: "",
            onboarded = p[K.onboarded] ?: false,
            recheckReminders = p[K.recheck] ?: true,
            monthlyReminder = p[K.monthly] ?: true,
            outbreakAlerts = p[K.outbreak] ?: true,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun saveProfile(name: String, role: String, farmCode: String) = context.dataStore.edit {
        it[K.name] = name.trim(); it[K.role] = role; it[K.farm] = farmCode.trim().uppercase(); it[K.onboarded] = true
    }

    suspend fun setRecheckReminders(on: Boolean) = context.dataStore.edit { it[K.recheck] = on }
    suspend fun setMonthlyReminder(on: Boolean) = context.dataStore.edit { it[K.monthly] = on }
    suspend fun setOutbreakAlerts(on: Boolean) = context.dataStore.edit { it[K.outbreak] = on }
    suspend fun signOutLocal() = context.dataStore.edit { it[K.onboarded] = false }
}
