package com.stephanpetzl.mqttelectricityreporter

import com.squareup.otto.Bus

class MainBus {
    companion object {
        val instance: Bus by lazy {
            Bus()
        }
    }
}
