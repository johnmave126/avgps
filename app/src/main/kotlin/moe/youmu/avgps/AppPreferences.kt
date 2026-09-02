package moe.youmu.avgps

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "avgps_preferences")

class AppPreferences(private val context: Context) {
    val unitSettings: Flow<UnitSettings> = context.dataStore.data.map { preferences ->
        UnitSettings(
            altitude = preferences.enumValue(ALTITUDE_UNIT, AltitudeUnit.METERS),
            speed = preferences.enumValue(SPEED_UNIT, SpeedUnit.METERS_PER_SECOND),
            distance = preferences.enumValue(DISTANCE_UNIT, DistanceUnit.METERS),
        )
    }

    suspend fun setAltitudeUnit(unit: AltitudeUnit) = setEnum(ALTITUDE_UNIT, unit)

    suspend fun setSpeedUnit(unit: SpeedUnit) = setEnum(SPEED_UNIT, unit)

    suspend fun setDistanceUnit(unit: DistanceUnit) = setEnum(DISTANCE_UNIT, unit)

    private suspend fun <T : Enum<T>> setEnum(key: Preferences.Key<String>, value: T) {
        context.dataStore.edit { preferences -> preferences[key] = value.name }
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(
        key: Preferences.Key<String>,
        default: T,
    ): T = this[key]?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    companion object {
        private val ALTITUDE_UNIT = stringPreferencesKey("altitude_unit")
        private val SPEED_UNIT = stringPreferencesKey("speed_unit")
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    }
}
