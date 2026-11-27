// Testes da lógica de alerta (rodam no PC): pio test -e native
#include <unity.h>
#include "alerta.h"

using namespace guiasense;

void setUp() {}
void tearDown() {}

void test_faixas_com_alcance_1m() {
  TEST_ASSERT_EQUAL(Nivel::Forte, classificar(0.00f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Forte, classificar(0.39f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Media, classificar(0.40f, 1.00f));  // limite: 40% já é Média
  TEST_ASSERT_EQUAL(Nivel::Media, classificar(0.69f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Leve, classificar(0.70f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Leve, classificar(0.99f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Nenhuma, classificar(1.00f, 1.00f));
  TEST_ASSERT_EQUAL(Nivel::Nenhuma, classificar(1.80f, 1.00f));
}

void test_faixas_escalam_com_alcance() {
  // Alcance 1,50 m -> Forte < 0,60 | Média < 1,05 | Leve < 1,50
  TEST_ASSERT_EQUAL(Nivel::Forte, classificar(0.59f, 1.50f));
  TEST_ASSERT_EQUAL(Nivel::Media, classificar(1.00f, 1.50f));
  TEST_ASSERT_EQUAL(Nivel::Leve, classificar(1.20f, 1.50f));
  TEST_ASSERT_EQUAL(Nivel::Nenhuma, classificar(1.60f, 1.50f));
}

void test_sem_obstaculo_nunca_alerta() {
  TEST_ASSERT_EQUAL(Nivel::Nenhuma, classificar(SEM_OBSTACULO, 2.00f));
  TEST_ASSERT_EQUAL(Nivel::Nenhuma, classificar(0.10f, 0.0f));
}

void test_leitura_do_sensor() {
  TEST_ASSERT_EQUAL_FLOAT(0.5f, leituraParaMetros(500, false));
  TEST_ASSERT_EQUAL_FLOAT(SEM_OBSTACULO, leituraParaMetros(500, true));    // timeout
  TEST_ASSERT_EQUAL_FLOAT(SEM_OBSTACULO, leituraParaMetros(8190, false));  // sem alvo
  TEST_ASSERT_EQUAL_FLOAT(SEM_OBSTACULO, leituraParaMetros(2201, false));
  TEST_ASSERT_EQUAL_FLOAT(2.2f, leituraParaMetros(2200, false));
}

void test_limitar_alcance() {
  TEST_ASSERT_EQUAL_FLOAT(1.0f, limitarAlcance(1.0f));
  TEST_ASSERT_EQUAL_FLOAT(ALCANCE_MIN_M, limitarAlcance(0.1f));
  TEST_ASSERT_EQUAL_FLOAT(ALCANCE_MAX_M, limitarAlcance(5.0f));
  TEST_ASSERT_EQUAL_FLOAT(ALCANCE_PADRAO_M, limitarAlcance(0.0f));
  TEST_ASSERT_EQUAL_FLOAT(ALCANCE_PADRAO_M, limitarAlcance(-3.0f));
}

void test_nomes_do_contrato_com_app() {
  TEST_ASSERT_EQUAL_STRING("Forte", nomeNivel(Nivel::Forte));
  TEST_ASSERT_EQUAL_STRING("Media", nomeNivel(Nivel::Media));
  TEST_ASSERT_EQUAL_STRING("Leve", nomeNivel(Nivel::Leve));
  TEST_ASSERT_EQUAL_STRING("Nenhuma", nomeNivel(Nivel::Nenhuma));
}

int main(int, char**) {
  UNITY_BEGIN();
  RUN_TEST(test_faixas_com_alcance_1m);
  RUN_TEST(test_faixas_escalam_com_alcance);
  RUN_TEST(test_sem_obstaculo_nunca_alerta);
  RUN_TEST(test_leitura_do_sensor);
  RUN_TEST(test_limitar_alcance);
  RUN_TEST(test_nomes_do_contrato_com_app);
  return UNITY_END();
}
