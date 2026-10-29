package dev.androidauto.ytcar.car

import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.OnDoneCallback
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/** Tela-base qualquer, para as telas testadas terem para onde voltar com pop(). */
class RootScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        ListTemplate.Builder().setTitle("raiz").setLoading(true).build()
}

fun newCarContext(): TestCarContext =
    TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

/** Coloca uma RootScreen na pilha e depois [screen] por cima, ambas em RESUMED. */
fun <S : Screen> startOnTopOfRoot(carContext: TestCarContext, screen: S): ScreenController {
    ScreenController(RootScreen(carContext)).moveToState(Lifecycle.State.RESUMED)
    return ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
}

fun TestCarContext.screenManager(): TestScreenManager = getCarService(TestScreenManager::class.java)

/** Simula o host tocando num botão/linha (passa pelo mesmo caminho do binder). */
fun Action.click() = requireNotNull(onClickDelegate).sendClick(object : OnDoneCallback {})

fun Row.click() = requireNotNull(onClickDelegate).sendClick(object : OnDoneCallback {})

fun ItemList.rows(): List<Row> = items.map { it as Row }

fun Row.titleText(): String = title.toString()

fun idleMain() = shadowOf(Looper.getMainLooper()).idle()
