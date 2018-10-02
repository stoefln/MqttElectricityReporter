package com.stephanpetzl.mqttelectricityreporter.event

class OnStartReporterServiceEvent(val brokerAddressUri: String, val mqttTopic: String)