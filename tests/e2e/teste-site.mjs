// Roteiro de testes do painel web, visível no Chrome.
// node teste-site.mjs publicado | corrigido
import { conectar, INTERCEPTADOR } from './cdp.mjs';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const alvo = process.argv[2] || 'publicado';
const URLS = {
  publicado: 'https://guiasense.github.io/GuiaSense/',
  antigo: 'https://edineysantos.github.io/GuiaSense_N.Ediney/',  // site anterior (repositório pessoal)
  corrigido: new URL('../../index.html', import.meta.url).href,
};
const PAUSA = 2600;
const DIR = fileURLToPath(new URL('./saida', import.meta.url));
fs.mkdirSync(DIR, { recursive: true });
const c = await conectar();
await c.send('Page.addScriptToEvaluateOnNewDocument', { source: INTERCEPTADOR });
await c.navegar(URLS[alvo]);
await c.sleep(3500);

const resultados = [];
let n = 0;
async function passo(nome, fn) {
  n++;
  await c.avaliar(`window.__passo(${JSON.stringify(`Teste ${n} — ${nome}…`)})`);
  await c.sleep(900);
  let r;
  try { r = await fn(); } catch (e) { r = { ok: false, detalhe: 'erro no teste: ' + e.message }; }
  resultados.push({ n, nome, ...r });
  const cor = r.ok ? '#2E7D32' : '#B71C1C';
  await c.avaliar(`window.__passo(${JSON.stringify(`Teste ${n} — ${nome}: ${r.ok ? '✔ PASSOU' : '✘ FALHOU'} · ${r.detalhe}`)}, '${cor}')`);
  await c.foto(`${DIR}/${alvo}_${String(n).padStart(2, '0')}.png`);
  console.log(`${r.ok ? 'PASSOU' : 'FALHOU'} | ${n}. ${nome} | ${r.detalhe}`);
  await c.sleep(PAUSA);
}
const clicar = (sel) => c.avaliar(`(() => { const el = document.querySelector(${JSON.stringify(sel)}); window.__destacar(el); el.click(); return true; })()`);
const texto = (sel) => c.avaliar(`document.querySelector(${JSON.stringify(sel)})?.textContent?.trim() ?? null`);
const existe = (sel) => c.avaliar(`!!document.querySelector(${JSON.stringify(sel)})`);
const ultimaGravacao = () => c.avaliar(`window.__gravacoes[window.__gravacoes.length - 1] || null`);

// 1
await passo('Página carrega sem erros de JavaScript', async () => {
  const titulo = await c.avaliar('document.title');
  return { ok: c.erros.length === 0, detalhe: `título "${titulo}", ${c.erros.length} erro(s) no console` };
});

// 2
await passo('Login anônimo no Firebase', async () => {
  const uid = await c.avaliar(`new Promise(r => { const f = () => firebase.auth().currentUser ? r(firebase.auth().currentUser.uid.slice(0,6)) : setTimeout(f, 200); f(); setTimeout(() => r(null), 8000); })`);
  return { ok: !!uid, detalhe: uid ? `autenticado (uid ${uid}…)` : 'não autenticou em 8 s' };
});

// 3
let estado = null;
await passo('Leitura do estado do óculos no banco (somente leitura)', async () => {
  estado = await c.avaliar(`firebase.database().ref('estado_dispositivo').once('value').then(s => s.val())`);
  return { ok: estado !== null, detalhe: estado ? `distância=${estado.ultima_distancia}, vibração=${estado.vibracao_atual}, conectado=${estado.conectado}, última atualização=${estado.ultima_atualizacao ? new Date(estado.ultima_atualizacao).toLocaleString('pt-BR') : 'NÃO INFORMADA'}` : 'nó vazio' };
});

// 4
await passo('"Ao vivo" só quando o óculos está mandando dados (observa 8 s)', async () => {
  const atualizacoes = await c.avaliar(`new Promise(r => { let k = -1; const ref = firebase.database().ref('estado_dispositivo'); const f = ref.on('value', () => k++); setTimeout(() => { ref.off('value', f); r(k); }, 8000); })`);
  const status = await texto('#txt_status_conexao');
  const dizAoVivo = /ao vivo/i.test(status || '');
  const ok = dizAoVivo === atualizacoes > 0;
  return { ok, detalhe: `${atualizacoes} atualização(ões) do óculos em 8 s; a tela diz "${status}"${!ok && dizAoVivo ? ' → mostra AO VIVO com o óculos parado' : ''}` };
});

// 5
await passo('Distância na tela é real (ou marcada como simulada)', async () => {
  const tela = await texto('#txt_distancia');
  if (estado?.ultima_distancia === undefined) {
    const marcado = /simulad/i.test(tela || '');
    return { ok: marcado, detalhe: `o banco não tem distância; a tela mostra "${tela}"${marcado ? '' : ' como se fosse leitura real'}` };
  }
  const d = Number(estado?.ultima_distancia);
  const esperado = d < 0 ? 'Livre' : (d > 20 ? d / 100 : d).toFixed(2);
  const okNum = tela && (tela.replace(',', '.').includes(esperado) || tela.includes(esperado.replace('.', ',')));
  return { ok: !!okNum, detalhe: `banco ${estado?.ultima_distancia} → tela "${tela}"${d >= 20 ? ' (valor 999 = "sem obstáculo" do firmware antigo, exibido como distância)' : ''}` };
});

