<p align="center">
  <b>Refresh Rate</b><br>
  Troque a taxa de atualização da tela entre 60, 90 e 120 Hz com um toque.
</p>

<p align="center">
  <a href="README.md">English</a> · <b>Português (Brasil)</b>
</p>

---

O **Refresh Rate** é um app Android para controlar a taxa de atualização da tela com um toque, usando root ou, sem root, o [Shizuku](https://shizuku.rikka.app/) (ADB). Ele lê as taxas que o próprio painel suporta, então não depende de um aparelho específico.

## Compatibilidade

| ROM | Status |
| --- | --- |
| AOSP e ROMs baseadas em AOSP | ✅ Funciona |
| HyperOS / MIUI | ⚠️ Não testado |
| Outras ROMs de fabricante (One UI, ColorOS etc.) | ⚠️ Não testado |

Testado em um Poco F5 com ROM AOSP. Em ROMs de fabricante, o sistema pode ignorar ou sobrescrever a taxa escolhida. Se testar em alguma, abra uma issue contando o resultado.

## Recursos

- **Taxas fixas**: 60, 90 ou 120 Hz, conforme o que o painel suporta.
- **Modo dinâmico**: o sistema varia entre a menor e a maior taxa conforme o uso.
- **Bloco nas Configurações Rápidas**: mostra a taxa ativa no ícone e alterna a cada toque.
- **Respeita a economia de energia**: avisa quando ela limita a taxa, no app e no bloco.
- **Reaplicar ao reiniciar**: restaura a última escolha depois do boot.
- **Forçar via SurfaceFlinger** (experimental): trava o modo direto no compositor quando o sistema insiste em voltar para outra taxa.
- Inglês e português (Brasil), com idioma por app no Android 13+.
- Material You, tema claro/escuro e ícone temático.

## Requisitos

- Android 12 ou superior
- Root (Magisk, KernelSU ou APatch) **ou** [Shizuku](https://shizuku.rikka.app/) para usar sem root

## Instalação

1. Baixe o APK mais recente em [Releases](../../releases).
2. Instale e abra o app.
3. Conceda o acesso: root, quando o gerenciador pedir, ou Shizuku, tocando em **Conceder** no app.
4. (Opcional) Edite as Configurações Rápidas e adicione o bloco **Taxa de atualização**.

## Usar sem root (Shizuku)

O Shizuku executa os comandos do app com os mesmos privilégios do `adb shell`, então o root não é necessário.

1. Instale o [Shizuku](https://shizuku.rikka.app/download/).
2. Inicie-o seguindo o [guia oficial](https://shizuku.rikka.app/guide/setup/): no Android 11+ pela **Depuração sem fio**, sem precisar de computador; ou por um computador via ADB.
3. Abra o Refresh Rate e toque em **Conceder**.

O Shizuku iniciado via ADB para quando o celular reinicia, então inicie-o de novo antes de trocar a taxa. No AOSP, a taxa escolhida continua salva pelo sistema depois de reiniciar. **Reaplicar ao reiniciar** só funciona com root ou com o Shizuku iniciado via root.

## Como funciona

O app executa comandos via `su` (root) ou Shizuku (usuário shell) e grava as chaves de taxa do AOSP:

```sh
settings put system min_refresh_rate  <hz>
settings put system peak_refresh_rate <hz>
```

Ele também grava `user_refresh_rate`, chave usada por algumas ROMs de fabricante. No AOSP ela é ignorada; está ali por compatibilidade, sem garantia de funcionar.

No modo **Forçar via SurfaceFlinger**, ele também envia:

```sh
service call SurfaceFlinger 1035 i32 <índice do modo>
```

Esse comando é interno e não documentado. O efeito dura até reiniciar e só vale para taxas fixas.

## Compilar

O GitHub Actions gera o APK a cada push na `main`. Para publicar uma release, crie uma tag `v*` (ou uma release com uma nova tag `v*` pelo GitHub):

```sh
git tag v1.1 && git push origin v1.1
```

A assinatura usa uma keystore guardada nos Secrets do repositório:

| Secret | Conteúdo |
| --- | --- |
| `KEYSTORE_BASE64` | Keystore `.jks` codificada em base64 |
| `KEYSTORE_PASSWORD` | Senha da keystore |
| `KEY_ALIAS` | Alias da chave |
| `KEY_PASSWORD` | Senha da chave |

Sem esses Secrets, builds da `main` saem com a chave de debug e builds de tag falham, para que nenhuma release seja publicada com assinatura errada.

Para compilar localmente, com o Android SDK instalado:

```sh
gradle assembleRelease
```

## Aviso

O app altera configurações do sistema com privilégios de root. Use por sua conta e risco.
