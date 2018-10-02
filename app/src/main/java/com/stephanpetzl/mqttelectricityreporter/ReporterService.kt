package com.stephanpetzl.mqttelectricityreporter

import android.arch.lifecycle.LifecycleService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import timber.log.Timber
import java.nio.charset.Charset


class ReporterService : LifecycleService() {

    private var mqttAndroidClient: MqttAndroidClient? = null
    private val clientId = "ElectricityReporter"
    private var mqttTopic: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            MainBus.register(this)
        } catch (e: IllegalArgumentException){
            // ignore error about double registering
        }
        init()
        return super.onStartCommand(intent, flags, startId)
    }

    private var loopThread: Thread? = null
    private var wasCharging = false

    private fun init() {
        val checkRunnable = Runnable {
            while (!Thread.interrupted())
                try {
                    Thread.sleep(1000)


                    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                        applicationContext.registerReceiver(null, ifilter)
                    }

                    val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                    val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                    val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
                    Timber.d("isCharging: $isCharging, status: $status, chargePlug: $chargePlug, usbCharge: $usbCharge, acCharge: $acCharge")
                    if(isCharging != wasCharging){
                        if(isCharging) {
                            MainBus.post(OnPowerOnEvent())
                        } else {
                            MainBus.post(OnPowerOffEvent())
                        }
                    }
                    wasCharging = isCharging
                } catch (e: InterruptedException) {

                }
        }
        if (loopThread != null) {
            loopThread!!.interrupt()
        }
        loopThread = Thread(checkRunnable)
        loopThread!!.start()
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
                MainBus.post(OnConnectedEvent())
            }

            override fun connectionLost(cause: Throwable) {
                sendStatus("The Connection was lost.")
                MainBus.post(OnDisconnectedEvent())
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
                    MainBus.post(OnDisconnectedEvent())
                }
            })


        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }

    private fun sendStatus(message: String) {
        Timber.d("Status: $message")
        MainBus.post(OnStatusEvent(message))
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