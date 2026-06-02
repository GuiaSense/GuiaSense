// Estado inicial
let alcanceMaximo = 1.00; // metros

const btnConectar = document.getElementById('btn_conectar');
const txtStatus = document.getElementById('txt_status_conexao');
const txtDist = document.getElementById('txt_distancia');
const txtInt = document.getElementById('txt_intensidade');
const viewBar = document.getElementById('view_barra_intensidade');
const viewFill = document.getElementById('view_intensidade');
const simRange = document.getElementById('sim_distance');
const modes = document.querySelectorAll('.mode');
const seekAlc = document.getElementById('seekbar_alcance');
const txtAlcAtual = document.getElementById('txt_alcance_atual');
const txtInfo = document.getElementById('txt_info_vibracao');
const btnSalvar = document.getElementById('btn_salvar');
const txtFeedback = document.getElementById('txt_feedback');
const wifiSsid = document.getElementById('wifi_ssid');
const wifiPass = document.getElementById('wifi_pass');
const btnSendWifi = document.getElementById('btn_send_wifi');
const txtWifiFeedback = document.getElementById('txt_wifi_feedback');

function formatMeters(v){ return (v/100).toFixed(2) + 'm' }

function updateVibrationInfo(){
  let info = '';
  if (alcanceMaximo === 1.00){
    info = `📏 Modo Perto (1,00m):\n• < 0,60m → Vibração Forte\n• 0,60m - 0,80m → Vibração Média\n• 0,80m - 1,00m → Vibração Leve`;
  } else if (alcanceMaximo === 1.50){
    info = `📏 Modo Médio (1,50m):\n• < 0,40m → Vibração Forte\n• 0,40m - 0,80m → Vibração Média\n• 0,80m - 1,50m → Vibração Leve`;
  } else {
    const forte = (alcanceMaximo * 0.4).toFixed(2);
    const media = (alcanceMaximo * 0.7).toFixed(2);
    info = `📏 Modo Personalizado (${alcanceMaximo.toFixed(2)}m):\n• < ${forte}m → Vibração Forte\n• ${forte}m - ${media}m → Vibração Média\n• ${media}m - ${alcanceMaximo.toFixed(2)}m → Vibração Leve`;
  }
  txtInfo.textContent = info;
}

function simulateSensorReading(distanceMeters){
  txtDist.textContent = distanceMeters.toFixed(2) + 'm';
  let intensidade = 'Nenhuma ⚪';
  const d = distanceMeters;

  if (alcanceMaximo === 1.00){
    if (d < 0.60) intensidade = 'Forte 🔴';
    else if (d < 0.80) intensidade = 'Média 🟠';
    else if (d < 1.00) intensidade = 'Leve 🟡';
  } else if (alcanceMaximo === 1.50){
    if (d < 0.40) intensidade = 'Forte 🔴';
    else if (d < 0.80) intensidade = 'Média 🟠';
    else if (d < 1.50) intensidade = 'Leve 🟡';
  } else {
    const forte = alcanceMaximo * 0.4;
    const media = alcanceMaximo * 0.7;
    if (d < forte) intensidade = 'Forte 🔴';
    else if (d < media) intensidade = 'Média 🟠';
    else if (d < alcanceMaximo) intensidade = 'Leve 🟡';
  }

  txtInt.textContent = intensidade;

  // Atualiza barra visual (percentual em relação ao alcanceMaximo)
  const percentual = Math.min(d / alcanceMaximo, 1);
  viewFill.style.width = (percentual * 100) + '%';
}

// Conectar / desconectar simulado
let conectado = false;
btnConectar.addEventListener('click', ()=>{
  conectado = !conectado;
  txtStatus.textContent = conectado ? 'Conectado' : 'Desconectado';
  btnConectar.textContent = conectado ? 'Desconectar' : 'Conectar ao Óculos';
});

// Modos rápidos
modes.forEach(b=>{
  b.addEventListener('click', ()=>{
    modes.forEach(x=>x.classList.remove('active'));
    b.classList.add('active');
    const v = parseFloat(b.dataset.value);
    alcanceMaximo = v;
    seekAlc.value = Math.round(v * 100);
    txtAlcAtual.textContent = alcanceMaximo.toFixed(2) + 'm';
    updateVibrationInfo();
    showFeedback(`Modo ${v === 1.00 ? 'Perto' : 'Médio'} selecionado`);
    try { saveConfigToFirebase(alcanceMaximo); } catch(e){ console.warn('saveConfig erro', e); }
  })
});

// Seekbar personalizado
seekAlc.addEventListener('input', ()=>{
  const val = parseInt(seekAlc.value,10);
  alcanceMaximo = val / 100;
  txtAlcAtual.textContent = alcanceMaximo.toFixed(2) + 'm';
  modes.forEach(x=>x.classList.remove('active'));
  updateVibrationInfo();
});

// Ao terminar de ajustar (evento change), salva no Firebase
seekAlc.addEventListener('change', ()=>{
  try { saveConfigToFirebase(alcanceMaximo); showFeedback('✅ Configuração salva no Firebase'); }
  catch(e){ console.warn('saveConfig erro', e); }
});

