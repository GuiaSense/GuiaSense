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

// Salvar: simula salvar local e enviar notificacao
btnSalvar.addEventListener('click', ()=>{
  localStorage.setItem('alcance_maximo', alcanceMaximo);
  showFeedback(`✅ Configuração salva! Alcance: ${alcanceMaximo.toFixed(2)}m`);
});

function showFeedback(msg){
  txtFeedback.textContent = msg;
  txtFeedback.style.opacity = '1';
  setTimeout(()=>{ txtFeedback.style.opacity = '0'; }, 3000);
}

// Simulação por controle deslizante
simRange.addEventListener('input', ()=>{
  const cm = parseInt(simRange.value,10);
  const m = cm / 100; // range em centímetros -> metros
  simulateSensorReading(m);
});

// Inicialização
updateVibrationInfo();
simulateSensorReading(parseInt(simRange.value,10)/100);
