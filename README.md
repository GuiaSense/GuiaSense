# Óculos Sensorial — Web

Esta página replica a interface do app Android presente em `MainActivity.kt` e `FrontApp.txt`.

Como usar

- Abra `index.html` no navegador (basta abrir o arquivo) ou faça deploy no GitHub Pages.
- Interaja com os modos rápidos, ajuste o alcance com o slider e simule distâncias com o controle "Simular distância".
- O botão `Salvar Configuração` grava o valor em `localStorage` (simulação).

Deploy no GitHub Pages

1. Crie um repositório no GitHub e envie estes arquivos (ou permita que eu crie e envie para você).
2. Nas configurações do repositório → Pages, escolha a branch `main` e a raiz `/` como fonte (se eu criar o repo, eu faço isso automaticamente).
3. A página estará disponível em `https://<seu-usuario>.github.io/<seu-repo>/`.

Observações e privacidade

- Arquivos sensíveis foram removidos ou substituídos por placeholders antes do push público.
- `google-services.json` está listado em `.gitignore` e não será enviado (contém chaves do Firebase).
- Antes de compilar e gravar o firmware no seu ESP, restaure as credenciais locais em `sketch.ino` ou configure-as via o portal de configuração que implementamos.

Se quiser, posso criar o repositório público `GuiaSense_Web`, subir os arquivos sanitizados e ativar o GitHub Pages para você — envie seu GitHub username e um Personal Access Token (escopo `public_repo`) e eu executo o processo.