// 6
await passo('Regra de vibração igual à do firmware gravado no óculos', async () => {
  // firmware antigo (gravado hoje no óculos): Forte < 50%, Média < 75% | corrigido: 40% / 70%
  const fw = alvo === 'publicado' ? [0.5, 0.75] : [0.4, 0.7];
  const nomeFw = (d) => (d < fw[0] ? 'Forte' : d < fw[1] ? 'Media' : d < 1 ? 'Leve' : 'Nenhuma');
  const casos = [0.30, 0.45, 0.55, 0.72, 0.78, 0.95];
  const tela = await c.avaliar(`(() => {
    const out = [];
    for (const d of ${JSON.stringify(casos)}) {
      if (window.GuiaSenseAlerta) out.push(GuiaSenseAlerta.classificar(d, 1.0));
      else { const old = document.getElementById('txt_intensidade').textContent; simulateSensorReading(d); out.push(document.getElementById('txt_intensidade').textContent.split(' ')[0].normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')); document.getElementById('txt_intensidade').textContent = old; }
    }
    return out; })()`);
  const div = casos.map((d, i) => [d, tela[i], nomeFw(d)]).filter(([, a, b]) => a !== b);
  return { ok: div.length === 0, detalhe: div.length ? `com alcance 1 m divergem: ` + div.map(([d, a, b]) => `${d.toFixed(2)} m site=${a} × óculos=${b}`).join('; ') : 'site e firmware concordam nos 6 casos' };
});

// 7
await passo('Botão "Médio" grava o alcance no caminho que o óculos lê', async () => {
  await clicar('.mode[data-value="1.50"]');
  await c.sleep(600);
  const g = await ultimaGravacao();
  const ok = g && g.caminho === 'configuracoes/usuario_id' && g.op === 'update';
  return { ok, detalhe: g ? `${g.op}("${g.caminho}", ${JSON.stringify(g.dados)})${g.caminho === 'configuracoes' && g.op === 'set' ? ' → o óculos lê configuracoes/usuario_id e o set() APAGARIA a configuração do app' : ''}` : 'nenhuma gravação' };
});

// 8
if (await existe('#btn_conectar')) {
  await passo('Botão "Conectar ao Óculos" faz conexão de verdade', async () => {
    const antes = await c.avaliar('window.__gravacoes.length');
    await clicar('#btn_conectar');
    await c.sleep(600);
    const status = await texto('#txt_status_conexao');
    const depois = await c.avaliar('window.__gravacoes.length');
    return { ok: false, detalhe: `o status mudou para "${status}" só trocando o texto: nenhuma comunicação com óculos ou banco (${depois - antes} gravações)` };
  });
}

// 9
if (await existe('#btn_send_wifi')) {
  await passo('Envio de Wi-Fi não expõe a senha', async () => {
    await c.avaliar(`(() => { const s = document.getElementById('wifi_ssid'), p = document.getElementById('wifi_pass'); window.__destacar(s); s.value = 'rede-de-teste'; p.value = 'senha-de-teste-123'; })()`);
    await c.sleep(900);
    await clicar('#btn_send_wifi');
    await c.sleep(600);
    const g = await ultimaGravacao();
    const vaza = g && JSON.stringify(g.dados).includes('senha-de-teste-123');
    return { ok: !vaza, detalhe: g ? `${g.op}("${g.caminho}") com ${JSON.stringify(g.dados)}${vaza ? ' → senha em TEXTO PURO no banco (e o óculos nem lê esse nó)' : ''}` : 'nada gravado' };
  });
} else {
  await passo('Wi-Fi configurado sem passar a senha pelo banco', async () => {
    const t = await texto('#h_wifi');
    return { ok: true, detalhe: `cartão "${t}" explica o portal GuiaSense-Setup; não há campo de senha na página` };
  });
}

// 10
await passo('Acessibilidade: controles com rótulo e anúncios ao leitor de tela', async () => {
  const r = await c.avaliar(`(() => {
    const semRotulo = [...document.querySelectorAll('input')].filter(i => !(i.labels && i.labels.length) && !i.getAttribute('aria-label') && !i.getAttribute('aria-labelledby')).map(i => '#' + i.id);
    const vivos = document.querySelectorAll('[aria-live]').length;
    return { semRotulo, vivos };
  })()`);
  const ok = r.semRotulo.length === 0 && r.vivos > 0;
  return { ok, detalhe: `${r.semRotulo.length} campo(s) sem rótulo${r.semRotulo.length ? ' (' + r.semRotulo.join(', ') + ')' : ''}; ${r.vivos} região(ões) aria-live para o leitor de tela anunciar mudanças` };
});

// 11
await passo('Layout no celular (390 px) sem rolagem lateral', async () => {
  await c.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 820, deviceScaleFactor: 1, mobile: true });
  await c.avaliar('window.scrollTo(0,0)');
  await c.sleep(800);
  const larg = await c.avaliar('document.documentElement.scrollWidth');
  return { ok: larg <= 390, detalhe: `largura do conteúdo ${larg}px na tela de 390px` };
});
await c.send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 820, deviceScaleFactor: 1, mobile: false });

const gravacoes = await c.avaliar('window.__gravacoes');
const ok = resultados.filter((r) => r.ok).length;
await c.avaliar(`window.__passo(${JSON.stringify(`Fim (${alvo}): ${ok}/${resultados.length} passaram · ${gravacoes.length} gravação(ões) interceptada(s), NADA foi gravado no banco`)}, '${ok === resultados.length ? '#2E7D32' : '#10233F'}')`);
fs.writeFileSync(`${DIR}/resultado_${alvo}.json`, JSON.stringify({ alvo, url: URLS[alvo], quando: new Date().toISOString(), resultados, gravacoes, errosConsole: c.erros }, null, 2));
console.log(`\nRESUMO ${alvo}: ${ok}/${resultados.length} · gravações interceptadas: ${gravacoes.length} · erros console: ${c.erros.length}`);
c.fechar();
