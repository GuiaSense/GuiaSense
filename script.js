// GuiaSense — painel web
// Lê o estado do óculos em tempo real e grava o alcance configurado no
// Firebase Realtime Database. Caminhos (os mesmos do firmware e do app):
//   estado_dispositivo                         <- escrito pelo óculos
//   configuracoes/usuario_id/alcance_maximo    <- escrito pelo app/web, lido pelo óculos
'use strict';

const A = window.GuiaSenseAlerta;
const PATH_ESTADO = 'estado_dispositivo';
const PATH_CONFIG = 'configuracoes/usuario_id';

const $ = (id) => document.getElementById(id);
const txtStatus = $('txt_status_conexao');
const statusDot = $('status_dot');
const txtDist = $('txt_distancia');
const txtInt = $('txt_intensidade');
const viewFill = $('view_intensidade');
const simRange = $('sim_distance');
const simLabel = $('sim_label');
const modes = document.querySelectorAll('.mode');
const seekAlc = $('seekbar_alcance');
const txtAlcAtual = $('txt_alcance_atual');
const txtInfo = $('txt_info_vibracao');
const btnSalvar = $('btn_salvar');
const txtFeedback = $('txt_feedback');

let alcanceMaximo = A.ALCANCE_PADRAO_M;
let ultimoEstado = null;        // último snapshot de estado_dispositivo
let offsetServidorMs = 0;       // diferença relógio local x servidor Firebase
let aoVivo = false;
let ultimoNivelVibrado = 'Nenhuma';
let feedbackTimer = null;

const fmt = (m) => m.toFixed(2).replace('.', ',') + ' m';

// ------------------------------------------------------------------ UI
function atualizarInfoVibracao() {
  const f = A.faixas(alcanceMaximo);
  txtInfo.textContent =
    `📏 Alcance configurado: ${fmt(alcanceMaximo)}\n` +
    `• menos de ${fmt(f.forte)} → Vibração Forte\n` +
    `• de ${fmt(f.forte)} a ${fmt(f.media)} → Vibração Média\n` +
    `• de ${fmt(f.media)} a ${fmt(f.leve)} → Vibração Leve\n` +
    `• acima de ${fmt(f.leve)} → sem vibração`;
}

function marcarModo() {
  modes.forEach((b) => {
    const ativo = Math.abs(parseFloat(b.dataset.value) - alcanceMaximo) < 0.001;
    b.classList.toggle('active', ativo);
    b.setAttribute('aria-pressed', String(ativo));
  });
}

function aplicarAlcanceNaTela(valor) {
  alcanceMaximo = A.limitarAlcance(valor);
  seekAlc.value = Math.round(alcanceMaximo * 100);
  txtAlcAtual.textContent = fmt(alcanceMaximo);
  marcarModo();
  atualizarInfoVibracao();
}

/** Mostra uma leitura. `nivelDoDispositivo` (se houver) prevalece sobre o cálculo local. */
function mostrarLeitura(distanciaM, nivelDoDispositivo) {
  const semObstaculo = typeof distanciaM !== 'number' || distanciaM < 0;
  // Sem óculos ao vivo, o valor vem do controle de simulação: deixar isso explícito.
  txtDist.textContent = (semObstaculo ? 'Livre (sem obstáculo)' : fmt(distanciaM)) + (aoVivo ? '' : ' (simulado)');
  const nivel = nivelDoDispositivo || A.classificar(distanciaM, alcanceMaximo);
  txtInt.textContent = A.rotulo(nivel);
  txtInt.dataset.nivel = nivel;
  viewFill.style.width = (A.intensidade(distanciaM, alcanceMaximo) * 100) + '%';
  viewFill.dataset.nivel = nivel;
  vibrarSeMudou(nivel);
}

// Vibração no próprio celular quando a página está aberta (Chrome/Android).
const PADROES = { Leve: [120], Media: [200, 100, 200], Forte: [400, 80, 400, 80, 400] };
function vibrarSeMudou(nivel) {
  if (nivel === ultimoNivelVibrado) return;
  ultimoNivelVibrado = nivel;
  if (aoVivo && navigator.vibrate && PADROES[nivel]) navigator.vibrate(PADROES[nivel]);
}

function mostrarFeedback(msg, erro) {
  txtFeedback.textContent = msg;
  txtFeedback.classList.toggle('erro', !!erro);
  txtFeedback.style.opacity = '1';
  clearTimeout(feedbackTimer);
  feedbackTimer = setTimeout(() => { txtFeedback.style.opacity = '0'; }, 3000);
}

function definirStatus(texto, estado) {
  txtStatus.textContent = texto;
  statusDot.dataset.estado = estado; // online | offline | aguardando | erro
}

