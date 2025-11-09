# RealmMC Controller

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spigot](https://img.shields.io/badge/Spigot-1.21-blue.svg)
![Velocity](https://img.shields.io/badge/Velocity-3.4.0--SNAPSHOT-blueviolet.svg)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-green.svg)

O **Controller** é o **sistema nervoso central** da RealmMC. É um projeto *monorepo* que compila em **dois plugins separados** (Spigot e Velocity), servindo como o núcleo de gerenciamento de toda a rede.

Ele gerencia desde **permissões de jogadores e perfis** até **auto-escalonamento de servidores de minigame via Pterodactyl API**, unificando todos os servidores com **MongoDB** e **Redis**.

---

## ✨ Principais Funcionalidades

### 🧩 1. Sistemas de Rede (Compartilhados)

* **Sistema de Permissões (RoleModule):** Sistema customizado baseado em MongoDB com suporte a herança, pesos, prefixos/sufixos e grupos temporários. As alterações são sincronizadas em tempo real via Redis.
* **Perfis de Jogador (ProfileModule):** Armazena dados como histórico de IP, logins e tempo de jogo no MongoDB.
* **Rastreamento de Sessão (SessionTrackerService):** Mantém no Redis os dados de sessão dos jogadores (proxy, servidor e estado atual).
* **Sistema de Preferências:** Gerencia e sincroniza preferências de idioma e configurações do jogador via Redis.

### ⚙️ 2. Recursos do Plugin Proxy (Velocity)

* **Gerenciamento Dinâmico de Servidores (ServerManagerModule):**

  * *Auto-Scaling:* Inicia novas instâncias (ex: LOBBY_AUTO, GAME_BW) conforme a demanda.
  * *Auto-Shutdown:* Desliga instâncias inativas após um tempo.
  * *Alta Disponibilidade:* Monitora servidores estáticos (Lobby, Login) e reinicia via Pterodactyl API.
  * *Balanceamento de Carga:* Redireciona jogadores com `getBestLobby()`.
* **Autenticação de Jogadores (PremiumLoginListener):**

  * Verifica login *premium/offline* e bloqueia nicks indevidos.
  * Limita contas por IP (via `IPLimitListener`).
  * Atualiza o MOTD dinamicamente com base em dados do Redis.

### 🕹️ 3. Recursos do Plugin Spigot (Servidor de Jogo)

* **Gerenciador de Entidades:**

  * `/npc`: Criação de NPCs com skins personalizadas e ações de clique.
  * `/hologram`: Criação de hologramas de texto flutuante.
  * `/display`: Entidades de item flutuante com ações e escala ajustável.
  * `/particle`: Efeitos de partículas animadas (círculo, hélice, esfera, etc.).
* **Gerenciador de Mapas (MapManager):**

  * Cria e gerencia perfis de mapa no MongoDB.
  * Salva e carrega *schematics* (FAWE).
  * Sincroniza e ativa mapas via Redis.
  * Define regras de jogo e spawns.

---

## 📦 Dependências

| Tecnologia            | Obrigatório | Descrição                            |
| --------------------- | ----------- | ------------------------------------ |
| **Java 17+**          | ✅           | Linguagem base                       |
| **MongoDB**           | ✅           | Banco de dados principal             |
| **Redis**             | ✅           | Sincronização em tempo real          |
| **Pterodactyl Panel** | ✅           | Auto-escalonamento de servidores     |
| **PacketEvents**      | ⚙️          | Sistema de NPCs (Spigot)             |
| **ViaVersion**        | ⚙️          | Detecção de protocolo (Spigot)       |

---

## 🚀 Instalação e Configuração

### 1. Compilação

```bash
git clone https://github.com/Realm-MC/controller.git
cd controller
./gradlew build
```

Após a compilação, dois arquivos `.jar` serão gerados em `build/libs/`:

* `controller-spigot.jar` → Coloque em `plugins/` dos servidores Spigot.
* `controller-velocity.jar` → Coloque em `plugins/` do proxy Velocity.

---

### 2. Variáveis de Ambiente (Propriedades Java)

O Controller usa **Propriedades de Sistema Java (-D)** em vez de `config.yml`.

#### Exemplo: Velocity (Proxy)

```bash
java -Xms512M -Xmx512M \
  -DMONGO_URI="mongodb://admin:pass@host:27017" \
  -DMONGO_DB="RealmMC-controller" \
  -DREDIS_HOST="redis-host" \
  -DREDIS_PORT="6379" \
  -DREDIS_PASSWORD="redis-pass" \
  -DPTERODACTYL_PANEL_URL="https://painel.dominio.com.br" \
  -DPTERODACTYL_API_KEY="api_key_aqui" \
  -Dcontroller.proxyId="proxy-1" \
  -DRUN_SESSION_REAPER="true" \
  -jar velocity.jar
```

#### Exemplo: Spigot (Servidor de Jogo)

```bash
java -Xms2G -Xmx4G \
  -DMONGO_URI="mongodb://admin:pass@host:27017" \
  -DMONGO_DB="RealmMC-controller" \
  -DREDIS_HOST="redis-host" \
  -DREDIS_PORT="6379" \
  -DREDIS_PASSWORD="redis-pass" \
  -Dcontroller.serverId="lobby-1" \
  -jar paper.jar
```

> 🔐 **Importante:** As variáveis de ambiente devem ser idênticas entre o Proxy e os Servidores de Jogo.

---

## 🛠️ Comandos Principais

> Permissão: `controller.manager`

### Proxy (Velocity)

* `/sconfig` — Gerencia servidores (criar, deletar, listar, info, definir IP, porta, tipo, grupo mínimo, etc.)
* `/role` — Gerencia grupos e permissões dos jogadores (info, list, add, set, remove, clear)

### Servidor (Spigot)

* `/mapmanager` — Gerencia perfis de mapas e regras.
* `/npc` — Cria e administra NPCs.
* `/hologram` — Gerencia hologramas de texto.
* `/display` — Controla entidades de item flutuante.
* `/particle` — Gera e anima partículas.

---

## 🧱 Estrutura do Projeto

```
controller/
├── src/main/java/com/realmmc/controller/
│   ├── core/         # Lógica central de módulos e serviços
│   ├── modules/      # Módulos compartilhados (Database, Profile, Role, etc.)
│   ├── proxy/        # Código do plugin Velocity
│   ├── spigot/       # Código do plugin Spigot
│   └── shared/       # Classes DTO e repositórios (MongoDB/Redis)
└── build.gradle      # Lógica de build que gera os dois JARs
```

---

## 🧩 Licença

Este projeto é de uso exclusivo da **RealmMC**. Todos os direitos reservados.

> Desenvolvido por **Lucas Corrêa**.
