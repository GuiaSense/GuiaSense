// GuiaSense — firmware do "Óculos Sensorial"
// Placa: ESP32-C3 Super Mini | Sensor: VL53L0X (ToF laser, I2C)
//
// Fluxo:
//   1. Mede a distância à frente do usuário (~30 leituras/s).
//   2. Classifica em Nenhuma / Leve / Media / Forte (regras em alerta.h).
//   3. Publica o estado no Firebase Realtime Database:
//        - imediatamente quando o nível muda (para o alerta chegar rápido);
//        - a cada 1 s como "batimento" (heartbeat), para o app saber que está online.
//   4. Lê o alcance máximo configurado pelo app/web a cada 2 s.
//   O app Android escuta esse estado e gera a notificação que vibra o relógio.
//
// Compila no Arduino IDE (abra esta pasta) ou no PlatformIO (pasta firmware/).

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <Wire.h>
#include <VL53L0X.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"

#include "alerta.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Crie GuiaSense/secrets.h copiando secrets.example.h e preenchendo suas credenciais."
#endif

using namespace guiasense;

// ---------------------------------------------------------------- Hardware
#define I2C_SDA 8
#define I2C_SCL 9   // GPIO9 também é o pino de BOOT do C3: mantenha o pull-up do I2C.

#ifdef SENSOR_SIMULADO
// No simulador Wokwi não existe VL53L0X: um potenciômetro no GPIO0 faz o papel
// do sensor (0 a 2500 mm; perto do máximo = "sem obstáculo").
#define PINO_POT_SIMULADO 0
#endif

// ---------------------------------------------------------------- Firebase
static const char* PATH_ALCANCE = "configuracoes/usuario_id/alcance_maximo";
static const char* PATH_ESTADO  = "estado_dispositivo";

// ---------------------------------------------------------------- Tempos
static const unsigned long WIFI_TIMEOUT_MS        = 15000;
static const unsigned long PORTAL_TIMEOUT_MS      = 180000;  // 3 min sem configurar -> reinicia e tenta de novo
static const unsigned long INTERVALO_HEARTBEAT_MS = 1000;
static const unsigned long INTERVALO_MIN_ENVIO_MS = 150;     // limita rajadas quando o nível oscila
static const unsigned long INTERVALO_CONFIG_MS    = 2000;

// ---------------------------------------------------------------- Objetos
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig fbConfig;
VL53L0X sensor;
Preferences prefs;
WebServer server(80);

// ---------------------------------------------------------------- Estado
bool modoPortal = false;
bool temCredenciaisSalvas = false;
unsigned long inicioPortal = 0;

float alcanceMaximo_m = ALCANCE_PADRAO_M;
Nivel ultimoNivelEnviado = Nivel::Nenhuma;
bool jaEnviouAlgo = false;
unsigned long ultimoEnvio = 0;
unsigned long ultimaLeituraConfig = 0;
unsigned long ultimoLog = 0;

// =================================================================== Sensor
void iniciarSensor() {
#ifdef SENSOR_SIMULADO
  pinMode(PINO_POT_SIMULADO, INPUT);
  Serial.println("[Sensor] Modo SIMULADO (potenciometro no GPIO0).");
#else
  Wire.begin(I2C_SDA, I2C_SCL);
  sensor.setTimeout(500);

  // Tenta algumas vezes antes de desistir (mau contato, sensor ligando).
  for (int tentativa = 1; tentativa <= 5; tentativa++) {
    if (sensor.init()) {
      // Modo "longo alcance" (exemplo oficial da Pololu): necessário para
      // alcances perto de 2 m. No modo padrão o VL53L0X vai só até ~1,2 m.
      sensor.setSignalRateLimit(0.1);
      sensor.setVcselPulsePeriod(VL53L0X::VcselPeriodPreRange, 18);
      sensor.setVcselPulsePeriod(VL53L0X::VcselPeriodFinalRange, 14);
      sensor.startContinuous();
      Serial.println("[Sensor] VL53L0X iniciado (modo longo alcance).");
      return;
    }
    Serial.printf("[Sensor] Falha ao iniciar VL53L0X (tentativa %d/5)\n", tentativa);
    delay(500);
  }
  // Sem sensor o óculos não tem utilidade: reinicia em vez de travar para sempre.
  Serial.println("[Sensor] ERRO: VL53L0X nao respondeu. Verifique SDA=8, SCL=9, 3V3, GND. Reiniciando...");
  delay(2000);
  ESP.restart();
#endif
}

