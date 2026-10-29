package dev.androidauto.ytcar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/** Uma Session por conexão com o carro. A primeira tela é o player. */
class YtSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = PlayerScreen(carContext)
}
