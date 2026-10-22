# CraftSocial

<!-- portfolio-card:start -->
[![sh3lby.code — нейтральная обложка](docs/portfolio/card.png)](docs/portfolio/card.svg)
<!-- portfolio-card:end -->

Purpur / Paper 1.21 plugin that brings a server's meta-game into the game: shops with inventory GUI, clans and parties with management menus, economy commands, quests, player text tags. The plugin is stateless: every action is validated by [CraftCore API](https://github.com/proger-exe/craftcore-api), responses are cached with short TTLs, and nothing touches the main thread while a request is in flight.

## Design

- **Fully async API client** (`api/ApiClient`, OkHttp + Gson): requests run on an `ExecutorService`, results are chained with `CompletableFuture` and handed back to the server thread via `BukkitScheduler.runTask()`. TPS stays flat under API latency.
- **Caffeine cache** (`cache/`) with 10–30 s TTLs per entity; GUI opens from cache instantly, refreshes in the background.
- **Service auth**: admin-level calls carry a service JWT obtained with `client_id` / `client_secret` from the plugin config.
- **GUIs** (`gui/`): shop, shop management, clan, clan management, party management.
- **Commands** (`commands/`): shop, economy, clan, party, quest, text tags; `/craftsocial reload|info`.

Around 12k lines of Java 21.

## Build and install

```bash
mvn package            # target/craftsocial-<version>.jar
```

Drop the jar into `plugins/`, set `api.base-url` and the service credentials in `config.yml`, restart.

## Layout

```
src/main/java/dev/sh3lby/craftsocial/
  api/        ApiClient, endpoints
  async/      executor and main-thread bridge
  cache/      Caffeine caches
  commands/   command handlers
  gui/        inventory GUIs
  handlers/   event handlers
  listeners/  Bukkit listeners
  models/     API DTOs
  storage/    local state
  tasks/      scheduled refresh tasks
  utils/      MessagePalette, PlayerIdMapper
docs/         ARCHITECT.md (design, RU), MODELS.md, shop.md, TRANSFERS.md
preficses/    rank badge images
```

## License

MIT
