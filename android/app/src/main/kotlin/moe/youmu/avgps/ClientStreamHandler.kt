package moe.youmu.avgps

import android.app.Activity
import android.util.Log
import io.flutter.plugin.common.EventChannel

class ClientStreamHandler(
    private var activity: MainActivity
) : EventChannel.StreamHandler {
    private var eventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        if (eventSink != null) {
            activity.locationService?.addClientSink(eventSink!!)
        }
    }

    override fun onCancel(arguments: Any?) {
        if (eventSink != null) {
            activity.locationService?.removeClientSink(eventSink!!)
        }
        eventSink = null
    }
}