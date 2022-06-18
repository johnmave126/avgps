package moe.youmu.avgps

interface StreamHandleWithLocation {
    fun onLocationAvailable(locationService: LocationService)
}