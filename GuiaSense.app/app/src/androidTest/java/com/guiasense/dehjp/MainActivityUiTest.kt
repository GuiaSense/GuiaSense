package com.guiasense.dehjp

import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guiasense.dehjp.AlertaVibracao.Nivel
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de interface da tela principal.
 * Rodam com o Firebase DESLIGADO (AppConfig.firebaseHabilitado = false), para não
 * alterar o alcance do óculos real no banco. O estado do óculos é simulado com
 * MainActivity.aplicarEstado().
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun abrirTela() {
        AppConfig.firebaseHabilitado = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun fechar() {
        scenario.onActivity { MonitorService.parar(it) }
        scenario.close()
    }

    // ------------------------------------------------------------ Alcance

    @Test
    fun telaInicial_mostraAlcancePadraoEFaixasCorretas() {
        onView(withId(R.id.txt_alcance_atual)).check(matches(withText("1,00 m")))
        onView(withId(R.id.rb_perto)).check(matches(isChecked()))
        onView(withId(R.id.txt_info_vibracao))
            .check(matches(withText(containsString("menos de 0,40 m → Vibração Forte"))))
            .check(matches(withText(containsString("de 0,70 m a 1,00 m → Vibração Leve"))))
            .check(matches(withText(not(containsString("&lt;")))))  // falha #15
        onView(withId(R.id.txt_status_conexao)).check(matches(withText("Modo de teste (sem Firebase)")))
    }

    @Test
    fun modoMedio_atualizaAlcanceFaixasEFeedback() {
        onView(withId(R.id.rb_medio)).perform(scrollTo(), click())
        onView(withId(R.id.txt_alcance_atual)).check(matches(withText("1,50 m")))
        onView(withId(R.id.txt_info_vibracao))
            .check(matches(withText(containsString("menos de 0,60 m → Vibração Forte"))))
        onView(withId(R.id.txt_feedback))
            .check(matches(withText("Alcance de 1,50 m salvo (modo de teste)")))
    }

    @Test
    fun voltarParaPerto_mantemOModoMarcado() {
        // Falha #16: escolher um modo mexia no SeekBar, que desmarcava o modo.
        onView(withId(R.id.rb_medio)).perform(scrollTo(), click())
        onView(withId(R.id.rb_perto)).perform(scrollTo(), click())
        onView(withId(R.id.rb_perto)).check(matches(isChecked()))
        onView(withId(R.id.rb_medio)).check(matches(isNotChecked()))
        onView(withId(R.id.txt_alcance_atual)).check(matches(withText("1,00 m")))
    }

    @Test
    fun seekBarNoFim_vaiA2mEDesmarcaOsModos() {
        onView(withId(R.id.seekbar_alcance)).perform(scrollTo(), tocarEm(1.0f))
        onView(withId(R.id.txt_alcance_atual)).check(matches(withText("2,00 m")))
        onView(withId(R.id.rb_perto)).check(matches(isNotChecked()))
        onView(withId(R.id.rb_medio)).check(matches(isNotChecked()))
        onView(withId(R.id.txt_info_vibracao))
            .check(matches(withText(containsString("menos de 0,80 m → Vibração Forte"))))
    }

    @Test
    fun seekBarNoInicio_naoDesceAbaixoDe50cm() {
        // Falha #17: o SeekBar começava em 0 (alcance 0 m).
        onView(withId(R.id.seekbar_alcance)).perform(scrollTo(), tocarEm(0.0f))
        onView(withId(R.id.txt_alcance_atual)).check(matches(withText("0,50 m")))
    }

    @Test
    fun seekBar_temRotuloEValorFaladoEmMetrosParaOTalkBack() {
        onView(withId(R.id.seekbar_alcance))
            .check(matches(withContentDescription("Alcance máximo de detecção")))
        onView(withId(R.id.rb_medio)).perform(scrollTo(), click())
        scenario.onActivity {
            val sb = it.findViewById<SeekBar>(R.id.seekbar_alcance)
            assertEquals("1,50 metros", ViewCompat.getStateDescription(sb).toString())
        }
    }

    // ---------------------------------------------------- Estado do óculos

    @Test
    fun oculosOnline_mostraDistanciaENivel() {
        scenario.onActivity { it.aplicarEstado(EstadoOculos(0.35, Nivel.FORTE, System.currentTimeMillis())) }
        onView(withId(R.id.txt_status_conexao)).check(matches(withText(containsString("ao vivo"))))
        onView(withId(R.id.txt_distancia)).check(matches(withText("0,35 m")))
        onView(withId(R.id.txt_intensidade)).check(matches(withText("Forte 🔴")))
    }

    @Test
    fun semObstaculo_mostraLivre() {
        scenario.onActivity { it.aplicarEstado(EstadoOculos(-1.0, Nivel.NENHUMA, System.currentTimeMillis())) }
        onView(withId(R.id.txt_distancia)).check(matches(withText("Livre (sem obstáculo)")))
        onView(withId(R.id.txt_intensidade)).check(matches(withText("Nenhuma ⚪")))
    }

    @Test
    fun dadoAntigo_mostraOfflineENaoExibeLeituraVelha() {
        // Falha #5: "Conectado" ficava para sempre. 60 s sem sinal => offline.
        val haUmMinuto = System.currentTimeMillis() - 60_000
        scenario.onActivity { it.aplicarEstado(EstadoOculos(0.20, Nivel.FORTE, haUmMinuto)) }
        onView(withId(R.id.txt_status_conexao)).check(matches(withText(containsString("offline"))))
        onView(withId(R.id.txt_intensidade)).check(matches(withText(not("Forte 🔴"))))
    }

    // ------------------------------------------------------ Monitoramento

    @Test
    fun botaoDeAlertas_iniciaEParaOServico() {
        concederPermissaoDeNotificacao()
        onView(withId(R.id.btn_conectar)).check(matches(withText("Iniciar alertas no relógio")))

        onView(withId(R.id.btn_conectar)).perform(click())
        esperarAte { MonitorService.rodando }
        SystemClock.sleep(500)  // a tela atualiza o texto 300 ms depois
        onView(withId(R.id.btn_conectar)).check(matches(withText("Parar alertas no relógio")))
        assertTrue(MonitorService.rodando)

        onView(withId(R.id.btn_conectar)).perform(click())
        esperarAte { !MonitorService.rodando }
        SystemClock.sleep(500)
        onView(withId(R.id.btn_conectar)).check(matches(withText("Iniciar alertas no relógio")))
        assertFalse(MonitorService.rodando)
    }

    // ------------------------------------------------------------ Apoio

    private fun concederPermissaoDeNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inst = InstrumentationRegistry.getInstrumentation()
            inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun esperarAte(timeoutMs: Long = 5_000, condicao: () -> Boolean) {
        val fim = SystemClock.uptimeMillis() + timeoutMs
        while (!condicao() && SystemClock.uptimeMillis() < fim) SystemClock.sleep(100)
    }

    /** Toca no SeekBar numa fração da trilha (0 = início, 1 = fim), como um dedo faria. */
    private fun tocarEm(fracao: Float): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(SeekBar::class.java)
        override fun getDescription() = "tocar no SeekBar em ${(fracao * 100).toInt()}%"
        override fun perform(uiController: UiController, view: View) {
            val sb = view as SeekBar
            val coords = CoordinatesProvider { v ->
                val loc = IntArray(2).also { v.getLocationOnScreen(it) }
                val trilha = v.width - sb.paddingLeft - sb.paddingRight
                // um pouco além das pontas garante o mínimo/máximo exato
                val x = loc[0] + sb.paddingLeft + trilha * fracao + if (fracao >= 1f) 20 else if (fracao <= 0f) -20 else 0
                floatArrayOf(x, loc[1] + v.height / 2f)
            }
            GeneralClickAction(Tap.SINGLE, coords, Press.FINGER, 0, 0).perform(uiController, view)
        }
    }
}
