# RealmMC Controller

Sistema modular de controller para servidores Minecraft que funciona tanto no **Velocity (Proxy)** quanto no **Spigot/Paper**.

## 🏗️ Arquitetura

O sistema é baseado em uma arquitetura modular com os seguintes componentes principais:

- **ControllerCore**: Classe base abstrata que gerencia módulos e serviços
- **ModuleManager**: Gerencia o ciclo de vida dos módulos com controle de dependências
- **ServiceRegistry**: Registro centralizado de serviços compartilhados
- **Módulos**: Componentes funcionais independentes

## 📦 Módulos Disponíveis

### Módulos Core
- **DatabaseModule**: Gerencia conexões MongoDB e Redis
- **ProfileModule**: Sistema de perfis de jogadores com sincronização
- **CommandModule**: Sistema de comandos compartilhados
- **SchedulerModule**: Gerenciamento de tarefas agendadas

### Módulos Específicos
- **ProxyModule**: Funcionalidades específicas do Velocity
- **SpigotModule**: Funcionalidades específicas do Spigot/Paper

## 🚀 Como Usar

### Para Desenvolvedores Spigot/Paper

#### 1. Configuração Básica

```java
public class MeuPlugin extends JavaPlugin {
    private Main controllerCore;
    
    @Override
    public void onEnable() {
        // Inicializar o Controller Core
        controllerCore = new Main();
        controllerCore.initialize();
        
        getLogger().info("Plugin habilitado com Controller Core!");
    }
    
    @Override
    public void onDisable() {
        if (controllerCore != null) {
            controllerCore.shutdown();
        }
    }
}
```

#### 2. Acessando Serviços

```java
// Obter o ServiceRegistry
ServiceRegistry serviceRegistry = controllerCore.getServiceRegistry();

// Acessar serviços específicos
ProfileService profileService = serviceRegistry.getService(ProfileService.class);
CommandRegistry commandRegistry = serviceRegistry.getService(CommandRegistry.class);

// Verificar se um serviço está disponível
if (serviceRegistry.hasService(ProfileService.class)) {
    // Usar o serviço
}
```

#### 3. Criando Módulos Customizados

```java
public class MeuModuloCustomizado extends AbstractCoreModule {
    
    @Override
    public String getName() {
        return "MeuModuloCustomizado";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getDescription() {
        return "Meu módulo personalizado";
    }
    
    @Override
    public int getPriority() {
        return 50; // Prioridade média
    }
    
    @Override
    public List<String> getDependencies() {
        return List.of("DatabaseModule"); // Depende do módulo de banco
    }
    
    @Override
    protected void onEnable() {
        logger.info("Meu módulo foi habilitado!");
        // Lógica de inicialização
    }
    
    @Override
    protected void onDisable() {
        logger.info("Meu módulo foi desabilitado!");
        // Lógica de finalização
    }
}
```

#### 4. Registrando Módulos Customizados

```java
// No seu plugin principal
ModuleManager moduleManager = controllerCore.getModuleManager();
moduleManager.registerModule(new MeuModuloCustomizado());
```

### Para Desenvolvedores Velocity (Proxy)

#### 1. Configuração Básica

```java
@Plugin(id = "meuplugin", name = "MeuPlugin", version = "1.0.0")
public class MeuProxy extends ControllerCore {
    
    @Inject
    public MeuProxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        super(logger);
        // Configuração inicial
    }
    
    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        initialize();
        logger.info("Proxy habilitado com Controller Core!");
    }
    
    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        shutdown();
    }
}
```

## 🔧 Configuração de Banco de Dados

### Variáveis de Ambiente

```bash
# MongoDB
MONGO_URI=mongodb://localhost:27017
MONGO_DB=realmmc

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
```

### Propriedades do Sistema

```java
// Configurar via código
System.setProperty("mongo.uri", "mongodb://localhost:27017");
System.setProperty("mongo.db", "realmmc");
System.setProperty("redis.host", "localhost");
System.setProperty("redis.port", "6379");
```

