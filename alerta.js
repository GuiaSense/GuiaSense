// alerta.js — regras de alerta do GuiaSense para a página web.
// Espelho de firmware/GuiaSense/alerta.h e do AlertaVibracao.kt do app.
// Funciona no navegador (window.GuiaSenseAlerta) e no Node (require) para testes.
(function (root) {
  'use strict';

  const FRACAO_FORTE = 0.40;
  const FRACAO_MEDIA = 0.70;
  const ALCANCE_MIN_M = 0.50;
  const ALCANCE_MAX_M = 2.00;
  const ALCANCE_PADRAO_M = 1.00;
  // Sem atualização do dispositivo por mais que isso => consideramos offline.
  const LIMITE_OFFLINE_MS = 5000;

  /** Retorna 'Forte' | 'Media' | 'Leve' | 'Nenhuma' (mesmo texto que o firmware grava). */
  function classificar(distanciaM, alcanceM) {
    if (typeof distanciaM !== 'number' || !isFinite(distanciaM) || distanciaM < 0) return 'Nenhuma';
    if (!(alcanceM > 0)) return 'Nenhuma';
    if (distanciaM < alcanceM * FRACAO_FORTE) return 'Forte';
    if (distanciaM < alcanceM * FRACAO_MEDIA) return 'Media';
    if (distanciaM < alcanceM) return 'Leve';
    return 'Nenhuma';
  }

  function faixas(alcanceM) {
    return { forte: alcanceM * FRACAO_FORTE, media: alcanceM * FRACAO_MEDIA, leve: alcanceM };
  }

  function limitarAlcance(alcanceM) {
    const v = Number(alcanceM);
    if (!isFinite(v) || v <= 0) return ALCANCE_PADRAO_M;
    return Math.min(ALCANCE_MAX_M, Math.max(ALCANCE_MIN_M, v));
  }

  /** Online se a última atualização (hora do servidor, ms) for recente. */
  function estaOnline(ultimaAtualizacaoMs, agoraServidorMs, limiteMs) {
    if (typeof ultimaAtualizacaoMs !== 'number') return false;
    return agoraServidorMs - ultimaAtualizacaoMs <= (limiteMs || LIMITE_OFFLINE_MS);
  }

  const ROTULOS = {
    Forte: 'Forte 🔴',
    Media: 'Média 🟠',
    Leve: 'Leve 🟡',
    Nenhuma: 'Nenhuma ⚪',
  };
  function rotulo(nivel) { return ROTULOS[nivel] || ROTULOS.Nenhuma; }

  // Fração de 0 a 1 para a barra de intensidade (quanto mais perto, mais cheia).
  function intensidade(distanciaM, alcanceM) {
    if (typeof distanciaM !== 'number' || distanciaM < 0 || !(alcanceM > 0)) return 0;
    return Math.min(1, Math.max(0, 1 - distanciaM / alcanceM));
  }

  const api = {
    FRACAO_FORTE, FRACAO_MEDIA, ALCANCE_MIN_M, ALCANCE_MAX_M, ALCANCE_PADRAO_M, LIMITE_OFFLINE_MS,
    classificar, faixas, limitarAlcance, estaOnline, rotulo, intensidade,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GuiaSenseAlerta = api;
})(typeof window !== 'undefined' ? window : globalThis);
