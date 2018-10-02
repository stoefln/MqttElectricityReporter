package com.stephanpetzl.mqttelectricityreporter

import android.arch.lifecycle.LifecycleService
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.squareup.otto.Subscribe
import com.stephanpetzl.mqttaudioplayer.event.OnConnectedEvent
import com.stephanpetzl.mqttaudioplayer.event.OnDisconnectedEvent
import com.stephanpetzl.mqttaudioplayer.event.OnPowerOffEvent
import com.stephanpetzl.mqttaudioplayer.event.OnPowerOnEvent
import com.stephanpetzl.mqttelectricityreporter.event.OnStartReporterServiceEvent
import com.stephanpetzl.mqttelectricityreporter.event.OnStatusEvent
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.nio.charset.Charset

class ReporterService : LifecycleService() {

    private var mqttAndroidClient: MqttAndroidClient? = null
    private val clientId = "ElectricityReporter"
    private var mqttTopic: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            MainBus.instance.register(this)
        } catch (e: IllegalArgumentException){
            // ignore error about double registering
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @Subscribe
    fun onStartReporterServiceEvent(event: OnStartReporterServiceEvent) {
        connectToMqttBroker(event.brokerAddressUri)
        mqttTopic = event.mqttTopic
    }

    private fun connectToMqttBroker(serverUri: String) {
        if (mqttAndroidClient != null && mqttAndroidClient!!.isConnected) {
            mqttAndroidClient!!.disconnect()

        }
        mqttAndroidClient = MqttAndroidClient(applicationContext, serverUri, clientId)
        mqttAndroidClient!!.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(reconnect: Boolean, serverURI: String) {

                if (reconnect) {
                    sendStatus("Reconnected to : $serverURI")
                    // Because Clean Session is true, we need to re-subscribe
                } else {
                    sendStatus("Connected to: $serverURI")
                }
                MainBus.instance.post(OnConnectedEvent())
            }

            override fun connectionLost(cause: Throwable) {
                sendStatus("The Connection was lost.")
                MainBus.instance.post(OnDisconnectedEvent())
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {

            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {

            }
        })

        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.setAutomaticReconnect(true)
        mqttConnectOptions.isCleanSession = false


        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient!!.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.setBufferEnabled(true)
                    disconnectedBufferOptions.setBufferSize(100)
                    disconnectedBufferOptions.setPersistBuffer(false)
                    disconnectedBufferOptions.setDeleteOldestMessages(false)
                    mqttAndroidClient!!.setBufferOpts(disconnectedBufferOptions)
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    sendStatus("Failed to connect to: $serverUri")
                    MainBus.instance.post(OnDisconnectedEvent())
                }
            })


        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }

    private fun sendStatus(message: String) {
        MainBus.instance.post(OnStatusEvent(message))
    }


    @Subscribe
    fun onPowerOffEvent(event: OnPowerOffEvent) {
        publishElectricityStatus(false)
    }

    @Subscribe
    fun onPowerOnEvent(event: OnPowerOnEvent) {
        publishElectricityStatus(true)
    }

    fun publishElectricityStatus(electricityOnOff: Boolean) {
        try {
            if (mqttTopic == null || mqttAndroidClient == null || !mqttAndroidClient!!.isConnected) {
                return
            }
            val message = MqttMessage()
            val messageString = if (electricityOnOff) "1" else "0"
            message.payload = messageString.toByteArray(Charset.defaultCharset())
            mqttAndroidClient!!.publish(mqttTopic, message)
            sendStatus("Message $messageString published to topic $mqttTopic")
            if (!mqttAndroidClient!!.isConnected) {
                sendStatus("" + mqttAndroidClient!!.getBufferedMessageCount() + " messages in buffer.")
            }
        } catch (e: Exception) {
            System.err.println("Error Publishing: " + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, ReporterService::class.java)
        }
    }
}