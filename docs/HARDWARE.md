# Hardware e simulação

## Lista de materiais

| Item | Observação |
|---|---|
| ESP32-C3 Super Mini | Wi-Fi 2,4 GHz, USB-C nativo |
| VL53L0X (módulo GY-VL53L0XV2) | Sensor ToF laser 940 nm, I2C, até ~2 m em modo longo alcance |
| Bateria Li-Po 3,7 V + módulo de carga (TP4056) | Alimentação portátil |
| Armação de óculos | Sensor apontado para frente, na altura da ponte do nariz |

## Ligações

| VL53L0X | ESP32-C3 Super Mini |
|---|---|
| VIN | 3V3 |
| GND | GND |
| SDA | GPIO 8 |
| SCL | GPIO 9 |

> GPIO 9 também é o pino de BOOT do C3. Os resistores de pull-up do módulo mantêm o
> pino em nível alto e o boot é normal — não ligue nada que puxe o GPIO 9 para GND.

## Primeiro uso (Wi-Fi)

1. Ligue o óculos. Sem rede conhecida, ele cria o Wi-Fi **GuiaSense-Setup**.
2. Conecte o celular nessa rede e abra `http://192.168.4.1`.
3. Informe SSID e senha (2,4 GHz). Ele reinicia e conecta.
4. Se a rede salva ficar indisponível, o portal abre e, após 3 min, o óculos reinicia e tenta de novo.

Opcional: defina `WIFI_SSID_PADRAO` em `secrets.h` para uma rede usada quando nada foi salvo.

## Log serial esperado

```
=== GuiaSense ===
[Sensor] VL53L0X iniciado (modo longo alcance).
[WiFi] Conectando a "MinhaRede".... conectado! IP: 192.168.0.42
[Firebase] Autenticado anonimamente.
Dist: 0.83m | Alcance: 1.00m | Alerta: Leve | Firebase: ok
Dist: 0.35m | Alcance: 1.00m | Alerta: Forte | Firebase: ok
```

## Simulação no Wokwi

O Wokwi não tem o VL53L0X, então o ambiente `wokwi` troca o sensor por um
**potenciômetro no GPIO 0** (0 → 2,5 m; perto do máximo = sem obstáculo). O Wi-Fi
virtual `Wokwi-GUEST` tem internet: a simulação **grava no Firebase de verdade**, então
o app e a web reagem como se fosse o óculos real — bom para demonstrar na banca.

```bash
cd firmware
pio run -e wokwi
```

Depois, **uma** das opções:

- **VS Code**: instale a extensão *Wokwi Simulator*, rode `Wokwi: Request a New License`
  (gratuita) e abra `firmware/diagram.json` → *Start Simulation*.
- **CLI**: gere um token em https://wokwi.com/dashboard/ci e rode
  ```powershell
  $env:WOKWI_CLI_TOKEN = "seu_token"
  wokwi-cli firmware --timeout 60000
  ```