function definirModoAoVivo(ligado) {
  aoVivo = ligado;
  simRange.disabled = ligado;
  simLabel.textContent = ligado
    ? 'Leitura ao vivo do óculos (simulação desativada)'
    : 'Simular distância (óculos offline)';
}

// ------------------------------------------------------------- Eventos
modes.forEach((b) => {
  b.addEventListener('click', () => {
    aplicarAlcanceNaTela(parseFloat(b.dataset.value));
    salvarAlcance(alcanceMaximo);
  });
});

seekAlc.addEventListener('input', () => {
  aplicarAlcanceNaTela(parseInt(seekAlc.value, 10) / 100);
  if (!aoVivo) mostrarLeitura(parseInt(simRange.value, 10) / 100);
});
seekAlc.addEventListener('change', () => salvarAlcance(alcanceMaximo));
btnSalvar.addEventListener('click', () => salvarAlcance(alcanceMaximo));

simRange.addEventListener('input', () => {
  if (aoVivo) return;
  mostrarLeitura(parseInt(simRange.value, 10) / 100);
});

// ------------------------------------------------------------ Firebase
function firebaseDisponivel() {
  return typeof firebase !== 'undefined' && firebase.apps && firebase.apps.length > 0;
}

function salvarAlcance(valor) {
  try { localStorage.setItem('alcance_maximo', String(valor)); } catch (e) { /* modo privado */ }
  if (!firebaseDisponivel()) {
    mostrarFeedback('Sem conexão com o Firebase — salvo só neste navegador.', true);
    return;
  }
  // update() no nó do usuário: não apaga outros campos nem outros usuários.
  firebase.database().ref(PATH_CONFIG)
    .update({ alcance_maximo: valor, timestamp: firebase.database.ServerValue.TIMESTAMP })
    .then(() => mostrarFeedback(`✅ Alcance de ${fmt(valor)} enviado ao óculos`))
    .catch((err) => {
      console.error('Erro ao gravar configuração', err);
      mostrarFeedback('Erro ao salvar: ' + (err.code || err.message), true);
    });
}

function avaliarConexao() {
  if (!ultimoEstado) {
    definirModoAoVivo(false);
    definirStatus('Aguardando o óculos enviar dados…', 'aguardando');
    return;
  }
  const agoraServidor = Date.now() + offsetServidorMs;
  const online = A.estaOnline(ultimoEstado.ultima_atualizacao, agoraServidor);
  definirModoAoVivo(online);
  if (online) {
    definirStatus('Óculos conectado (ao vivo)', 'online');
  } else {
    const ts = ultimoEstado.ultima_atualizacao;
    const quando = typeof ts === 'number'
      ? new Date(ts).toLocaleString('pt-BR')
      : 'desconhecido';
    definirStatus(`Óculos offline — último sinal: ${quando}`, 'offline');
  }
}

function iniciarFirebase() {
  const db = firebase.database();

  db.ref('.info/serverTimeOffset').on('value', (s) => { offsetServidorMs = s.val() || 0; });

  db.ref(PATH_ESTADO).on('value', (snap) => {
    ultimoEstado = snap.val();
    avaliarConexao();
    if (ultimoEstado && aoVivo) {
      const d = Number(ultimoEstado.ultima_distancia);
      mostrarLeitura(isFinite(d) ? d : -1, ultimoEstado.vibracao_atual);
    }
  }, (err) => {
    console.error('Erro ao ler estado_dispositivo', err);
    definirStatus('Sem permissão para ler o banco (' + err.code + ')', 'erro');
  });

  // Mantém a tela sincronizada se o alcance for alterado pelo app Android.
  db.ref(PATH_CONFIG + '/alcance_maximo').on('value', (snap) => {
    if (snap.exists()) aplicarAlcanceNaTela(snap.val());
  });

  // Sem novos dados, o "ao vivo" precisa expirar sozinho.
  setInterval(avaliarConexao, 1000);
}

// ------------------------------------------------------------ Início
(function iniciar() {
  let salvo = NaN;
  try { salvo = parseFloat(localStorage.getItem('alcance_maximo')); } catch (e) { /* ignore */ }
  aplicarAlcanceNaTela(isFinite(salvo) ? salvo : A.ALCANCE_PADRAO_M);
  definirModoAoVivo(false);
  mostrarLeitura(parseInt(simRange.value, 10) / 100);

  if (!firebaseDisponivel() || !firebase.auth) {
    definirStatus('Firebase indisponível — modo simulação', 'erro');
    return;
  }
  definirStatus('Conectando ao Firebase…', 'aguardando');
  firebase.auth().signInAnonymously()
    .then(iniciarFirebase)
    .catch((error) => {
      console.error('Erro ao autenticar', error);
      definirStatus('Erro de autenticação (' + error.code + ')', 'erro');
    });
})();
