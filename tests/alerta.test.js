// Testes da lógica web: node --test tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const A = require('../alerta.js');

test('faixas 40% / 70% com alcance de 1 m', () => {
  assert.equal(A.classificar(0.00, 1), 'Forte');
  assert.equal(A.classificar(0.39, 1), 'Forte');
  assert.equal(A.classificar(0.40, 1), 'Media');
  assert.equal(A.classificar(0.69, 1), 'Media');
  assert.equal(A.classificar(0.70, 1), 'Leve');
  assert.equal(A.classificar(0.99, 1), 'Leve');
  assert.equal(A.classificar(1.00, 1), 'Nenhuma');
});

test('faixas escalam com o alcance (1,50 m)', () => {
  assert.equal(A.classificar(0.59, 1.5), 'Forte');
  assert.equal(A.classificar(1.00, 1.5), 'Media');
  assert.equal(A.classificar(1.20, 1.5), 'Leve');
  assert.equal(A.classificar(1.60, 1.5), 'Nenhuma');
});

test('sem obstáculo (-1) ou valor inválido não alerta', () => {
  assert.equal(A.classificar(-1, 2), 'Nenhuma');
  assert.equal(A.classificar(NaN, 2), 'Nenhuma');
  assert.equal(A.classificar(undefined, 2), 'Nenhuma');
});

test('limitarAlcance respeita 0,5–2,0 m', () => {
  assert.equal(A.limitarAlcance(0.1), 0.5);
  assert.equal(A.limitarAlcance(5), 2);
  assert.equal(A.limitarAlcance('1.5'), 1.5);
  assert.equal(A.limitarAlcance(null), 1);
});

test('estaOnline usa a hora do servidor', () => {
  assert.equal(A.estaOnline(10_000, 14_000), true);
  assert.equal(A.estaOnline(10_000, 16_000), false);
  assert.equal(A.estaOnline(undefined, 16_000), false);
});

test('barra de intensidade cresce quando o obstáculo se aproxima', () => {
  assert.equal(A.intensidade(0, 1), 1);
  assert.equal(A.intensidade(0.5, 1), 0.5);
  assert.equal(A.intensidade(1.5, 1), 0);
  assert.equal(A.intensidade(-1, 1), 0);
});
