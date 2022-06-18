package moe.youmu.avgps

import android.util.Log
import io.flutter.plugin.common.EventChannel

class LocationStreamHandler(private var activity: MainActivity) : EventChannel.StreamHandler {
    private var eventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        if (eventSink != null) {
            activity.locationService!!.addLocationSink(eventSink!!)
        }
    }

    override fun onCancel(arguments: Any?) {
        if (eventSink != null) {
            activity.locationService?.removeLocationSink(eventSink!!)
        }
        eventSink = null
    }

}