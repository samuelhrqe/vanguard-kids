# Node-RED - Plataforma IoT

Node-RED é uma ferramenta de programação visual que permite a criação de fluxos de dados para IoT e automação. Baseado em Node.js, ele fornece uma interface de arrastar e soltar para conectar nós. Ele foi escolhido para este projeto devido à sua flexibilidade, facilidade de uso e experiência de uso anterior com a plataforma.

## Instalação

O Node-RED pode ser instalado de várias maneiras. Para esse projeto, o Node-RED foi instalado localmente.

### Pré-requisitos

- Node.js ([Supported Node versions](https://nodered.org/docs/faq/node-versions)).
  - Para instalação do Node.js, foi utilizado o [NVM](https://github.com/nvm-sh/nvm/blob/master/README.md). O link acima fornece instruções detalhadas para instalação do NVM e do Node.js.

Após a instalação do Node.js, o Node-RED pode ser instalado globalmente usando o npm:

```bash
sudo npm install -g node-red
```

Mais informações sobre a instalação do Node-RED podem ser encontradas na [documentação oficial](https://nodered.org/docs/getting-started/local).

## Iniciando o Node-RED

O fluxo criado para esse projeto está disponível em `flow.json`. Para iniciá-lo, execute o seguinte comando no terminal:

```bash
node-red flow.json
```

Após a execução do comando, o Node-RED estará disponível no navegador através do endereço [http://localhost:1880](http://localhost:1880). A interface de usuário do Node-RED permite a criação e edição de fluxos de dados de forma intuitiva.

## Configurando o fluxo

### Conexão com o MQTT Broker

### Instalação do Dashboard 2.0
