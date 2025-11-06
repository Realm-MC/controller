# RealmMC Controller

Sistema modular de backend para servidores RealmMC que compartilha o mesmo núcleo entre **Velocity (proxy)** e **Spigot/Paper (servidor)**. O Controller provê serviços compartilhados, gerenciamento de módulos independentes e uma camada de integração consistente entre as plataformas.

## 📚 Sumário
- [Visão geral](#-visão-geral)
- [Principais recursos](#-principais-recursos)
- [Arquitetura em alto nível](#-arquitetura-em-alto-nível)
- [Módulos incluídos](#-módulos-incluídos)
- [Requisitos](#-requisitos)
- [Guia rápido de uso](#-guia-rápido-de-uso)
    - [Spigot/Paper](#spigotpaper)
    - [Velocity](#velocity)
- [Configuração de ambientes externos](#-configuração-de-ambientes-externos)
- [Estrutura do projeto](#-estrutura-do-projeto)
- [Boas práticas para novos módulos](#-boas-práticas-para-novos-módulos)
- [Como contribuir](#-como-contribuir)
- [Licença](#-licença)

## 🔎 Visão geral
O Controller centraliza integrações críticas de servidores Minecraft — autenticação, perfis, papéis, comandos, agendamentos, preferências e orquestração de servidores — em um único núcleo Java 21. Módulos são registrados automaticamente conforme a plataforma alvo, e serviços compartilhados são expostos através do `ServiceRegistry`, permitindo que diferentes projetos RealmMC reutilizem as mesmas funcionalidades com baixo acoplamento.

## ✨ Principais recursos
- Núcleo único compatível com Velocity e Spigot/Paper, com detecção automática de plataforma.
- `ModuleManager` com ordenação por prioridade e resolução de dependências entre módulos.
- Registro automático via anotação `@AutoRegister`, inclusive com escopo por plataforma.
- `ServiceRegistry` compartilhado para injetar serviços (Mongo, Redis, mensageria, players online, etc.).
- Sincronização distribuída de perfis, papéis e sessões utilizando Redis e MongoDB.
- Integração com serviços externos como GeoIP, mensageria interna, sons, partículas e gerenciamento Pterodactyl.
- Toolchain pronta para CI/CD com Gradle Shadow e publicação em repositório Maven (GitHub Packages).

## 🏗 Arquitetura em alto nível

