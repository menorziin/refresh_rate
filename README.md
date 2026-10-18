<p align="center">
  <b>Marble Hz</b><br>
  Troque a taxa de atualização do Poco F5 entre 60, 90 e 120 Hz com um toque.
</p>

---

O **Marble Hz** é um app Android para quem tem root e quer controlar a taxa de atualização da tela sem depender das opções limitadas da HyperOS. Foi feito para o **Poco F5** (codinome *marble*), mas lê as taxas que o próprio painel suporta, então tende a funcionar em outros aparelhos.

## Recursos

- **Taxas fixas**: 60, 90 ou 120 Hz, conforme o que o painel suporta.
- **Modo dinâmico (60–120 Hz)**: o sistema varia a taxa conforme o uso.
- **Bloco nas Configurações Rápidas**: mostra a taxa ativa e alterna a cada toque, sem abrir o app.
- **Reaplicar ao reiniciar**: restaura a última escolha depois do boot.
- **Forçar via SurfaceFlinger** (experimental): trava o modo direto no compositor quando a HyperOS insiste em voltar para 60 Hz.
- Material You, tema claro/escuro e ícone temático.

## Requisitos

- Android 12 ou superior
- Root (Magisk, KernelSU ou APatch)

## Instalação

1. Baixe o APK mais recente em [Releases](../../releases).
2. Instale e abra o app.
3. Conceda acesso root quando o gerenciador pedir.
4. (Opcional) Edite as Configurações Rápidas e adicione o bloco **Taxa de atualização**.

## Como funciona

O app executa comandos via `su` e grava as chaves de taxa usadas pelo AOSP e pela HyperOS:

```sh
settings put system min_refresh_rate  <hz>
settings put system peak_refresh_rate <hz>
settings put system user_refresh_rate <hz>   # chave da MIUI/HyperOS
```

No modo **Forçar via SurfaceFlinger**, ele também envia:

```sh
service call SurfaceFlinger 1035 i32 <índice do modo>
```

Esse comando é interno e não documentado. O efeito dura até reiniciar e só vale para taxas fixas.

## Compilar

O APK é gerado pelo GitHub Actions a cada push na `main`. Para publicar uma release, crie uma tag `v*`:

```sh
git tag v1.0 && git push origin v1.0
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
