package moe.youmu.avgps

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AvGpsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val service = MutableStateFlow<LocationService?>(null)
    private var bound = false
    private var monitoringRequested = false

    val appState = service
        .flatMapLatest { current -> current?.state ?: flowOf(AvGpsState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvGpsState())

    val unitSettings = preferences.unitSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitSettings())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service.value = (binder as LocationService.LocalBinder).getService()
            service.value?.setUiMonitoring(monitoringRequested)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.value = null
            bound = false
        }
    }

    init {
        val context = getApplication<Application>()
        val intent = Intent(context, LocationService::class.java)
        context.startService(intent)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun setMonitoring(enabled: Boolean) {
        monitoringRequested = enabled
        service.value?.setUiMonitoring(enabled)
    }

    fun refreshMonitoring() = service.value?.refreshMonitoring()

    fun clearClients() = service.value?.clearEFB()

    fun setClientEnabled(client: Client, enabled: Boolean) {
        service.value?.setEfbEnabled(client.source, enabled)
    }

    fun setAltitudeUnit(unit: AltitudeUnit) {
        viewModelScope.launch { preferences.setAltitudeUnit(unit) }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch { preferences.setSpeedUnit(unit) }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { preferences.setDistanceUnit(unit) }
    }

    override fun onCleared() {
        service.value?.setUiMonitoring(false)
        if (bound) getApplication<Application>().unbindService(connection)
    }
}
