# Falhas encontradas e corrigidas

Análise feita em 27/11/2026 sobre o commit `a2877ec` (falhas 24–25 encontradas depois, pelos testes de interface). Severidade: 🔴 crítica (quebra a
função principal) · 🟠 alta · 🟡 média · ⚪ baixa.

## Integração entre as camadas

| # | Sev. | Falha | Efeito | Correção |
|---|---|---|---|---|
| 1 | 🔴 | A web gravava em `configuracoes` com `set()`; o óculos lê `configuracoes/usuario_id/alcance_maximo` | Alcance definido pela web **nunca chegava** ao óculos e ainda **apagava** a configuração salva pelo app | Web passou a usar `update()` em `configuracoes/usuario_id` |
| 2 | 🔴 | Firmware só aceitava o alcance se o tipo fosse `double`/`float`; o Firebase grava `1.0` como inteiro `1` | Modos **Perto (1,00 m)** e **2,00 m** eram ignorados pelo óculos | `getFloat` aceita int/float/double; valor limitado a 0,5–2,0 m |
| 3 | 🔴 | O app Android não lia nada do Firebase (`simulateSensorReading` nunca era chamada) e não gerava notificação | O app não mostrava o sensor nem tinha como vibrar o relógio | `MonitorService` (serviço em 1º plano) + notificações com vibração por nível; tela lê o estado ao vivo |
| 4 | 🟠 | Regras de vibração diferentes: firmware 50 %/75 %, app/web 40 %/70 % e faixas fixas por modo | A tela dizia uma coisa e o óculos fazia outra | Regra única 40 %/70 % em `alerta.h`, `AlertaVibracao.kt` e `alerta.js`, com os mesmos testes |
| 5 | 🟠 | `conectado: true` nunca voltava a `false` | Web mostrava "Conectado" com o óculos desligado | Firmware grava `ultima_atualizacao` (hora do servidor); app/web consideram offline após 5 s sem sinal |
| 6 | 🟡 | Sem obstáculo o firmware enviava `999`, e a web "adivinhava" cm × m (`> 20 → ÷100`) | Exibia 9,99 m | Valor `-1` = sem obstáculo, exibido como "Livre" |

## Firmware (ESP32-C3)

| # | Sev. | Falha | Correção |
|---|---|---|---|
| 7 | 🟠 | Estado enviado só 1×/s, em 2 requisições separadas | Envio **imediato quando o nível muda** (mín. 150 ms) + heartbeat 1 s, numa única requisição atômica (`updateNodeSilent`) |
| 8 | 🟠 | VL53L0X no modo padrão (~1,2 m), mas o app permite até 2,00 m | Modo longo alcance (exemplo oficial Pololu) + leitura contínua |
| 9 | 🟠 | Sem Wi-Fi o código seguia para o Firebase mesmo no portal de configuração | Firebase só inicia com Wi-Fi conectado |
| 10 | 🟡 | Se o roteador caísse no boot, ficava no portal para sempre | Portal reinicia após 3 min quando há rede salva |
| 11 | 🟡 | Falha no sensor → `while(1);` (trava sem aviso) | 5 tentativas, mensagem de diagnóstico e reinício |
| 12 | 🟡 | Senha do Wi-Fi e chave do Firebase escritas no código público | Movidas para `secrets.h` (fora do Git); exemplo em `secrets.example.h` |
| 13 | ⚪ | Login anônimo sem esperar o token (escritas iniciais falhavam) | `tokenStatusCallback` + envio só quando `Firebase.ready()` |

## App Android

| # | Sev. | Falha | Correção |
|---|---|---|---|
| 14 | 🟠 | App sem login no Firebase: com regras seguras, toda escrita falharia | Login anônimo (`firebase-auth`) |
| 15 | 🟡 | Texto com `&lt;` literal na tela ("• &lt; 0,60m") | Textos gerados em `AlertaVibracao.textoFaixas` |
| 16 | 🟡 | Escolher "Perto/Médio" mudava o SeekBar, que chamava `clearCheck()` e desmarcava o modo (e mostrava "Modo Médio selecionado" errado) | SeekBar só reage a toque do usuário (`fromUser`) |
| 17 | 🟡 | SeekBar começava em 0 (alcance 0 m → divisão por zero na barra) | `android:min="50"` + `limitarAlcance` |
| 18 | ⚪ | App sempre abria em 1,00 m, ignorando o valor salvo | Lê e acompanha `configuracoes/usuario_id/alcance_maximo` |
| 19 | ⚪ | Mensagens de feedback sumiam antes do tempo (timers acumulados) | Timer único |
| 24 | 🟠 | Os RadioButtons ficavam dentro de cartões, fora do controle do `RadioGroup`: tocar em **Médio** marcava o botão mas não mudava o alcance, e os dois modos ficavam marcados (achado pelos testes de interface) | Toque tratado em cada botão; exclusão mútua no código |
| 25 | 🟠 | Acessibilidade: o controle de alcance não tinha rótulo; o TalkBack lia só "barra de busca, 33%" (achado pelo Accessibility Test Framework) | Rótulo "Alcance máximo de detecção" e valor falado em metros |

## Web

| # | Sev. | Falha | Correção |
|---|---|---|---|
| 20 | 🟠 | Cartão "Conectar via Wi-Fi" gravava **SSID e senha em texto puro** no banco (`credenciais_wifi`), que o óculos nem lia | Removido; substituído pelas instruções do portal `GuiaSense-Setup` |
| 21 | 🟡 | Botão "Conectar ao Óculos" só trocava o texto (falso) | Indicador real de conexão (online/offline/último sinal) |
| 22 | ⚪ | Barra crescia com a distância (cheia = longe) | Cheia = perto, com cor por nível |
| 23 | ⚪ | Acessibilidade: sem `aria-live`, foco, `label` | Leitores de tela anunciam status e intensidade |

## Achados pelos testes ponta a ponta do site (27/11/2026)

| # | Sev. | Falha | Correção |
|---|---|---|---|
| 26 | 🟡 | Com o óculos offline, a distância do controle de simulação aparecia como leitura real | Marcada como "(simulado)" |
| 27 | 🟠 | O site publicado (GitHub Pages) roda um código diferente deste repositório e lê `configuracoes/alcance_maximo`, caminho que ninguém mais usa | Publicar a versão corrigida (ver pendências) |

## Segurança e repositório

- **Regras do banco**: criado [database.rules.json](../database.rules.json) (só usuários autenticados; valida tipos e faixas; bloqueia o resto).
- `.idea/` saiu do Git; `.pio/` e `secrets.h` no `.gitignore`.

## Ações manuais pendentes (precisam de acesso ao console Firebase)

0. O site passou a ser publicado pelo GitHub Pages **deste repositório** (https://guiasense.github.io/GuiaSense/). **Desativar o site antigo** (`edineysantos/GuiaSense_N.Ediney`), que continua apagando a configuração a cada clique, e **recriar `configuracoes/usuario_id/alcance_maximo`** (basta salvar o alcance uma vez pelo app ou pelo site novo). Hoje esse nó não existe e o óculos fica preso em 1,00 m.
1. **Apagar o nó `credenciais_wifi`** do Realtime Database — pode conter senha real em texto puro.
2. Publicar as regras de `database.rules.json`.
3. A senha do hotspot continua no **histórico do Git** (commits antigos). Troque a senha do hotspot.
4. (Opcional) restringir a API key no Google Cloud Console aos apps/domínios do projeto.
