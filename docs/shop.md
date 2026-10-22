# Shops: полная документация (новый формат)

Документ описывает актуальную реализацию шопов после перехода на модель:

- `ShopItem.quantity` — размер лота (метаданные позиции),
- `ShopItem.total_count` — физический остаток товара в магазине,
- `Shop.inventory` — JSON-зеркало остатков/прайса для быстрого чтения и контроля консистентности.

---

## 1) Общая концепция

Шоп состоит из:

- магазина (`Shop`),
- позиций магазина (`ShopItem`) с уникальной связкой `shop + item_id`,
- зеркала в `Shop.inventory` (ключ: `item_id`, значение: объект с `price`, `quantity`, `total_count`).

Цель формата:

- разделить размер лота и фактический остаток,
- избежать дюпа за счет атомарного списания `total_count`,
- не терять параметры товара при нулевом остатке.

---

## 2) Модель данных

### 2.1 `Shop`

Источник: `core/models/economics/shops.py`

Поля:

- `id` (BigInt, PK) — ID магазина
- `owner_id` (FK -> Player) — владелец
- `name` (Text) — имя магазина
- `inventory` (JSON) — зеркало позиций по `item_id`

Формат `inventory`:

```json
{
  "minecraft:diamond": {
    "price": 100,
    "quantity": 1,
    "total_count": 12
  },
  "minecraft:stone": {
    "price": 2,
    "quantity": 64,
    "total_count": 640
  }
}
```

### 2.2 `ShopItem`

Источник: `core/models/economics/shops.py`

Поля:

- `id` (автогенерируемый PK)
- `item_id` (string) — Minecraft item id (например `minecraft:diamond`)
- `shop` (FK -> Shop)
- `price` (int) — цена за 1 единицу
- `quantity` (int) — размер лота (по умолчанию `1`)
- `total_count` (int) — текущий физический остаток в магазине

Ограничения:

- `unique_together = ("shop", "item_id")`
- `indexes = ("shop", "item_id")`

---

## 3) Бизнес-семантика полей

- `price` — цена за одну единицу товара.
- `quantity` — размер лота позиции (информационная/доменная характеристика позиции).
- `total_count` — именно этот остаток уменьшается при покупке.

Важно:

- запись `ShopItem` не обязана удаляться при `total_count = 0`;
- при нуле позиция может быть скрыта из выдачи и позже пополнена, сохраняя цену/параметры.

---

## 4) API шопов

Базовый префикс: `/user`

Аутентификация: заголовок `X-Player-ID`.

### 4.1 Магазины

#### Create shop

`POST /user/shops`

Request:

```json
{
  "name": "My Shop"
}
```

Response `ShopResponse`:

```json
{
  "id": 1,
  "owner_id": 123,
  "name": "My Shop"
}
```

#### Get shops

`GET /user/shops?owner_id=<optional>&page=1&per_page=20`

- без `owner_id` возвращаются магазины текущего игрока;
- с `owner_id` — магазины указанного владельца.

#### Get shop by id

`GET /user/shops/{shop_id}`

Response `ShopWithItemsResponse`:

```json
{
  "id": 1,
  "owner_id": 123,
  "name": "My Shop",
  "items": [
    {
      "id": 10,
      "item_id": "minecraft:diamond",
      "shop_id": 1,
      "price": 100,
      "quantity": 1,
      "total_count": 12
    }
  ]
}
```

#### Update shop

`PUT /user/shops/{shop_id}`

- только владелец.

#### Delete shop

`DELETE /user/shops/{shop_id}`

- только владелец.

---

### 4.2 Позиции магазина

#### Create shop item

`POST /user/shops/{shop_id}/items`

Request `ShopItemCreate`:

```json
{
  "item_id": "minecraft:diamond",
  "price": 100,
  "quantity": 1,
  "total_count": 20
}
```

Response `ShopItemResponse`:

```json
{
  "id": 10,
  "item_id": "minecraft:diamond",
  "shop_id": 1,
  "price": 100,
  "quantity": 1,
  "total_count": 20
}
```