// Salvar: simula salvar local e enviar notificacao
btnSalvar.addEventListener('click', ()=>{
  localStorage.setItem('alcance_maximo', alcanceMaximo);
  try {
    saveConfigToFirebase(alcanceMaximo);
  } catch (e) {
    console.warn('Erro ao tentar salvar no Firebase', e);
  }
  showFeedback(`✅ Configuração salva! Alcance: ${alcanceMaximo.toFixed(2)}m`);
});

function showFeedback(msg){
  txtFeedback.textContent = msg;
  txtFeedback.style.opacity = '1';
  setTimeout(()=>{ txtFeedback.style.opacity = '0'; }, 3000);
}

// Simulação por controle deslizante
simRange.addEventListener('input', ()=>{
  if (window._isLiveData) return;
  const cm = parseInt(simRange.value,10);
  const m = cm / 100; // range em centímetros -> metros
  simulateSensorReading(m);
});

// Inicialização da interface
updateVibrationInfo();
simulateSensorReading(parseInt(simRange.value,10)/100);
window._isLiveData = false;

// --- Configuração e Autenticação Firebase ---

if (typeof firebase !== 'undefined' && firebase.auth) {
  // Autenticação Anônima no Web App
  firebase.auth().signInAnonymously()
    .then(() => {
      console.log("Página logada anonimamente no Firebase com sucesso!");
      // Inicia a escuta dos dados apenas após o login
      listenSensorStatus();
    })
    .catch((error) => {
      console.error("Erro ao autenticar a página web:", error.code, error.message);
      txtStatus.textContent = 'Erro de Autenticação';
    });
} else {
  console.warn('Firebase ou Firebase Auth não disponível. Modo simulado ativo.');
}

// Habilita listener do status do sensor
function listenSensorStatus() {
  if (typeof firebase === 'undefined' || !firebase.database) {
    console.warn('Firebase Database não disponível.');
    return;
  }
  
  const ref = firebase.database().ref('estado_dispositivo');
  
  ref.on('value', snapshot => {
    const data = snapshot.val();
    const simLabel = document.querySelector('.sim-label');
    
    if (!data) {
      window._isLiveData = false;
      if (simRange) simRange.disabled = false;
      if (simLabel) simLabel.textContent = 'Simular distância';
      txtStatus.textContent = 'Desconectado';
      return;
    }
    
    // Há dados ao vivo
    window._isLiveData = true;
    if (simRange) simRange.disabled = true;
    if (simLabel) simLabel.textContent = 'Ao Vivo';
    
    // Atualiza status de conexão
    txtStatus.textContent = (data.conectado ? 'Conectado' : 'Desconectado') + (window._isLiveData ? ' (Ao Vivo)' : '');

    // Atualiza distância
    if (data.ultima_distancia !== undefined && data.ultima_distancia !== null) {
      let distanceMeters = Number(data.ultima_distancia);
      if (distanceMeters > 20) {
        distanceMeters = distanceMeters / 100.0; // cm -> m se o valor for muito alto
      }
      try { simulateSensorReading(Number(distanceMeters)); }
      catch(e){ console.error('Erro ao aplicar leitura do sensor', e); }
    }

    // Atualiza vibração
    if (data.vibracao_atual) {
      txtInt.textContent = data.vibracao_atual;
    }
  }, err => {
    console.error('Erro no listener estado_dispositivo:', err);
  });
}

// --- Firebase helper (grava no Realtime Database) ---
function saveConfigToFirebase(alcance) {
  if (typeof firebase === 'undefined' || !firebase.database) {
    console.warn('Firebase não está disponível no contexto web.');
    return;
  }
  
  const ref = firebase.database().ref('configuracoes');
  
  const payload = {
    alcance_maximo: alcance
  };
  
  ref.set(payload)
    .then(()=> console.log('Configuração gravada no Firebase'))
    .catch(err => console.error('Erro ao gravar no Firebase', err));
}

// --- Wi‑Fi provisioning via Firebase ---
btnSendWifi.addEventListener('click', ()=>{
  const ssid = (wifiSsid && wifiSsid.value || '').trim();
  const pass = (wifiPass && wifiPass.value || '').trim();
  if (!ssid) { txtWifiFeedback.textContent = 'Informe o SSID.'; return; }
  if (!pass) { txtWifiFeedback.textContent = 'Informe a senha.'; return; }
  txtWifiFeedback.textContent = 'Enviando...';
  try {
    saveWifiToFirebase(ssid, pass);
  } catch(e){
    console.error(e);
    txtWifiFeedback.textContent = 'Erro ao enviar.';
  }
});

function saveWifiToFirebase(ssid, password) {
  if (typeof firebase === 'undefined' || !firebase.database) {
    console.warn('Firebase não disponível.');
    txtWifiFeedback.textContent = 'Firebase não disponível.';
    return;
  }
  
  const ref = firebase.database().ref('credenciais_wifi');
  const payload = {
    ssid: ssid,
    password: password,
    timestamp: Date.now()
  };
  
  ref.set(payload)
    .then(()=> {
      console.log('Credenciais Wi‑Fi gravadas no Firebase');
      txtWifiFeedback.textContent = 'Credenciais enviadas com sucesso.';
      if (wifiPass) wifiPass.value = '';
    })
    .catch(err => { console.error(err); txtWifiFeedback.textContent = 'Erro ao gravar credenciais.'; });
}
