package com.guiasense.dehjp

import java.util.Locale

/**
 * Regras de alerta do GuiaSense (lógica pura, sem Android — testada em AlertaVibracaoTest).
 * Espelho de firmware/GuiaSense/alerta.h e de alerta.js (web). Altere os três juntos.
 */
object AlertaVibracao {

    const val FRACAO_FORTE = 0.40
    const val FRACAO_MEDIA = 0.70
    const val ALCANCE_MIN_M = 0.50
    const val ALCANCE_MAX_M = 2.00
    const val ALCANCE_PADRAO_M = 1.00

    /** Sem atualização do óculos por mais que isso => offline / dado velho. */
    const val LIMITE_OFFLINE_MS = 5_000L

    // Caminhos no Realtime Database (contrato com firmware e web).
    const val PATH_ESTADO = "estado_dispositivo"
    const val PATH_CONFIG = "configuracoes/usuario_id"

    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    enum class Nivel(val textoFirebase: String, val rotulo: String) {
        NENHUMA("Nenhuma", "Nenhuma ⚪"),
        LEVE("Leve", "Leve 🟡"),
        MEDIA("Media", "Média 🟠"),
        FORTE("Forte", "Forte 🔴");

        companion object {
            /** Converte o texto gravado pelo firmware (ex.: "Media"). Desconhecido => NENHUMA. */
            fun doFirebase(texto: String?): Nivel =
                entries.firstOrNull { it.textoFirebase.equals(texto?.trim(), ignoreCase = true) }
                    ?: NENHUMA
        }
    }

    fun classificar(distanciaM: Double?, alcanceM: Double): Nivel {
        if (distanciaM == null || distanciaM.isNaN() || distanciaM < 0 || alcanceM <= 0) return Nivel.NENHUMA
        return when {
            distanciaM < alcanceM * FRACAO_FORTE -> Nivel.FORTE
            distanciaM < alcanceM * FRACAO_MEDIA -> Nivel.MEDIA
            distanciaM < alcanceM -> Nivel.LEVE
            else -> Nivel.NENHUMA
        }
    }

    fun limitarAlcance(alcanceM: Double?): Double {
        if (alcanceM == null || alcanceM.isNaN() || alcanceM <= 0) return ALCANCE_PADRAO_M
        return alcanceM.coerceIn(ALCANCE_MIN_M, ALCANCE_MAX_M)
    }

    fun estaOnline(ultimaAtualizacaoMs: Long?, agoraServidorMs: Long): Boolean =
        ultimaAtualizacaoMs != null && agoraServidorMs - ultimaAtualizacaoMs <= LIMITE_OFFLINE_MS

    /** 0..1 — quanto mais perto o obstáculo, mais cheia a barra. */
    fun intensidade(distanciaM: Double?, alcanceM: Double): Double {
        if (distanciaM == null || distanciaM < 0 || alcanceM <= 0) return 0.0
        return (1.0 - distanciaM / alcanceM).coerceIn(0.0, 1.0)
    }

    fun metros(valor: Double): String = String.format(PT_BR, "%.2f m", valor)

    fun textoDistancia(distanciaM: Double?): String =
        if (distanciaM == null || distanciaM < 0) "Livre (sem obstáculo)" else metros(distanciaM)

    fun textoFaixas(alcanceM: Double): String {
        val forte = alcanceM * FRACAO_FORTE
        val media = alcanceM * FRACAO_MEDIA
        return "📏 Alcance configurado: ${metros(alcanceM)}\n" +
            "• menos de ${metros(forte)} → Vibração Forte\n" +
            "• de ${metros(forte)} a ${metros(media)} → Vibração Média\n" +
            "• de ${metros(media)} a ${metros(alcanceM)} → Vibração Leve\n" +
            "• acima de ${metros(alcanceM)} → sem vibração"
    }
}
