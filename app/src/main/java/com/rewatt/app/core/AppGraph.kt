package com.rewatt.app.core

import android.content.Context
import com.rewatt.app.data.AuthRepository
import com.rewatt.app.data.MqttSyncRepository
import com.rewatt.app.data.ReWattDatabase
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.perception.PerceptionStateHolder

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val authRepository: AuthRepository by lazy {
        AuthRepository(appContext)
    }

    val database: ReWattDatabase by lazy {
        ReWattDatabase.create(appContext)
    }

    val mqttSyncRepository: MqttSyncRepository by lazy {
        MqttSyncRepository(appContext)
    }

    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository(
            telemetryDao = database.telemetryDao(),
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(appContext)
    }

    val perceptionStateHolder: PerceptionStateHolder by lazy {
        PerceptionStateHolder()
    }
}