## 📋 API de Serviços

### ProfileService

```java
ProfileService profileService = serviceRegistry.getService(ProfileService.class);

// Buscar perfil por UUID
Optional<Profile> profile = profileService.getByUuid(playerUuid);

// Buscar perfil por nome
Optional<Profile> profile = profileService.getByName("PlayerName");

// Salvar perfil
profileService.save(profile);
```

### CommandRegistry

```java
CommandRegistry commandRegistry = serviceRegistry.getService(CommandRegistry.class);

// Registrar comando
commandRegistry.registerCommand("meucomando", commandInfo);

// Obter comando
Optional<CommandInfo> command = commandRegistry.getCommand("meucomando");

// Listar todos os comandos
Map<String, CommandInfo> commands = commandRegistry.getAllCommands();
```

## 🔄 Ciclo de Vida dos Módulos

1. **Registro**: Módulos são registrados no ModuleManager
2. **Ordenação**: Módulos são ordenados por prioridade e dependências
3. **Habilitação**: Módulos são habilitados em ordem
4. **Execução**: Módulos executam suas funcionalidades
5. **Desabilitação**: Módulos são desabilitados em ordem reversa

## ⚙️ Prioridades dos Módulos

- **100+**: Alta prioridade (DatabaseModule, SchedulerModule)
- **50-99**: Prioridade média (ProfileModule, CommandModule)
- **1-49**: Baixa prioridade (ProxyModule, SpigotModule)

## 🛠️ Desenvolvimento

### Estrutura de Pastas

```
src/main/java/com/realmmc/controller/
├── core/                    # Classes base do sistema
│   ├── modules/            # Interfaces e classes base de módulos
│   └── services/           # ServiceRegistry
├── modules/                # Módulos funcionais
│   ├── database/          # Módulo de banco de dados
│   ├── profile/           # Módulo de perfis
│   ├── commands/          # Módulo de comandos
│   ├── scheduler/         # Módulo de agendamento
│   ├── proxy/             # Módulo específico do proxy
│   └── spigot/            # Módulo específico do spigot
├── proxy/                 # Implementação Velocity
├── spigot/                # Implementação Spigot
└── shared/                # Utilitários compartilhados
```

### Boas Práticas

1. **Sempre estender AbstractCoreModule** para novos módulos
2. **Definir dependências corretamente** para evitar problemas de inicialização
3. **Usar o ServiceRegistry** para compartilhar serviços entre módulos
4. **Implementar onEnable() e onDisable()** adequadamente
5. **Usar logging apropriado** através do logger do módulo

## 📝 Exemplos Práticos

### Exemplo: Sistema de Economia

```java
public class EconomyModule extends AbstractCoreModule {
    private EconomyService economyService;
    
    @Override
    public List<String> getDependencies() {
        return List.of("DatabaseModule", "ProfileModule");
    }
    
    @Override
    protected void onEnable() {
        economyService = new EconomyService();
        
        // Registrar no ServiceRegistry
        ServiceRegistry registry = getServiceRegistry();
        registry.registerService(EconomyService.class, economyService);
        
        logger.info("Sistema de economia habilitado!");
    }
    
    @Override
    protected void onDisable() {
        if (economyService != null) {
            economyService.shutdown();
        }
    }
}
```

### Exemplo: Comando Customizado

```java
@Cmd(name = "money", aliases = {"cash", "bal"})
public class MoneyCommand implements CommandInterface {
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando!");
            return;
        }
        
        Player player = (Player) sender;
        ServiceRegistry registry = // obter registry
        EconomyService economy = registry.getService(EconomyService.class);
        
        double balance = economy.getBalance(player.getUniqueId());
        player.sendMessage("Seu saldo: $" + balance);
    }
}
```

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/MinhaFeature`)
3. Commit suas mudanças (`git commit -m 'Adiciona MinhaFeature'`)
4. Push para a branch (`git push origin feature/MinhaFeature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes.