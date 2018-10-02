package com.stephanpetzl.mqttelectricityreporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stephanpetzl.mqttaudioplayer.event.OnPowerOffEvent
import com.stephanpetzl.mqttaudioplayer.event.OnPowerOnEvent
import timber.log.Timber

class PlugInControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("onReceive ${intent.action}")
        val action = intent.action

        if (action == Intent.ACTION_POWER_CONNECTED) {
            MainBus.instance.post(OnPowerOnEvent())
        } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
            MainBus.instance.post(OnPowerOffEvent())
        }
    }

}