#### Get shop items

`GET /user/shops/{shop_id}/items`

Возвращает позиции с доступным остатком (по текущей реализации: `total_count > 0`).

#### Update shop item

`PUT /user/shops/{shop_id}/items/{item_id}`

Request `ShopItemUpdate` (любые поля опциональны):

```json
{
  "price": 120,
  "quantity": 1,
  "total_count": 18
}
```

#### Delete shop item

`DELETE /user/shops/{shop_id}/items/{item_id}`

- только владелец магазина.

---

### 4.3 Покупка

#### Buy shop item

`POST /user/shops/{shop_id}/items/{item_id}/buy`

`item_id` в URL — это ID записи `ShopItem` (PK), не Minecraft `item_id`.

Request:

```json
{
  "quantity": 5
}
```

Response `ShopPurchaseResponse`:

```json
{
  "shop_item_id": 10,
  "item_id": "minecraft:diamond",
  "quantity": 5,
  "total_paid": 500,
  "remaining_total_count": 15
}
```

Логика:

1. Проверка существования позиции и принадлежности магазину.
2. Проверка `quantity >= 1`.
3. Проверка достаточности `total_count`.
4. Проверка баланса покупателя (`price * quantity`).
5. Списание баланса у покупателя.
6. Зачисление владельцу магазина.
7. Списание `total_count` у позиции.
8. Синхронизация зеркала `Shop.inventory`.

---

## 5) Права доступа

- Операции изменения магазина и позиций (`create/update/delete`) — только владелец магазина.
- Покупка доступна авторизованному игроку, если хватает баланса и остатков.

Типичные коды:

- `400` — ошибка валидации/остатков (`Quantity must be at least 1`, `Insufficient quantity in shop`)
- `402` — недостаточно средств (`Insufficient balance`)
- `403` — нет прав (не владелец)
- `404` — магазин/позиция не найдены
- `500` — внутренняя ошибка

---

## 6) Анти-дюп и конкурентность

Покупка выполняется атомарно:

- транзакция БД,
- `SELECT ... FOR UPDATE` на:
  - `ShopItem`,
  - `Shop`,
  - покупателя,
  - владельца.

Это исключает двойное списание одного остатка при одновременных покупках.

---

## 7) Синхронизация `ShopItem` и `Shop.inventory`

В менеджере шопов реализована двусторонняя поддержка консистентности:

- при создании/обновлении `ShopItem` обновляется `Shop.inventory[item_id]`,
- при удалении `ShopItem` запись удаляется из `Shop.inventory`,
- при покупке после изменения `total_count` обновляется зеркало в `Shop.inventory`.

`Shop.inventory` не является публичным контрактом API ответов, но используется как внутренний агрегат и для контроля состояния.

---

## 8) Миграция на новый формат

Файлы:

- `shop_inventory_migration.sql`
- `docs/SHOP_INVENTORY_MIGRATION.md`

Что делает SQL:

1. Добавляет `shop_items.total_count` (если отсутствует).
2. Делает backfill `total_count` из старого `quantity` (legacy-состояние).
3. Заполняет `shops.inventory` из `shop_items`.
4. Нормализует пустые `inventory` в `{}`.

Автозапуск:

- при старте приложения вызывается `run_startup_migrations()`;
- миграция отмечается в таблице `startup_migrations` и повторно не исполняется.

---

## 9) Рекомендации для клиентов

- Используйте `remaining_total_count` как источник остатка после покупки.
- Не интерпретируйте `quantity` как текущий остаток; для остатков используйте `total_count`.
- Для покупки отправляйте реальное количество единиц к списанию.

---

## 10) Где смотреть реализацию

- Модель: `core/models/economics/shops.py`
- Менеджер: `core/managers/economics/shops.py`
- Роуты: `api/routers/user/shops.py`
- Схемы: `api/schemas/shops.py`
- Автомиграции на старте: `core/models/startup_migrations.py`
