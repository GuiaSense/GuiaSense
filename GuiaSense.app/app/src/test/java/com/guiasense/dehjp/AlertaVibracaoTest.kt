package com.guiasense.dehjp

import com.guiasense.dehjp.AlertaVibracao.Nivel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mesmos casos de firmware/test e tests/alerta.test.js: as três camadas precisam concordar. */
class AlertaVibracaoTest {

    @Test fun faixasComAlcanceDe1m() {
        assertEquals(Nivel.FORTE, AlertaVibracao.classificar(0.00, 1.0))
        assertEquals(Nivel.FORTE, AlertaVibracao.classificar(0.39, 1.0))
        assertEquals(Nivel.MEDIA, AlertaVibracao.classificar(0.40, 1.0))
        assertEquals(Nivel.MEDIA, AlertaVibracao.classificar(0.69, 1.0))
        assertEquals(Nivel.LEVE, AlertaVibracao.classificar(0.70, 1.0))
        assertEquals(Nivel.LEVE, AlertaVibracao.classificar(0.99, 1.0))
        assertEquals(Nivel.NENHUMA, AlertaVibracao.classificar(1.00, 1.0))
    }

    @Test fun faixasEscalamComOAlcance() {
        assertEquals(Nivel.FORTE, AlertaVibracao.classificar(0.59, 1.5))
        assertEquals(Nivel.MEDIA, AlertaVibracao.classificar(1.00, 1.5))
        assertEquals(Nivel.LEVE, AlertaVibracao.classificar(1.20, 1.5))
        assertEquals(Nivel.NENHUMA, AlertaVibracao.classificar(1.60, 1.5))
    }

    @Test fun semObstaculoNaoAlerta() {
        assertEquals(Nivel.NENHUMA, AlertaVibracao.classificar(-1.0, 2.0))
        assertEquals(Nivel.NENHUMA, AlertaVibracao.classificar(null, 2.0))
        assertEquals(Nivel.NENHUMA, AlertaVibracao.classificar(Double.NaN, 2.0))
    }

    @Test fun textoDoFirmwareViraNivel() {
        assertEquals(Nivel.FORTE, Nivel.doFirebase("Forte"))
        assertEquals(Nivel.MEDIA, Nivel.doFirebase("Media"))
        assertEquals(Nivel.LEVE, Nivel.doFirebase(" leve "))
        assertEquals(Nivel.NENHUMA, Nivel.doFirebase(null))
        assertEquals(Nivel.NENHUMA, Nivel.doFirebase("???"))
    }

    @Test fun limitarAlcance() {
        assertEquals(0.5, AlertaVibracao.limitarAlcance(0.1), 1e-9)
        assertEquals(2.0, AlertaVibracao.limitarAlcance(5.0), 1e-9)
        assertEquals(1.0, AlertaVibracao.limitarAlcance(null), 1e-9)
        assertEquals(1.0, AlertaVibracao.limitarAlcance(-2.0), 1e-9)
    }

    @Test fun onlineDependeDaUltimaAtualizacao() {
        assertTrue(AlertaVibracao.estaOnline(10_000, 14_000))
        assertFalse(AlertaVibracao.estaOnline(10_000, 16_000))
        assertFalse(AlertaVibracao.estaOnline(null, 16_000))
    }

    @Test fun textosEmPortugues() {
        assertEquals("1,50 m", AlertaVibracao.metros(1.5))
        assertEquals("Livre (sem obstáculo)", AlertaVibracao.textoDistancia(-1.0))
        assertTrue(AlertaVibracao.textoFaixas(1.0).contains("menos de 0,40 m → Vibração Forte"))
        assertFalse(AlertaVibracao.textoFaixas(1.0).contains("&lt;"))
    }
}
