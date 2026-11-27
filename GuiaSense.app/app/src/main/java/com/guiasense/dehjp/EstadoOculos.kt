package com.guiasense.dehjp

import com.google.firebase.database.DataSnapshot

/** Configurações globais do app. */
object AppConfig {
    /**
     * false = não conecta nem grava no Firebase. Usado pelos testes de interface,
     * para que eles não alterem o alcance do óculos real no banco.
     */
    @Volatile
    var firebaseHabilitado = true
}

/** Último estado publicado pelo óculos em `estado_dispositivo` (ver docs/ARQUITETURA.md). */
data class EstadoOculos(
    val distanciaM: Double?,
    val nivel: AlertaVibracao.Nivel,
    val atualizadoEmMs: Long?,
) {
    companion object {
        fun de(s: DataSnapshot): EstadoOculos? {
            if (!s.exists()) return null
            return EstadoOculos(
                distanciaM = s.child("ultima_distancia").getValue(Double::class.java),
                nivel = AlertaVibracao.Nivel.doFirebase(s.child("vibracao_atual").getValue(String::class.java)),
                atualizadoEmMs = s.child("ultima_atualizacao").getValue(Long::class.java),
            )
        }
    }
}
