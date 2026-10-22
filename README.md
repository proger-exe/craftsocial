# CraftSocial

<!-- portfolio-card:start -->
[![CraftSocial — Игровые меню и экономика. Данные — через CraftCore.](docs/portfolio/card.png)](docs/portfolio/card.svg)
<!-- portfolio-card:end -->

Управлять магазином, кланом или группой прямо в Minecraft. CraftSocial добавляет игровые меню и команды к механикам, данные которых хранит [CraftCore API](https://github.com/proger-exe/craftcore-api). Плагин рассчитан на Paper/Purpur 1.21.

Игрок работает с привычным инвентарным интерфейсом. API проверяет действия и возвращает результат; плагин отображает его в игре. Есть магазины и управление их остатками, кланы, группы, экономические команды, квесты и текстовые теги игроков.

## Сеть за пределами игрового потока

HTTP-запросы через OkHttp выполняются в `ExecutorService`. Результаты передаются через `CompletableFuture` и возвращаются в серверный поток с помощью `BukkitScheduler.runTask()`. Ожидание API не занимает основной поток сервера.

Caffeine хранит ответы с настраиваемым TTL; при отсутствии актуальной записи плагин запрашивает API. Служебные вызовы используют JWT, полученный по `client_id` и `client_secret` из конфигурации.

Проект показывает интеграцию Java-плагина с отдельным backend: интерфейс остаётся в игре, а проверка действий выполняется API.

Обсудить похожий проект: [sh3l.beer](https://sh3l.beer).

## Сборка и установка

```bash
mvn package            # target/craftsocial-<version>.jar
```

Поместите JAR в `plugins/`, задайте `api.base-url` и служебные учётные данные в `config.yml`, затем перезапустите сервер. Команды плагина: магазины, экономика, кланы, группы, квесты, теги и `/craftsocial reload|info`.

## Структура проекта

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

## Лицензия

MIT
