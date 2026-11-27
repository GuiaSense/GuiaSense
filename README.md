# GuiaSense — Óculos Sensorial

Tecnologia assistiva para pessoas com deficiência visual. Um óculos com sensor
laser (VL53L0X) mede a distância até obstáculos à frente do usuário e envia o
estado ao Firebase. O app Android recebe o alerta e gera uma notificação com
vibração; o smartwatch pareado ao celular espelha a notificação e **vibra no
pulso**, com padrões diferentes para obstáculos próximos, médios e distantes.

> TCC — Técnico em Desenvolvimento de Sistemas · SENAI

```
 ┌──────────────┐   Wi-Fi/HTTPS   ┌────────────────────┐   tempo real   ┌──────────────┐  Bluetooth  ┌──────────┐
 │ Óculos       │ ──────────────▶ │ Firebase Realtime  │ ─────────────▶ │ App Android  │ ──────────▶ │ Relógio  │
 │ ESP32-C3     │ ◀────────────── │ Database + Auth    │ ◀───────────── │ (notificação)│  espelho    │ (vibra)  │
 │ + VL53L0X    │  alcance máx.   └────────────────────┘  configura     └──────────────┘             └──────────┘
 └──────────────┘                          ▲
                                           │  acompanha e configura
                                    ┌──────┴───────┐
                                    │ Painel Web   │
                                    └──────────────┘
```

## Estrutura

| Pasta / arquivo | O que é |
|---|---|
| [firmware/](firmware/) | Firmware do óculos (PlatformIO **e** Arduino IDE), simulação Wokwi e testes |
| [GuiaSense.app/](GuiaSense.app/) | App Android (Kotlin) — monitoramento, alertas e configuração |
| [index.html](index.html), [script.js](script.js), [alerta.js](alerta.js), [styles.css](styles.css) | Painel web (GitHub Pages) |
| [database.rules.json](database.rules.json) | Regras de segurança do Realtime Database |
| [tests/](tests/) | Testes da lógica web (Node) |
| [docs/](docs/) | Arquitetura, hardware, testes e relatório de falhas corrigidas |

## Regra de alerta (igual nas três camadas)

| Distância (fração do alcance configurado) | Alerta | Vibração no relógio |
|---|---|---|
| menos de 40 % | **Forte** 🔴 | 3 pulsos longos, repete a cada 3 s |
| de 40 % a 70 % | **Média** 🟠 | 2 pulsos, repete a cada 3 s |
| de 70 % a 100 % | **Leve** 🟡 | 1 pulso curto |
| acima do alcance / sem leitura | Nenhuma ⚪ | — |

Alcance ajustável de 0,50 m a 2,00 m (modos rápidos: Perto 1,00 m e Médio 1,50 m).
A lógica está em [alerta.h](firmware/GuiaSense/alerta.h), [AlertaVibracao.kt](GuiaSense.app/app/src/main/java/com/guiasense/dehjp/AlertaVibracao.kt) e [alerta.js](alerta.js), cada uma com os mesmos casos de teste.

## Como rodar

### Firmware
```bash
cd firmware
cp GuiaSense/secrets.example.h GuiaSense/secrets.h   # e preencha
pio run -e esp32c3 -t upload        # grava na placa
pio device monitor                  # log serial
pio test -e native                  # testes da lógica (precisa de g++ no PATH)
```
No Arduino IDE: abra `firmware/GuiaSense/GuiaSense.ino` e instale as bibliotecas
*Firebase Arduino Client Library for ESP8266 and ESP32* (Mobizt) e *VL53L0X* (Pololu).
Detalhes de ligação e simulação: [docs/HARDWARE.md](docs/HARDWARE.md).

### App Android
Abra `GuiaSense.app/` no Android Studio e rode. Pela linha de comando:
```bash
cd GuiaSense.app
./gradlew testDebugUnitTest assembleDebug
```
No app, toque em **Iniciar alertas no relógio** e permita notificações.

### Painel web
Abra `index.html` no navegador, ou acesse a versão publicada:
https://guiasense.github.io/GuiaSense/ (GitHub Pages deste repositório, publicado a partir da `main`) · Testes: `node --test tests/`

### Firebase
1. Em *Authentication → Sign-in method*, habilite **Anônimo**.
2. Publique as regras de [database.rules.json](database.rules.json) em *Realtime Database → Regras*.

## Documentação
- [Arquitetura e contrato de dados](docs/ARQUITETURA.md)
- [Hardware, ligações e simulação Wokwi](docs/HARDWARE.md)
- [Testes](docs/TESTES.md)
- [Falhas encontradas e corrigidas](docs/FALHAS-CORRIGIDAS.md)
- Slides da pré-banca: fonte em [docs/slides/](docs/slides/)
