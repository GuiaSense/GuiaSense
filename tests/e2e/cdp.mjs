// Mini-driver do Chrome via DevTools Protocol (sem dependências).
// Uso: node cdp.mjs <roteiro.mjs>
const PORT = 9333;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function conectar() {
  let alvos;
  for (let i = 0; i < 30; i++) {
    try { alvos = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json(); break; }
    catch { await sleep(500); }
  }
  const pagina = alvos.find((t) => t.type === 'page');
  const ws = new WebSocket(pagina.webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0;
  const pend = new Map();
  const ouvintes = [];
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pend.has(m.id)) { const p = pend.get(m.id); pend.delete(m.id); m.error ? p.rej(new Error(m.error.message)) : p.res(m.result); }
    else ouvintes.forEach((f) => f(m));
  };
  const send = (method, params = {}) => new Promise((res, rej) => { const i = ++id; pend.set(i, { res, rej }); ws.send(JSON.stringify({ id: i, method, params })); });
  const erros = [];
  ouvintes.push((m) => {
    if (m.method === 'Runtime.exceptionThrown') erros.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') erros.push(m.params.args.map((a) => a.value ?? a.description).join(' '));
  });
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 820, deviceScaleFactor: 1, mobile: false });

  const avaliar = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
    return r.result.value;
  };
  const navegar = async (url) => {
    const carregou = new Promise((r) => ouvintes.push(function f(m) { if (m.method === 'Page.loadEventFired') { ouvintes.splice(ouvintes.indexOf(f), 1); r(); } }));
    await send('Page.navigate', { url });
    await carregou;
  };
  const foto = async (arq) => {
    const r = await send('Page.captureScreenshot', { format: 'png' });
    (await import('node:fs')).writeFileSync(arq, Buffer.from(r.data, 'base64'));
  };
  return { send, avaliar, navegar, foto, erros, sleep, fechar: () => ws.close() };
}

// Código injetado ANTES dos scripts da página: intercepta gravações no Firebase
// e mostra uma faixa com o passo atual do teste.
export const INTERCEPTADOR = `
window.__gravacoes = [];
(function () {
  const t = setInterval(() => {
    try {
      const R = firebase.database.Reference.prototype;
      if (R.__guiasense) return clearInterval(t);
      ['set', 'update', 'push', 'remove'].forEach((m) => {
        R[m] = function (v) {
          const caminho = decodeURIComponent(this.toString().replace(/^https:\\/\\/[^/]+\\/?/, '')) || '(raiz)';
          window.__gravacoes.push({ op: m, caminho, dados: v === undefined ? null : JSON.parse(JSON.stringify(v)) });
          window.__passo && window.__passo('🛑 Gravação INTERCEPTADA (não foi ao banco): ' + m + '("' + caminho + '")', '#B71C1C');
          return Promise.resolve();
        };
      });
      R.__guiasense = true; clearInterval(t);
    } catch (e) {}
  }, 5);
  window.__passo = (txt, cor) => {
    let d = document.getElementById('__faixa');
    if (!d) {
      d = document.createElement('div'); d.id = '__faixa';
      d.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;padding:14px 20px;font:600 18px system-ui;color:#fff;box-shadow:0 -2px 12px #0004';
      document.body.appendChild(d);
    }
    d.style.background = cor || '#10233F'; d.textContent = txt;
  };
  window.__destacar = (el) => {
    el.scrollIntoView({ block: 'center' });
    el.style.outline = '4px solid #F08A00'; el.style.outlineOffset = '3px';
    setTimeout(() => { el.style.outline = ''; }, 1800);
  };
})();
`;
