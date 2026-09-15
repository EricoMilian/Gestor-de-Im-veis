# Gestão de Imóveis — acesso biométrico nativo

Esta versão é um **projeto Android nativo** que abre o aplicativo web dentro de um WebView e integra o botão de acesso diretamente ao `BiometricPrompt` do AndroidX.

## Fluxo desejado
- **Biometria / Reconhecimento Facial:** independente do Google/Firebase.
- Ao tocar no botão, o Android mostra o diálogo biométrico do sistema.
- Só uma biometria que o Android aceite como cadastrada no aparelho libera o aplicativo.
- A digital/rosto nunca é enviado ao JavaScript nem armazenado pelo aplicativo.
- O Firebase/Google continua como método separado.

## Proteção contra alteração de biometria
A chave do Android Keystore usada pelo aplicativo é invalidada quando novas biometrias são cadastradas no aparelho. Isso evita manter automaticamente um vínculo antigo depois que o conjunto de digitais/rostos foi alterado.

## Observação
O código-fonte está pronto para ser compilado em Android Studio/Gradle. Este ambiente de chat não possui Android SDK/Gradle instalado para gerar e assinar um APK final aqui.
