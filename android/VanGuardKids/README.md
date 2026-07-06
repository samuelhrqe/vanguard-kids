# VanGuard Kids

## 1. Arquitetura do Projeto e Descrição do Código

O aplicativo foi desenvolvido utilizando a arquitetura **MVVM (Model-View-ViewModel)**, garantindo a separação de responsabilidades entre a interface, a lógica de negócios e o consumo de dados.

A estrutura de pastas (`br.unicamp.iot.vanguardkids`) está dividida da seguinte forma:

* **`/data`**: Camada de dados responsável por buscar informações externas.
  * `/mqtt`: Contém o `MqttDataSource` (gerencia a conexão, assinatura e publicação de tópicos MQTT) e `SeatMqttModels.kt` (modelagem dos dados recebidos dos bancos).
    * `/sensor`: Contém o `MagDataSource`, responsável por lidar com a leitura de sensores/hardware.
* **`/repository`**: Contém o `VanGuardRepository.kt`. É a única fonte de verdade (*Single Source of Truth*) do app. Ele centraliza os dados vindos do MQTT e dos Sensores, entregando-os processados para a interface.
* **`/ui`**: Camada de interface do usuário.
  * `/activity`: `MainActivity` hospeda o layout principal e os fragmentos.
    * `/dialog`: `SafetyBottomSheet` exibe o alerta crítico de segurança (gaveta inferior) caso haja crianças retidas ao tentar encerrar a rota.
    * `/fragment`: Divide a tela em componentes modulares: `MapFragment` (navegação/azimute), `SeatingFragment` (grade de assentos dinâmicos) e `StatusFragment` (contadores de ocupação).
* **`/viewmodel`**: Contém `MapViewModel` e `SeatingViewModel`. Eles observam o repositório e atualizam a UI (`/fragment`) reativamente conforme o status do hardware muda, sobrevivendo às mudanças de ciclo de vida da tela.
* **`/aidl`**: Contém `IMag.aidl`, uma interface de comunicação de baixo nível (Android Interface Definition Language) para integração direta com hardwares/serviços específicos do dispositivo.

---

## 2. Fluxo do Usuário

1. **Inicialização:** O motorista abre o app, que automaticamente tenta estabelecer conexão com o Broker MQTT e os sensores via AIDL. O status da conexão é exibido na tela (`StatusFragment`).
2. **Monitoramento em Tempo Real:** Durante o trajeto, a interface é atualizada em tempo real. O `SeatingFragment` altera a cor dos assentos (Verde = Livre, Vermelho = Ocupado) de acordo com os dados recebidos dos dispositivos IoT.
3. **Encerramento de Rota:** O motorista clica em "Encerrar Rota".
4. **Validação de Segurança:** *Se houver alunos retidos:* O sistema bloqueia o encerramento e levanta a gaveta `SafetyBottomSheet`, detalhando exatamente quais assentos ainda acusam presença.
    * *Se todos desembarcaram:* A rota é encerrada com sucesso.

---

## 3. Instalação de Dependências e Ambiente

### Ferramentas Necessárias

A aplicação foi desenvolvida utilizando as seguintes especificações:

* **Android Studio:** Panda 4 (2025.3.4) - Build #AI-253.32098.
* **JDK:** OpenJDK 64-Bit Server VM (Versão 21.0.10).
* **SDK Android:** Mínimo configurado no `build.gradle` (API 24+).
* **Sistema Operacional:** Linux Pop!_OS 24.04 LTS.

### Dependências

O projeto utiliza as seguintes dependências (já configuradas no arquivo `build.gradle` do app). Para instalá-las, basta abrir o projeto no Android Studio e clicar em **"Sync Project with Gradle Files"**.

* `androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0` (Lógica MVVM)
* `androidx.activity:activity-ktx:1.13.0` (Ktx bindings)
* `androidx.fragment:fragment-ktx:1.8.9` (Navegação de Fragmentos)
* `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` (Cliente MQTT IoT)

### Permissões e Features

O arquivo `AndroidManifest.xml` já declara as permissões necessárias para comunicação IoT:

* `INTERNET`
* `ACCESS_NETWORK_STATE`

No arquivo `build.gradle`, as funcionalidades `aidl` e `buildConfig` estão habilitadas para permitir a comunicação com o hardware subjacente.

---

## 4. Configuração do Ambiente (Chaves e APIs)

Para garantir a segurança das credenciais e evitar que senhas sejam expostas no repositório de código, o projeto utiliza o arquivo `local.properties` para gerenciar as variáveis de configuração do **Broker MQTT**.

Para configurar o seu ambiente localmente, siga estes passos:

1. Na raiz do projeto, localize o arquivo de template chamado `local.properties.example`.
2. Crie uma cópia deste arquivo e renomeie-a para `local.properties` (este arquivo é ignorado pelo Git por padrão para proteger seus dados).
3. Abra o arquivo `local.properties` recém-criado e preencha as variáveis com o caminho do seu SDK e as credenciais do seu Broker MQTT (como HiveMQ, Mosquitto, etc):

```properties
sdk.dir=/PATH/TO/ANDROID/SDK
BROKER_URL=url_aqui
MQTT_USERNAME=seu_usuario_aqui
MQTT_PASSWORD=sua_senha_aqui
```

---

## 5. Ordem de Execução do Sistema

Para testar o sistema completo sem erros de sincronização, siga rigorosamente a ordem abaixo:

1. **Backend/Broker:** Inicie o Broker MQTT (ex: Mosquitto, HiveMQ, EMQX) na rede local ou nuvem. Verifique se a porta `1883` (ou a porta segura `8883`) está aberta.
2. **Hardware/IoT:** Ligue os dispositivos IoT embarcados nos assentos (ESP32). Confirme se eles estão conectados ao mesmo Broker MQTT e publicando nos tópicos corretos.
3. **Hardware Físico (Sensor Mag):** Caso esteja rodando em hardware customizado que necessite do serviço AIDL (`IMag.aidl`), certifique-se de que o serviço do sistema (HAL) está ativo.
4. **Aplicativo (App):** Por fim, inicie o aplicativo VanGuard Kids no RPI5. Ele irá se conectar ao broker, assinar os tópicos, buscar o status inicial via hardware e começar o monitoramento visual.