float lerDistancia_m() {
#ifdef SENSOR_SIMULADO
  uint16_t mm = map(analogRead(PINO_POT_SIMULADO), 0, 4095, 0, 2500);
  return leituraParaMetros(mm, false);
#else
  uint16_t mm = sensor.readRangeContinuousMillimeters();
  return leituraParaMetros(mm, sensor.timeoutOccurred());
#endif
}

// =================================================================== Wi-Fi
bool conectarWiFi(const String& ssid, const String& senha) {
  Serial.printf("[WiFi] Conectando a \"%s\"", ssid.c_str());
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid.c_str(), senha.c_str());
  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < WIFI_TIMEOUT_MS) {
    delay(300);
    Serial.print(".");
  }
  bool ok = WiFi.status() == WL_CONNECTED;
  Serial.println(ok ? " conectado! IP: " + WiFi.localIP().toString() : " falhou.");
  return ok;
}

void iniciarPortal() {
  modoPortal = true;
  inicioPortal = millis();
  WiFi.mode(WIFI_AP);
  WiFi.softAP("GuiaSense-Setup");
  Serial.printf("[Portal] Conecte-se a rede \"GuiaSense-Setup\" e abra http://%s\n",
                WiFi.softAPIP().toString().c_str());

  server.on("/", HTTP_GET, []() {
    String page = F("<!doctype html><html><head><meta charset=\"utf-8\">"
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    "<title>GuiaSense - Wi-Fi</title></head>"
                    "<body style=\"font-family:Arial,sans-serif;padding:16px;max-width:420px\">"
                    "<h2>Configurar Wi-Fi do GuiaSense</h2>"
                    "<form method=\"POST\" action=\"/save\">"
                    "<label>Rede (SSID)<br><input name=\"ssid\" maxlength=\"32\" required></label><br><br>"
                    "<label>Senha<br><input name=\"pass\" type=\"password\" maxlength=\"64\"></label><br><br>"
                    "<input type=\"submit\" value=\"Salvar e reiniciar\">"
                    "</form><p>Use uma rede 2,4 GHz. O dispositivo reinicia ap&oacute;s salvar.</p>"
                    "</body></html>");
    server.send(200, "text/html; charset=utf-8", page);
  });

  server.on("/save", HTTP_POST, []() {
    String ssid = server.arg("ssid");
    String pass = server.arg("pass");
    ssid.trim();
    if (ssid.length() == 0 || ssid.length() > 32 || pass.length() > 64) {
      server.send(400, "text/plain; charset=utf-8", "SSID ou senha invalidos");
      return;
    }
    prefs.putString("ssid", ssid);
    prefs.putString("pass", pass);
    server.send(200, "text/html; charset=utf-8",
                "<html><body><h3>Salvo! Reiniciando...</h3></body></html>");
    delay(1000);
    ESP.restart();
  });

  server.begin();
}

// ================================================================ Firebase
void iniciarFirebase() {
  fbConfig.api_key = FIREBASE_API_KEY;
  fbConfig.database_url = FIREBASE_DATABASE_URL;
  fbConfig.token_status_callback = tokenStatusCallback;  // log do token no Serial

  // Login anônimo (precisa estar habilitado em Authentication > Sign-in method).
  if (Firebase.signUp(&fbConfig, &auth, "", "")) {
    Serial.println("[Firebase] Autenticado anonimamente.");
  } else {
    Serial.printf("[Firebase] Erro na autenticacao: %s\n",
                  fbConfig.signer.signupError.message.c_str());
  }

  fbdo.setBSSLBufferSize(4096, 1024);
  fbdo.setResponseSize(2048);
  Firebase.begin(&fbConfig, &auth);
  Firebase.reconnectWiFi(true);
}

