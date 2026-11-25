# PixPronto

**Seu PIX, do seu jeito.** Um aplicativo Android nativo, offline-first e open source para criar e organizar QR Codes Pix.

> Estado atual: fundação funcional inicial (0.1.0). Leia “Escopo desta entrega” antes de distribuir ou usar em produção.

## Implementado nesta entrega

- Kotlin + Jetpack Compose + Material 3.
- Gerador local de BR Code estático (modo reutilizável, `Point of Initiation Method = 11`).
- CRC16-CCITT (polinômio `0x1021`, inicial `0xFFFF`) e validação do CRC.
- QR Code PNG em memória com ZXing, margem silenciosa e correção M.
- Valor opcional com `BigDecimal`; sem valor artificial quando deixado em branco.
- Copiar payload completo para a área de transferência.
- Banco Room local para chaves e histórico.
- Interface inicial para gerar, consultar histórico, cadastrar e excluir chaves.
- Sem permissão INTERNET e sem serviço remoto.
- Testes unitários iniciais do CRC e do payload.

## Escopo ainda pendente

Esta entrega **não** implementa ainda exportação para arquivos PNG/SVG/PDF, favoritos completos, edição/validação rigorosa de CPF/CNPJ/telefone/e-mail, backup/restauração (criptografado ou não), escolha persistente de tema, pesquisa/filtros do histórico, migrações futuras, testes instrumentados/UI, ícone de marca final ou assinatura de APK. A tela de chaves é uma base funcional e não substitui validação completa. Não use o app como comprovante de pagamento.

## Requisitos

- Android Studio compatível com AGP 8.13.2.
- JDK 17.
- Android SDK 36.
- Gradle Wrapper 8.13.2.

Abra a pasta `PixPronto` no Android Studio e aguarde a sincronização do Gradle. Para compilar:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Arquitetura

- `pix/`: construção BR Code, CRC16 e renderização QR.
- `data/`: Room entities, DAO e banco local.
- `MainActivity.kt`: navegação e telas Compose da primeira entrega.

## Privacidade e limitações

Os dados são armazenados no banco privado do aplicativo. Não há tráfego de rede no código desta entrega. O app gera o payload; não consulta o DICT, não verifica se a chave existe e não confirma liquidação. O CRC correto, isoladamente, não garante aceitação por uma instituição financeira.

## Referências técnicas

- Banco Central do Brasil, [Normas sobre o Pix](https://www.bcb.gov.br/estabilidadefinanceira/pix-normas) e Manual de Padrões para Iniciação do Pix.
- [ZXing](https://github.com/zxing/zxing).

## Licença

GNU GPL-3.0-or-later. Consulte `LICENSE`.

## Compilação no Android Studio

1. Extraia o ZIP.
2. Abra a pasta `PixPronto` (a que contém `settings.gradle.kts`).
3. Aguarde o Gradle sincronizar e instalar as dependências.
4. Selecione **Build > Make Project** ou execute `./gradlew assembleDebug`.

O script `gradlew` incluído usa Gradle 8.13.2; se não houver Gradle instalado, baixa a distribuição oficial na primeira execução. JDK 17 e Android SDK 36 continuam necessários.

Na primeira execução, o script de bootstrap baixa Gradle 8.13.2 e gera os arquivos oficiais do Gradle Wrapper a partir dessa distribuição. Depois disso, o projeto passa a usar o Wrapper padrão (`gradle/wrapper/gradle-wrapper.jar`).
