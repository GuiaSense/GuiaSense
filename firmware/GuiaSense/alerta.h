// alerta.h — Regras de alerta do GuiaSense (lógica pura, sem dependência de hardware).
//
// Este arquivo é compilado tanto no ESP32 quanto no PC (testes unitários em
// firmware/test). As MESMAS regras estão replicadas em:
//   - web:     alerta.js
//   - Android: AlertaVibracao.kt
// Se alterar algo aqui, altere lá também (e rode os testes dos três).
#pragma once

#include <stdint.h>

namespace guiasense {

// Faixas de alerta como fração do alcance máximo configurado.
constexpr float FRACAO_FORTE = 0.40f;  // d < 40% do alcance  -> Forte
constexpr float FRACAO_MEDIA = 0.70f;  // d < 70% do alcance  -> Média
                                       // d < 100% do alcance -> Leve

// Limites do alcance configurável pelo app (metros).
constexpr float ALCANCE_MIN_M = 0.50f;
constexpr float ALCANCE_MAX_M = 2.00f;
constexpr float ALCANCE_PADRAO_M = 1.00f;

// Acima disso o VL53L0X não é confiável (mesmo em modo longo alcance);
// a biblioteca Pololu devolve ~8190 mm quando não há alvo.
constexpr uint16_t LEITURA_MAX_VALIDA_MM = 2200;

// Valor enviado ao banco quando não há obstáculo detectável.
constexpr float SEM_OBSTACULO = -1.0f;

enum class Nivel : uint8_t { Nenhuma = 0, Leve = 1, Media = 2, Forte = 3 };

// Converte a leitura bruta do sensor em metros, ou SEM_OBSTACULO se inválida.
inline float leituraParaMetros(uint16_t mm, bool timeout) {
  if (timeout || mm > LEITURA_MAX_VALIDA_MM) return SEM_OBSTACULO;
  return mm / 1000.0f;
}

// Garante que o alcance vindo do banco esteja dentro da faixa suportada.
// Valores não finitos ou <= 0 voltam ao padrão.
inline float limitarAlcance(float alcance_m) {
  if (!(alcance_m > 0.0f) || alcance_m != alcance_m) return ALCANCE_PADRAO_M;
  if (alcance_m < ALCANCE_MIN_M) return ALCANCE_MIN_M;
  if (alcance_m > ALCANCE_MAX_M) return ALCANCE_MAX_M;
  return alcance_m;
}

inline Nivel classificar(float distancia_m, float alcance_m) {
  if (distancia_m < 0.0f || alcance_m <= 0.0f) return Nivel::Nenhuma;
  if (distancia_m < alcance_m * FRACAO_FORTE) return Nivel::Forte;
  if (distancia_m < alcance_m * FRACAO_MEDIA) return Nivel::Media;
  if (distancia_m < alcance_m) return Nivel::Leve;
  return Nivel::Nenhuma;
}

// Texto gravado em estado_dispositivo/vibracao_atual (contrato com app e web:
// ASCII, sem acento).
inline const char* nomeNivel(Nivel n) {
  switch (n) {
    case Nivel::Forte: return "Forte";
    case Nivel::Media: return "Media";
    case Nivel::Leve:  return "Leve";
    default:           return "Nenhuma";
  }
}

}  // namespace guiasense
