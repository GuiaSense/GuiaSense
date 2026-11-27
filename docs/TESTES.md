# Testes

A regra de alerta existe em três linguagens; cada uma tem os **mesmos casos de teste**
(limites de 40 % e 70 %, escala com o alcance, "sem obstáculo", textos do contrato).

| Camada | Arquivo | Comando |
|---|---|---|
| Firmware (C++) | [firmware/test/test_alerta/test_main.cpp](../firmware/test/test_alerta/test_main.cpp) | `cd firmware && pio test -e native` |
| App (Kotlin) | [AlertaVibracaoTest.kt](../GuiaSense.app/app/src/test/java/com/guiasense/dehjp/AlertaVibracaoTest.kt) | `cd GuiaSense.app && ./gradlew testDebugUnitTest` |
| Web (JS) | [tests/alerta.test.js](../tests/alerta.test.js) | `node --test tests/` |

`pio test -e native` precisa de um `g++` no PATH (no Windows: MinGW/WinLibs em pasta **sem espaços**).

## Testes ponta a ponta do painel web (Chrome real)

[tests/e2e/teste-site.mjs](../tests/e2e/teste-site.mjs) abre um Chrome visível e executa 10–11 testes com o
**Firebase real** (login e leituras ao vivo). Toda **gravação é interceptada** antes de sair do navegador e só
registrada: o teste nunca altera o banco. Uma faixa na página mostra cada passo.

```bash
"C:/Program Files/Google/Chrome/Application/chrome.exe" --remote-debugging-port=9333 --user-data-dir=%TEMP%/chrome-teste
node tests/e2e/teste-site.mjs publicado    # https://guiasense.github.io/GuiaSense/
node tests/e2e/teste-site.mjs antigo      # site antigo (repositório pessoal)
node tests/e2e/teste-site.mjs corrigido    # index.html deste repositório
```

**Resultado em 27/11/2026**

| # | Teste | Site antigo | Corrigido |
|---|---|---|---|
| 1 | Carrega sem erros de JS | ✔ | ✔ |
| 2 | Login anônimo no Firebase | ✔ | ✔ |
| 3 | Lê o estado do óculos | ✔ | ✔ |
| 4 | "Ao vivo" só com dados chegando | ✘ diz "Ao Vivo" com 0 atualizações em 8 s | ✔ "offline" |
| 5 | Distância real ou marcada como simulada | ✘ mostra 1,00 m sem dado no banco | ✔ "1,00 m (simulado)" |
| 6 | Mesma regra do firmware | ✘ 0,45 m: site Média × óculos Forte | ✔ (com o firmware corrigido) |
| 7 | "Médio" grava onde o óculos lê | ✘ `set("configuracoes")` apaga `usuario_id` | ✔ `update("configuracoes/usuario_id")` |
| 8 | Botão "Conectar" real | ✘ só troca o texto | — (removido) |
| 9 | Wi-Fi sem expor senha | ✘ senha em texto puro em `credenciais_wifi` | ✔ portal no óculos |
| 10 | Acessibilidade (rótulos, aria-live) | ✘ 2 campos sem rótulo, 0 aria-live | ✔ |
| 11 | Celular 390 px sem rolagem lateral | ✔ | ✔ |
| | **Total** | **4/11** | **10/10** |

**Achados no banco real (só leitura):** `configuracoes` contém apenas `{alcance_maximo: 1.02}`; o nó
`configuracoes/usuario_id`, que o óculos lê, **não existe mais** (apagado pelo `set()` do site publicado), então o
óculos usa sempre 1,00 m. `estado_dispositivo` só tem `conectado: true`, sem distância nem horário.

**O site publicado não é o código deste repositório:** é uma versão mais nova que existe só em
`edineysantos/GuiaSense_N.Ediney` (site antigo; agora o oficial é https://guiasense.github.io/GuiaSense/) e lê `configuracoes/alcance_maximo`, um caminho que nem o óculos nem o app usam.

## Testes de interface (Android, Espresso)

| Arquivo | O que verifica |
|---|---|
| [MainActivityUiTest.kt](../GuiaSense.app/app/src/androidTest/java/com/guiasense/dehjp/MainActivityUiTest.kt) | Modos Perto/Médio, SeekBar (limites 0,50–2,00 m), textos das faixas, estado online/offline/sem obstáculo, botão que inicia e para o serviço de alertas |
| [AcessibilidadeTest.kt](../GuiaSense.app/app/src/androidTest/java/com/guiasense/dehjp/AcessibilidadeTest.kt) | Accessibility Test Framework do Google: contraste, tamanho de toque, rótulos para o TalkBack |

**Resultado (27/11/2026, emulador Android 11):** 12/12 ✔. Na primeira execução encontraram duas falhas reais (#24 e #25 em [FALHAS-CORRIGIDAS.md](FALHAS-CORRIGIDAS.md)).

Os testes rodam com `AppConfig.firebaseHabilitado = false`: **não gravam no banco real** (senão
mudariam o alcance do óculos). O estado do óculos é simulado com `MainActivity.aplicarEstado()`.

```bash
cd GuiaSense.app
./gradlew connectedDebugAndroidTest      # com um emulador ou celular conectado
```

**PC com pouca RAM (8 GB):** AVD `GuiaSense_API30_1GB` (Android 11, Google APIs x86_64, `hw.ramSize=1024`). O emulador impõe no mínimo 2 GB para essa imagem, mas aloca aos poucos. No Windows/AMD é preciso o *Android Emulator Hypervisor Driver* (AEHD).
Para economizar memória, compile antes (`./gradlew assembleDebug assembleDebugAndroidTest`),
feche o Android Studio/Gradle e rode só pelo adb:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.guiasense.dehjp.test/androidx.test.runner.AndroidJUnitRunner
```

## Compilação

| Alvo | Comando |
|---|---|
| ESP32-C3 real | `cd firmware && pio run -e esp32c3` |
| Simulação Wokwi | `cd firmware && pio run -e wokwi` |
| APK debug | `cd GuiaSense.app && ./gradlew assembleDebug` |

## Roteiro de teste manual (integração)

| # | Passo | Esperado |
|---|---|---|
| 1 | Ligar o óculos (ou a simulação Wokwi) | Serial: `Firebase: ok`; web mostra "Óculos conectado (ao vivo)" |
| 2 | No app, tocar em **Iniciar alertas no relógio** | Notificação fixa "GuiaSense monitorando" |
| 3 | Aproximar a mão a ~0,8 m (alcance 1 m) | Relógio: 1 pulso (Leve) |
| 4 | Aproximar a ~0,5 m | 2 pulsos (Média), repete a cada 3 s |
| 5 | Aproximar a ~0,2 m | 3 pulsos longos (Forte) |
| 6 | Afastar tudo | Notificação de alerta some |
| 7 | Mudar para **Médio (1,50 m)** na web | Serial: `Alcance alterado: 1.00 m -> 1.50 m`; app atualiza sozinho |
| 8 | Desligar o óculos | Em ~5 s web/app mostram "offline"; nenhum alerta com dado velho |
| 9 | Ligar o óculos longe do Wi-Fi salvo | Aparece a rede `GuiaSense-Setup`; após 3 min reinicia |