void enviarEstado(float distancia_m, Nivel nivel) {
  FirebaseJson json;
  json.set("ultima_distancia", distancia_m);  // -1 = sem obstáculo
  json.set("vibracao_atual", nomeNivel(nivel));
  json.set("alcance_maximo", alcanceMaximo_m);
  json.set("conectado", true);
  json.set("ultima_atualizacao/.sv", "timestamp");  // hora do servidor (ms)

  // Uma única requisição atualiza todos os campos de uma vez (atômico).
  if (Firebase.RTDB.updateNodeSilent(&fbdo, PATH_ESTADO, &json)) {
    ultimoNivelEnviado = nivel;
    jaEnviouAlgo = true;
  } else {
    Serial.printf("[Firebase] Falha ao enviar estado: %s\n", fbdo.errorReason().c_str());
  }
  ultimoEnvio = millis();
}

void lerAlcanceConfigurado() {
  ultimaLeituraConfig = millis();
  // getFloat aceita int, float e double. Importante: o RTDB grava 1.0 como "1"
  // (inteiro); a versão anterior só aceitava double e ignorava 1,00 m e 2,00 m.
  if (Firebase.RTDB.getFloat(&fbdo, PATH_ALCANCE)) {
    float novo = limitarAlcance(fbdo.to<float>());
    if (fabsf(novo - alcanceMaximo_m) > 0.001f) {
      Serial.printf("[Config] Alcance alterado: %.2f m -> %.2f m\n", alcanceMaximo_m, novo);
      alcanceMaximo_m = novo;
    }
  }
}

// =================================================================== Setup
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n=== GuiaSense ===");

  iniciarSensor();

  prefs.begin("wifi", false);
  String ssid = prefs.getString("ssid", "");
  String senha = prefs.getString("pass", "");
  temCredenciaisSalvas = ssid.length() > 0;

#ifdef SENSOR_SIMULADO
  // Simulador Wokwi: rede virtual aberta com acesso à internet.
  if (!temCredenciaisSalvas) { ssid = "Wokwi-GUEST"; senha = ""; }
#endif
  if (ssid.length() == 0 && strlen(WIFI_SSID_PADRAO) > 0) {
    ssid = WIFI_SSID_PADRAO;
    senha = WIFI_SENHA_PADRAO;
  }

  if (ssid.length() > 0 && conectarWiFi(ssid, senha)) {
    iniciarFirebase();
  } else {
    iniciarPortal();  // sem Wi-Fi não adianta iniciar o Firebase
  }
}

// ==================================================================== Loop
void loop() {
  if (modoPortal) {
    server.handleClient();
    // Se havia uma rede salva (ex.: roteador desligado temporariamente),
    // reinicia depois de um tempo para tentar de novo sozinho.
    if (temCredenciaisSalvas && millis() - inicioPortal > PORTAL_TIMEOUT_MS) {
      Serial.println("[Portal] Tempo esgotado, reiniciando para tentar a rede salva...");
      ESP.restart();
    }
    delay(2);
    return;
  }

  float distancia_m = lerDistancia_m();
  Nivel nivel = classificar(distancia_m, alcanceMaximo_m);
  unsigned long agora = millis();

  if (Firebase.ready()) {
    bool mudou = !jaEnviouAlgo || nivel != ultimoNivelEnviado;
    if ((mudou && agora - ultimoEnvio >= INTERVALO_MIN_ENVIO_MS) ||
        agora - ultimoEnvio >= INTERVALO_HEARTBEAT_MS) {
      enviarEstado(distancia_m, nivel);
    }
    if (millis() - ultimaLeituraConfig >= INTERVALO_CONFIG_MS) {
      lerAlcanceConfigurado();
    }
  }

  if (agora - ultimoLog >= 1000) {
    ultimoLog = agora;
    Serial.printf("Dist: %.2fm | Alcance: %.2fm | Alerta: %s | Firebase: %s\n",
                  distancia_m, alcanceMaximo_m, nomeNivel(nivel),
                  Firebase.ready() ? "ok" : "aguardando");
  }

#ifdef SENSOR_SIMULADO
  delay(30);  // o sensor real já leva ~33 ms por medição
#endif
}
