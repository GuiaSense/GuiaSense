package com.guiasense.dehjp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O GuiaSense é tecnologia assistiva: a própria tela precisa ser acessível
 * (TalkBack, contraste, tamanho de toque). O Accessibility Test Framework do
 * Google verifica a tela inteira a cada ação do Espresso e falha o teste se
 * encontrar um erro.
 */
@RunWith(AndroidJUnit4::class)
class AcessibilidadeTest {

    companion object {
        @BeforeClass @JvmStatic
        fun ligarVerificacoes() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
    }

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun abrirTela() {
        AppConfig.firebaseHabilitado = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun fechar() = scenario.close()

    @Test
    fun telaPrincipal_semProblemasDeAcessibilidade() {
        onView(withId(R.id.rb_perto)).perform(scrollTo(), click())
        onView(withId(R.id.rb_medio)).perform(scrollTo(), click())
        onView(withId(R.id.btn_salvar)).perform(scrollTo(), click())
    }
}
