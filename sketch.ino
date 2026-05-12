#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Wire.h>
#include <VL53L0X.h> // <-- Mudamos para a biblioteca da Pololu
#include <WebServer.h>
#include <Preferences.h>

// Helpers do Firebase
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// Configurações Firebase
// Atenção: valores sensíveis removidos para publicação pública.
// Substitua por suas credenciais localmente antes de compilar ou
// use um mecanismo seguro (Preferences / NVS) para fornecer em tempo de execução.
#define DATABASE_URL "guiasense-default-rtdb.firebaseio.com"
#define DATABASE_SECRET "<SEU_DATABASE_SECRET_AQUI>"

// Configurações WiFi
// Atenção: remova hardcodes de SSID/senha em repositórios públicos.
// Recomendamos configurar via portal do dispositivo (Preferences) ou
// preenchendo estas constantes localmente antes do upload.
#define WIFI_SSID "<SEU_SSID_AQUI>"
#define WIFI_PASSWORD "<SUA_SENHA_WIFI_AQUI>"

// Pinos I2C e Hardware para ESP32-C3 Super Mini
#define I2C_SDA 8
#define I2C_SCL 9
#define MOTOR_PIN 10 

// Objetos
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
VL53L0X sensor; // <-- Objeto do sensor Pololu
Preferences prefs;
WebServer server(80);

// Estado do portal de configuração
bool configMode = false;
String stored_ssid = "";
String stored_pass = "";

// Variáveis do Sistema
float alcanceMaximo_m = 1.00; 
unsigned long tempoUltimaSincronizacao = 0;
const int INTERVALO_SINC = 1000; 

void setup() {
  Serial.begin(115200);
  pinMode(MOTOR_PIN, OUTPUT);

  // 1. Inicializa I2C
  Wire.begin(I2C_SDA, I2C_SCL);

  // 2. Inicializa o Sensor Laser
  Serial.println("Iniciando Sensor VL53L0X...");
  sensor.setTimeout(500);
  if (!sensor.init()) {
    Serial.println("Erro: Falha ao iniciar o VL53L0X!");
    while(1); 
  }
  Serial.println("VL53L0X iniciado com sucesso!");

  // 3. Conecta WiFi
  Serial.println("Verificando credenciais WiFi salvas...");
  prefs.begin("wifi", false);
  stored_ssid = prefs.getString("ssid", "");
  stored_pass = prefs.getString("pass", "");

  if (stored_ssid.length() > 0) {
    Serial.printf("Tentando conectar a SSID: %s\n", stored_ssid.c_str());
    WiFi.begin(stored_ssid.c_str(), stored_pass.c_str());
    unsigned long start = millis();
    const unsigned long TIMEOUT = 10000; // 10s
    while (WiFi.status() != WL_CONNECTED && millis() - start < TIMEOUT) {
      delay(300);
      Serial.print(".");
    }
    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\nWiFi Conectado com credenciais salvas!");
    } else {
      Serial.println("\nFalha ao conectar com credenciais salvas.");
      stored_ssid = ""; // forçar portal de configuração
    }
  }

  if (stored_ssid.length() == 0) {
    // Inicia Access Point para configurar WiFi
    Serial.println("Iniciando portal de configuração WiFi (AP)...");
    String apName = "GuiaSense-Setup";
    WiFi.softAP(apName.c_str());
    IPAddress ip = WiFi.softAPIP();
    Serial.printf("AP criado: %s -> %s\n", apName.c_str(), ip.toString().c_str());
    configMode = true;

    // Handlers simples
    server.on("/", HTTP_GET, []() {
      String page = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">";
      page += "<title>Configurar WiFi</title></head><body style=\"font-family:Arial,sans-serif;padding:16px\">";
      page += "<h2>Configurar WiFi</h2>";
      page += "<form method=\"POST\" action=\"/save\">";
      page += "SSID:<br><input name=\"ssid\" maxlength=32><br>Password:<br><input name=\"pass\" type=\"password\"><br><br>";
      page += "<input type=\"submit\" value=\"Salvar\">";
      page += "</form><p>Após salvar, o dispositivo reiniciará e tentará conectar.</p></body></html>";
      server.send(200, "text/html", page);
    });

    server.on("/save", HTTP_POST, []() {
      String ssid = server.arg("ssid");
      String pass = server.arg("pass");
      if (ssid.length() > 0) {
        prefs.putString("ssid", ssid);
        prefs.putString("pass", pass);
        String resp = "<html><body><h3>Salvo! Reiniciando...</h3></body></html>";
        server.send(200, "text/html", resp);
        delay(1000);
        ESP.restart();
      } else {
        server.send(400, "text/plain", "SSID inválido");
      }
    });

    server.begin();
  }

  // 4. Configuração Firebase
  config.database_url = DATABASE_URL;
  config.signer.tokens.legacy_token = DATABASE_SECRET;
  fbdo.setBSSLBufferSize(4096, 1024);
  
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  if(Firebase.RTDB.setBool(&fbdo, "estado_dispositivo/conectado", true)) {
    Serial.println("Registrado no Firebase!");
  }
}

void loop() {
  // Se estivermos no modo de configuração, atendemos o servidor web
  if (configMode) {
    server.handleClient();
    delay(1);
    return; // não executa leituras enquanto configurando
  }
  // --- 1. LEITURA DO SENSOR LASER ---
  // A Pololu lê direto em milímetros
  uint16_t dist_mm = sensor.readRangeSingleMillimeters();
  float distancia_m = 999.0; // Distância "segura" (infinito)

  // Se não houve erro (timeout) e a distância for menor que 8000mm (8m)
  // A Pololu retorna valores em torno de 8190 quando não há objeto na frente.
  if (!sensor.timeoutOccurred() && dist_mm < 8000) {
    distancia_m = dist_mm / 1000.0;
  }

  // --- 2. LÓGICA DE VIBRAÇÃO ---
  int intensidadeVibracao = 0;
  String nivelVibracao = "Nenhuma";

  float zonaForte = alcanceMaximo_m * 0.5;
  float zonaMedia = alcanceMaximo_m * 0.75;

  if (distancia_m < zonaForte) {
    intensidadeVibracao = 255;
    nivelVibracao = "Forte";
  } else if (distancia_m < zonaMedia) {
    intensidadeVibracao = 150;
    nivelVibracao = "Media";
  } else if (distancia_m < alcanceMaximo_m) {
    intensidadeVibracao = 80;
    nivelVibracao = "Leve";
  }

  analogWrite(MOTOR_PIN, intensidadeVibracao);

  // --- 3. SINCRONIZAÇÃO COM FIREBASE ---
  if (millis() - tempoUltimaSincronizacao > INTERVALO_SINC) {
    tempoUltimaSincronizacao = millis();

    // Ler Alcance Máximo do App
    if (Firebase.RTDB.getDouble(&fbdo, "configuracoes/usuario_id/alcance_maximo")) {
      if (fbdo.dataType() == "double" || fbdo.dataType() == "float") {
          alcanceMaximo_m = fbdo.to<double>();
      }
    }

    // Enviar dados para o banco
    Firebase.RTDB.setDouble(&fbdo, "estado_dispositivo/ultima_distancia", distancia_m);
    Firebase.RTDB.setString(&fbdo, "estado_dispositivo/vibracao_atual", nivelVibracao);
    
    Serial.printf("Dist: %.2fm | Alcance Max: %.2fm | Motor: %s\n", distancia_m, alcanceMaximo_m, nivelVibracao.c_str());
  }
}