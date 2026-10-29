# YT Car — YouTube no Android Auto (projeto de estudo)

App experimental que toca vídeos do YouTube na central do carro pelo **Android Auto**,
usando a **Car App Library** oficial do Google de um jeito "criativo".

> ⚠️ **Só para estudo e uso pessoal.** O Google não aprova esse tipo de app na Play Store.
> O vídeo aparece na **central da frente**: use **apenas com o carro parado**.
> Ver vídeo com o carro em movimento numa tela visível ao motorista é infração no Brasil.

## Como funciona

```
Android Auto ──entrega──> Surface (a "tela do mapa" de apps de navegação)
                              │
                     VirtualDisplay privado
                              │
                         Presentation
                              │
               WebView ── player.html ── YouTube IFrame API
```

1. O app se declara como **app de navegação** (`androidx.car.app.category.NAVIGATION`).
   Só esses apps recebem do Android Auto uma `Surface` livre para desenhar.
2. O `SurfaceRenderer` cria um `VirtualDisplay` em cima dessa Surface e mostra nele uma
   `Presentation` com um `WebView`.
3. O `WebView` carrega `assets/player.html`, que usa o **player oficial do YouTube**
   (IFrame API), a forma permitida de embutir vídeos. Nada de extrair o vídeo do YouTube.
4. A barra do `NavigationTemplate` tem os controles: **Tocar/Pausar, Próximo, Buscar e Fila**.
   Toques na tela do vídeo são repassados para o WebView (Car API 5+), então os controles
   do próprio player do YouTube também funcionam.

## Estrutura

| Arquivo | O que faz |
|---|---|
| `car/YtCarAppService.kt` | Ponto de entrada que o Android Auto procura |
| `car/YtSession.kt` | Uma sessão por conexão com o carro |
| `car/PlayerScreen.kt` | Tela principal: NavigationTemplate + controles |
| `car/SearchScreen.kt` | Busca (teclado do carro, só parado) |
| `car/QueueScreen.kt` | Lista da fila |
| `player/SurfaceRenderer.kt` | Surface → VirtualDisplay → Presentation → WebView |
| `player/PlayerBridge.kt` | Ponte JavaScript ↔ Kotlin |
| `assets/player.html` | Página com a YouTube IFrame API |
| `data/VideoQueue.kt` | Fila compartilhada entre celular e carro |
| `data/YouTubeIds.kt` | Extrai o ID de links do YouTube |
| `data/YouTubeSearch.kt` | Busca pela YouTube Data API v3 (opcional) |
| `phone/MainActivity.kt` | App do celular: colar link ou "Compartilhar → YT Car" |

## Como colocar vídeos na fila

- **Pelo celular:** no app do YouTube, toque em **Compartilhar → YT Car**. Ou abra o YT Car
  e cole um link.
- **Pelo carro:** toque em **Buscar** e digite um termo (precisa da chave da API, abaixo)
  ou cole/digite um link.

### Chave da API (opcional, só para a busca)

1. No [Google Cloud Console](https://console.cloud.google.com/), crie um projeto e ative a
   **YouTube Data API v3**.
2. Crie uma **chave de API** (restrinja à YouTube Data API v3).
3. No `local.properties` na raiz do projeto (ele não vai para o git), adicione:
   ```
   YT_API_KEY=sua_chave_aqui
   ```

Sem chave, tudo funciona, menos a busca por texto.

## Rodando no Windows

### 1. Pegar o APK pronto (sem compilar)

Cada push roda o GitHub Actions (aba **Actions** do repositório). Abra a execução mais
recente e baixe o artefato **`ytcar-debug-apk`**. Instale no celular Android com
`adb install app-debug.apk` ou copiando o arquivo.

### 2. Ou compilar no Android Studio

Abra a pasta do projeto no Android Studio e rode o módulo `app`.
Pela linha de comando: `gradlew.bat assembleDebug`.

### 3. Liberar o Android Auto para apps de fora da Play Store

No celular, abra as configurações do **Android Auto**
(Android 10+: Configurações → Apps → Android Auto → Configurações adicionais no app):

1. Toque **10 vezes** em **Versão** até aparecer o aviso do modo desenvolvedor.
2. Menu **⋮ → Configurações do desenvolvedor**.
3. Ative **Fontes desconhecidas** (*Unknown sources*).

Sem isso, o YT Car não aparece na lista de apps do carro.

### 4. Testar no PC com o DHU (emulador da central)

1. No Android Studio: **SDK Manager → SDK Tools →** marque
   **Android Auto Desktop Head Unit Emulator**.
2. No celular, nas configurações de desenvolvedor do Android Auto, toque em
   **Iniciar servidor da unidade principal** (*Start head unit server*).
3. Ligue o celular no PC por USB (com depuração USB ativa) e rode:
   ```
   adb forward tcp:5277 tcp:5277
   cd %LOCALAPPDATA%\Android\Sdk\extras\google\auto
   desktop-head-unit.exe
   ```
4. No DHU, abra o menu de apps e escolha **YT Car**.

Logs úteis: `adb logcat -s YtCarRenderer YtCarSearch`

### 5. No carro

Com as **Fontes desconhecidas** ativas, ligue o celular no USB do carro e abra o YT Car no
Android Auto. A internet vem do próprio celular.

## Limitações conhecidas

- **Não é aprovável na Play Store.** Apps de navegação precisam navegar de verdade.
- **Atualizações do Android Auto** podem mudar o comportamento da Surface ou bloquear o truque.
- **Alguns vídeos não tocam** fora do YouTube (o dono desativou embeds, ou há restrição de
  idade/região). O player mostra o código de erro (101/150/152/153).
- **Identidade do app no embed (erros 152/153).** Desde julho/2025 o player embutido
  retorna o erro **153** quando a requisição não traz o `HTTP Referer` (ou identificação
  equivalente do cliente). A doc
  [Required Minimum Functionality](https://developers.google.com/youtube/terms/required-minimum-functionality)
  diz que, em app com HTML local, o `baseUrl` do `loadDataWithBaseURL` define o Referer, que ele
  deve ser `https://` + o **ID do app** registrado no sistema (para app fora de loja, o ID com
  que foi instalado) e que a `Referrer-Policy` não pode suprimi-lo (recomendado
  `strict-origin-when-cross-origin`). O app faz exatamente isso: base
  `https://dev.androidauto.ytcar/` e `<meta name="referrer">` no `player.html`. A mesma doc
  avisa que clientes com muito volume podem precisar de credenciais extras e que
  descumprir as regras pode reduzir a funcionalidade do player: isso só dá para confirmar
  no aparelho. Se aparecer **152/153**, comece por aqui (logcat `YtCarRenderer`).
- **Toque na tela do vídeo** depende da central: `SurfaceCallback.onClick` é Car API 5+ e,
  pela documentação, "pode não ser chamado em alguns carros". Os botões da barra sempre
  funcionam.
- A ponte JavaScript (`AndroidBridge`) é injetada pelo WebView em todos os frames, inclusive
  no iframe do YouTube. Ela só recebe estado/tempo/título do player (o ID é validado e o
  título só vira texto), então o risco é baixo, mas é bom saber.
- Ao abrir **Buscar** ou **Fila**, o Android Auto pode destruir a Surface. O vídeo para e
  volta do mesmo ponto quando você retorna ao player.
- A fila fica só em memória: some se o processo do app for encerrado.
- A **tela traseira** do carro não recebe o vídeo; ela só mostra o "tocando agora" que a
  central monta.

## Testes

```
gradlew.bat testDebugUnitTest
```

- **JVM puros:** extração de IDs, fila (inclusive concorrência) e parser da busca.
- **Robolectric + `androidx.car.app:app-testing`:** monta `PlayerScreen`, `QueueScreen` e
  `SearchScreen` com `TestCarContext` e confere os templates (4 ações do player, linhas da
  fila, busca com link indo para a fila, erros da busca) e o "Compartilhar → YT Car" do
  `MainActivity`. Rodam com SDK 34 no Robolectric (o SDK 35 dele pede Java 21).
- O que **não** dá para testar fora do aparelho: VirtualDisplay/Presentation de verdade e o
  player do YouTube tocando. Isso exige celular + DHU ou o carro.

O CI mostra a contagem de testes como anotação do job.
