# Модели данных и схемы запросов/ответов

Данная документация описывает все схемы данных (Pydantic models), используемые в API для запросов и ответов.

---

## Общие схемы

### SuccessResponse

Стандартный ответ об успешной операции.

```json
{
  "success": true,
  "message": "Operation completed successfully"
}
```

**Поля:**
- `success` (bool, required) - Статус операции (всегда `true`)
- `message` (string, optional) - Сообщение об успехе

---

### ErrorResponse

Стандартный ответ об ошибке.

```json
{
  "success": false,
  "error": "Error message",
  "detail": "Additional error details"
}
```

**Поля:**
- `success` (bool, required) - Статус операции (всегда `false`)
- `error` (string, required) - Сообщение об ошибке
- `detail` (string, optional) - Дополнительные детали ошибки

---

### PaginatedResponse

Обертка для пагинированных ответов.

```json
{
  "items": [...],
  "total": 100,
  "page": 1,
  "per_page": 20,
  "pages": 5,
  "has_next": true,
  "has_prev": false
}
```

**Поля:**
- `items` (array, required) - Массив элементов
- `total` (int, required) - Общее количество элементов
- `page` (int, required) - Текущая страница
- `per_page` (int, required) - Элементов на странице
- `pages` (int, required) - Всего страниц
- `has_next` (bool, required) - Есть ли следующая страница
- `has_prev` (bool, required) - Есть ли предыдущая страница

---

### PaginationParams

Параметры пагинации для запросов.

```json
{
  "page": 1,
  "per_page": 20
}
```

**Поля:**
- `page` (int, optional, default: 1) - Номер страницы (>= 1)
- `per_page` (int, optional, default: 20) - Элементов на странице (1-100)

---

## Profile (Профиль игрока)

### PlayerProfileCreate

Схема для создания профиля игрока.

```json
{
  "player_id": 123,
  "nickname": "PlayerName",
  "age": 18,
  "gender": "male",
  "source": "api"
}
```

**Поля:**
- `player_id` (int, required) - ID игрока
- `nickname` (string, required, max: 32) - Никнейм игрока
- `age` (int, optional, default: 13, min: 13) - Возраст игрока
- `gender` (enum, optional) - Пол: `male`, `female`, `other`
- `source` (string, optional, default: "api") - Источник регистрации

---

### PlayerProfileUpdate

Схема для обновления профиля игрока.

```json
{
  "nickname": "NewNickname",
  "age": 19,
  "gender": "female",
  "source": "website"
}
```

**Поля:**
- `nickname` (string, optional) - Никнейм игрока
- `age` (int, optional, min: 13) - Возраст игрока
- `gender` (enum, optional) - Пол: `male`, `female`, `other`
- `source` (string, optional) - Источник регистрации

---

### PlayerProfileResponse

Ответ с данными профиля игрока.

```json
{
  "id": 1,
  "player_id": 123,
  "nickname": "PlayerName",
  "age": 18,
  "gender": "male",
  "source": "api"
}
```

**Поля:**
- `id` (int, required) - ID профиля
- `player_id` (int, required) - ID игрока
- `nickname` (string, required) - Никнейм игрока
- `age` (int, required) - Возраст игрока
- `gender` (string, required) - Пол игрока
- `source` (string, required) - Источник регистрации

---

### PlayerAnalyticsResponse

Ответ с аналитикой игрока.

```json
{
  "id": 1,
  "player_id": 123,
  "hours_played": 100,
  "invites_count": 5,
  "socials": {
    "discord": "https://discord.gg/...",
    "youtube": "https://youtube.com/..."
  },
  "world_rating": "diamond",
  "clan_id": 5,
  "party_id": null,
  "earned_money": 50000,
  "spent_money": 30000,
  "bio": "I love playing!",
  "era_stamp": "alpha",
  "join_streak_days": 10,
  "join_streak_max": 15,
  "last_login": "2024-01-01T12:00:00",
  "in_war": false
}
```

**Поля:**
- `id` (int, required) - ID аналитики
- `player_id` (int, required) - ID игрока
- `hours_played` (int, required) - Часов сыграно
- `invites_count` (int, required) - Количество приглашений
- `socials` (object, required) - Социальные сети (ключ: название, значение: ссылка)
- `world_rating` (string, optional) - Рейтинг мира: `wood`, `stone`, `iron`, `gold`, `diamond`, `netherite`
- `clan_id` (int, optional) - ID клана
- `party_id` (int, optional) - ID группы
- `earned_money` (int, required) - Заработано денег
- `spent_money` (int, required) - Потрачено денег
- `bio` (string, optional) - Биография игрока
- `era_stamp` (string, optional) - Эра: `alpha`, `beta`, `release`
- `join_streak_days` (int, required) - Текущая серия входов (дни)
- `join_streak_max` (int, required) - Максимальная серия входов (дни)
- `last_login` (datetime, optional) - Последний вход
- `in_war` (bool, required) - Участвует ли в войне

---

### PlayerAnalyticsUpdate

Схема для обновления аналитики игрока.

```json
{
  "bio": "Updated bio",
  "world_rating": "diamond",
  "era_stamp": "beta"
}
```

**Поля:**
- `bio` (string, optional) - Биография игрока
- `world_rating` (enum, optional) - Рейтинг мира
- `era_stamp` (enum, optional) - Эра

---

### SocialLinkAdd

Схема для добавления социальной ссылки.

```json
{
  "social_name": "discord",
  "link": "https://discord.gg/..."
}
```

**Поля:**
- `social_name` (string, required) - Название социальной сети
- `link` (string, required) - Ссылка

---

## Economics (Экономика)

### TransactionCreate

Схема для создания транзакции.

```json
{
  "amount": 1000,
  "transaction_type": "transfer",
  "description": "Payment for service",
  "from_player_id": 1,
  "to_player_id": 2,
  "related_entity_type": "quest",
  "related_entity_id": 5
}
```

**Поля:**
- `amount` (int, required) - Сумма транзакции
- `transaction_type` (enum, required) - Тип: `deposit`, `withdrawal`, `transfer`, `reward`, `payment`, `refund`
- `description` (string, required) - Описание транзакции
- `from_player_id` (int, optional) - ID отправителя
- `to_player_id` (int, optional) - ID получателя
- `related_entity_type` (string, optional) - Тип связанной сущности (например, "quest")
- `related_entity_id` (int, optional) - ID связанной сущности

---

### TransactionResponse

Ответ с данными транзакции.

```json
{
  "id": 1,
  "from_player_id": 1,
  "to_player_id": 2,
  "amount": 1000,
  "transaction_type": "transfer",
  "description": "Payment for service",
  "related_entity_type": "quest",
  "related_entity_id": 5,
  "created_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID транзакции
- `from_player_id` (int, optional) - ID отправителя
- `to_player_id` (int, optional) - ID получателя
- `amount` (int, required) - Сумма
- `transaction_type` (string, required) - Тип транзакции
- `description` (string, required) - Описание
- `related_entity_type` (string, optional) - Тип связанной сущности
- `related_entity_id` (int, optional) - ID связанной сущности
- `created_at` (datetime, required) - Дата создания

---

### QuestCreate

Схема для создания квеста.

```json
{
  "title": "Collect 10 diamonds",
  "description": "Collect 10 diamonds and bring them to the quest giver",
  "quest_type": "system",
  "reward_amount": 1000,
  "payment_amount": 0,
  "max_completions": null,
  "expires_at": null,
  "requirements": {
    "item_id": "minecraft:diamond",
    "quantity": 10
  }
}
```

**Поля:**
- `title` (string, required) - Название квеста
- `description` (string, required) - Описание квеста
- `quest_type` (enum, required) - Тип: `system`, `player`
- `reward_amount` (int, optional, default: 0) - Награда за выполнение
- `payment_amount` (int, optional, default: 0) - Оплата за создание (для player квестов)
- `max_completions` (int, optional) - Максимальное количество выполнений
- `expires_at` (datetime, optional) - Дата истечения
- `requirements` (object, optional) - Требования квеста (JSON)

---

### QuestResponse

Ответ с данными квеста.

```json
{
  "id": 1,
  "creator_id": null,
  "title": "Collect 10 diamonds",
  "description": "Collect 10 diamonds and bring them to the quest giver",
  "quest_type": "system",
  "reward_amount": 1000,
  "payment_amount": 0,
  "status": "active",
  "max_completions": null,
  "current_completions": 5,
  "requirements": {
    "item_id": "minecraft:diamond",
    "quantity": 10
  },
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID квеста
- `creator_id` (int, optional) - ID создателя (для player квестов)
- `title` (string, required) - Название
- `description` (string, required) - Описание
- `quest_type` (string, required) - Тип квеста
- `reward_amount` (int, required) - Награда
- `payment_amount` (int, required) - Оплата за создание
- `status` (string, required) - Статус: `active`, `completed`, `cancelled`
- `max_completions` (int, optional) - Максимум выполнений
- `current_completions` (int, required) - Текущее количество выполнений
- `requirements` (object, required) - Требования
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### QuestProgressResponse

Ответ с прогрессом выполнения квеста.

```json
{
  "id": 1,
  "quest_id": 5,
  "player_id": 123,
  "progress": {
    "items_collected": 5,
    "total_required": 10
  },
  "is_completed": false,
  "completed_at": null,
  "claimed_reward": false,
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID прогресса
- `quest_id` (int, required) - ID квеста
- `player_id` (int, required) - ID игрока
- `progress` (object, required) - Прогресс выполнения (JSON)
- `is_completed` (bool, required) - Завершен ли квест
- `completed_at` (datetime, optional) - Дата завершения
- `claimed_reward` (bool, required) - Получена ли награда
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

## Safety (Модерация)

### BanCreate

Схема для создания бана.

```json
{
  "player_id": 123,
  "reason": "Cheating",
  "expires_at": "2024-12-31T23:59:59"
}
```

**Поля:**
- `player_id` (int, required) - ID игрока
- `reason` (string, required) - Причина бана
- `expires_at` (datetime, optional) - Дата истечения (null = перманентный бан)

---

### BanResponse

Ответ с данными бана.

```json
{
  "id": 1,
  "player_id": 123,
  "admin_id": 1,
  "reason": "Cheating",
  "banned_at": "2024-01-01T12:00:00",
  "expires_at": "2024-12-31T23:59:59",
  "is_active": true,
  "unbanned_at": null,
  "unbanned_by_id": null
}
```

**Поля:**
- `id` (int, required) - ID бана
- `player_id` (int, required) - ID игрока
- `admin_id` (int, optional) - ID администратора
- `reason` (string, required) - Причина
- `banned_at` (datetime, required) - Дата бана
- `expires_at` (datetime, optional) - Дата истечения
- `is_active` (bool, required) - Активен ли бан
- `unbanned_at` (datetime, optional) - Дата разбана
- `unbanned_by_id` (int, optional) - ID разбанившего

---

### MuteCreate

Схема для создания мута.

```json
{
  "player_id": 123,
  "reason": "Spam",
  "expires_at": "2024-12-31T23:59:59"
}
```

**Поля:**
- `player_id` (int, required) - ID игрока
- `reason` (string, required) - Причина мута
- `expires_at` (datetime, required) - Дата истечения

---

### MuteResponse

Ответ с данными мута.

```json
{
  "id": 1,
  "player_id": 123,
  "admin_id": 1,
  "reason": "Spam",
  "muted_at": "2024-01-01T12:00:00",
  "expires_at": "2024-12-31T23:59:59",
  "is_active": true
}
```

**Поля:**
- `id` (int, required) - ID мута
- `player_id` (int, required) - ID игрока
- `admin_id` (int, optional) - ID администратора
- `reason` (string, required) - Причина
- `muted_at` (datetime, required) - Дата мута
- `expires_at` (datetime, required) - Дата истечения
- `is_active` (bool, required) - Активен ли мут

---

### WarnCreate

Схема для создания предупреждения.

```json
{
  "player_id": 123,
  "reason": "Inappropriate behavior"
}
```

**Поля:**
- `player_id` (int, required) - ID игрока
- `reason` (string, required) - Причина предупреждения

---

### WarnResponse

Ответ с данными предупреждения.

```json
{
  "id": 1,
  "player_id": 123,
  "admin_id": 1,
  "reason": "Inappropriate behavior",
  "warned_at": "2024-01-01T12:00:00",
  "is_active": true
}
```

**Поля:**
- `id` (int, required) - ID предупреждения
- `player_id` (int, required) - ID игрока
- `admin_id` (int, required) - ID администратора
- `reason` (string, required) - Причина
- `warned_at` (datetime, required) - Дата предупреждения
- `is_active` (bool, required) - Активно ли предупреждение

---

### AdminActionLogResponse

Ответ с логом действия администратора.

```json
{
  "id": 1,
  "admin_id": 1,
  "action_type": "ban",
  "target_player_id": 123,
  "details": {
    "reason": "Cheating",
    "ban_id": 5
  },
  "created_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID лога
- `admin_id` (int, required) - ID администратора
- `action_type` (string, required) - Тип действия
- `target_player_id` (int, optional) - ID целевого игрока
- `details` (object, required) - Детали действия (JSON)
- `created_at` (datetime, required) - Дата создания

---

## Social (Социальные функции)

### FriendRequest

Схема для запроса в друзья.

```json
{
  "friend_id": 456
}
```

**Поля:**
- `friend_id` (int, required) - ID друга

---

### FriendResponse

Ответ с данными друга.

```json
{
  "id": 1,
  "player_id": 123,
  "friend_id": 456,
  "status": "accepted",
  "requested_at": "2024-01-01T12:00:00",
  "accepted_at": "2024-01-01T12:05:00",
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:05:00"
}
```

**Поля:**
- `id` (int, required) - ID записи
- `player_id` (int, required) - ID игрока
- `friend_id` (int, required) - ID друга
- `status` (string, required) - Статус: `pending`, `accepted`, `rejected`, `blocked`
- `requested_at` (datetime, required) - Дата запроса
- `accepted_at` (datetime, optional) - Дата принятия
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### MutualFriendsResponse

Ответ с общими друзьями.

```json
{
  "player1_id": 123,
  "player2_id": 456,
  "mutual_friends": [
    {
      "id": 789,
      "nickname": "Player789"
    }
  ],
  "count": 1
}
```

**Поля:**
- `player1_id` (int, required) - ID первого игрока
- `player2_id` (int, required) - ID второго игрока
- `mutual_friends` (array, required) - Массив общих друзей
- `count` (int, required) - Количество общих друзей

---

## Clans (Кланы)

### ClanCreate

Схема для создания клана.

```json
{
  "name": "Elite Warriors",
  "tag": "EW",
  "description": "The best clan on the server",
  "prefix": "[EW]"
}
```

**Поля:**
- `name` (string, required) - Название клана
- `tag` (string, required) - Тег клана
- `description` (string, optional, default: "") - Описание
- `prefix` (string, optional, default: "") - Префикс для никнеймов

---

### ClanResponse

Ответ с данными клана.

```json
{
  "id": 1,
  "owner_id": 123,
  "members": {
    "123": {
      "role": "leader",
      "joined_at": "2024-01-01T12:00:00"
    },
    "456": {
      "role": "member",
      "joined_at": "2024-01-02T12:00:00"
    }
  },
  "name": "Elite Warriors",
  "description": "The best clan on the server",
  "tag": "EW",
  "prefix": "[EW]",
  "inventory": [],
  "balance": 10000,
  "homes": [
    {
      "name": "spawn",
      "dim": "overworld",
      "x": 0,
      "y": 64,
      "z": 0
    }
  ],
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID клана
- `owner_id` (int, required) - ID владельца
- `members` (object, required) - Участники (ключ: player_id, значение: данные участника)
- `name` (string, required) - Название
- `description` (string, required) - Описание
- `tag` (string, required) - Тег
- `prefix` (string, required) - Префикс
- `inventory` (array, required) - Инвентарь клана
- `balance` (int, required) - Баланс клана
- `homes` (array, required) - Дома клана
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### ClanUpdate

Схема для обновления клана.

```json
{
  "name": "New Name",
  "description": "New description",
  "tag": "NN",
  "prefix": "[NN]"
}
```

**Поля:**
- `name` (string, optional) - Название
- `description` (string, optional) - Описание
- `tag` (string, optional) - Тег
- `prefix` (string, optional) - Префикс

---

### ClanMemberAdd

Схема для добавления участника в клан.

```json
{
  "player_id": 456,
  "role": "member"
}
```

**Поля:**
- `player_id` (int, required) - ID игрока
- `role` (enum, optional, default: "member") - Роль: `leader`, `moderator`, `member`

---

### ClanMemberUpdate

Схема для обновления роли участника.

```json
{
  "role": "moderator"
}
```

**Поля:**
- `role` (enum, required) - Роль: `leader`, `moderator`, `member`

---

## Party (Группы)

### PartyCreate

Схема для создания группы.

```json
{
  "name": "Adventure Squad"
}
```

**Поля:**
- `name` (string, required) - Название группы

---

### PartyResponse

Ответ с данными группы.

```json
{
  "id": 1,
  "name": "Adventure Squad",
  "leader_id": 123,
  "members": [123, 456, 789],
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID группы
- `name` (string, required) - Название
- `leader_id` (int, required) - ID лидера
- `members` (array, required) - Массив ID участников
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### PartyUpdate

Схема для обновления группы.

```json
{
  "name": "New Name"
}
```

**Поля:**
- `name` (string, optional) - Название

---

### PartyMemberAdd

Схема для добавления участника в группу.

```json
{
  "player_id": 456
}
```

**Поля:**
- `player_id` (int, required) - ID игрока

---

## Mailing (Объявления и уведомления)

### AnnouncementCreate

Схема для создания объявления.

```json
{
  "title": "Server Maintenance",
  "content": "Server will be under maintenance tomorrow",
  "target_audience": "all",
  "priority": 10,
  "scheduled_at": "2024-01-01T12:00:00",
  "expires_at": "2024-01-02T12:00:00"
}
```

**Поля:**
- `title` (string, required) - Заголовок
- `content` (string, required) - Содержание
- `target_audience` (enum, optional, default: "all") - Аудитория: `all`, `premium`, `regular`, `new`
- `priority` (int, optional, default: 0) - Приоритет
- `scheduled_at` (datetime, optional) - Дата публикации
- `expires_at` (datetime, optional) - Дата истечения

---

### AnnouncementResponse

Ответ с данными объявления.

```json
{
  "id": 1,
  "title": "Server Maintenance",
  "content": "Server will be under maintenance tomorrow",
  "author_id": 1,
  "is_active": true,
  "priority": 10,
  "target_audience": "all",
  "scheduled_at": "2024-01-01T12:00:00",
  "expires_at": "2024-01-02T12:00:00",
  "created_at": "2024-01-01T10:00:00",
  "updated_at": "2024-01-01T10:00:00"
}
```

**Поля:**
- `id` (int, required) - ID объявления
- `title` (string, required) - Заголовок
- `content` (string, required) - Содержание
- `author_id` (int, optional) - ID автора
- `is_active` (bool, required) - Активно ли объявление
- `priority` (int, required) - Приоритет
- `target_audience` (string, required) - Аудитория
- `scheduled_at` (datetime, optional) - Дата публикации
- `expires_at` (datetime, optional) - Дата истечения
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### AnnouncementUpdate

Схема для обновления объявления.

```json
{
  "title": "Updated Title",
  "content": "Updated content",
  "target_audience": "premium",
  "priority": 20,
  "scheduled_at": "2024-01-02T12:00:00",
  "expires_at": "2024-01-03T12:00:00"
}
```

**Поля:**
- `title` (string, optional) - Заголовок
- `content` (string, optional) - Содержание
- `target_audience` (enum, optional) - Аудитория
- `priority` (int, optional) - Приоритет
- `scheduled_at` (datetime, optional) - Дата публикации
- `expires_at` (datetime, optional) - Дата истечения

---

### NotificationCreate

Схема для создания уведомления.

```json
{
  "title": "New quest available",
  "content": "A new quest has been added to the game",
  "notification_type": "info",
  "player_id": 123
}
```

**Поля:**
- `title` (string, required) - Заголовок
- `content` (string, required) - Содержание
- `notification_type` (enum, optional, default: "info") - Тип: `info`, `warning`, `error`, `success`
- `player_id` (int, optional) - ID игрока (если не указан, отправляется текущему игроку)

---

### NotificationResponse

Ответ с данными уведомления.

```json
{
  "id": 1,
  "player_id": 123,
  "title": "New quest available",
  "content": "A new quest has been added to the game",
  "notification_type": "info",
  "is_read": false,
  "read_at": null,
  "created_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID уведомления
- `player_id` (int, optional) - ID игрока
- `title` (string, required) - Заголовок
- `content` (string, required) - Содержание
- `notification_type` (string, required) - Тип уведомления
- `is_read` (bool, required) - Прочитано ли
- `read_at` (datetime, optional) - Дата прочтения
- `created_at` (datetime, required) - Дата создания

---

### NotificationBulkCreate

Схема для массовой отправки уведомлений.

```json
{
  "title": "New quest available",
  "content": "A new quest has been added",
  "notification_type": "info",
  "player_ids": [1, 2, 3]
}
```

**Поля:**
- `title` (string, required) - Заголовок
- `content` (string, required) - Содержание
- `notification_type` (enum, optional, default: "info") - Тип уведомления
- `player_ids` (array, optional) - Массив ID игроков (если `null` или не указан, отправляется всем)

---

## Market (Рынок)

### MarketItemCreate

Схема для создания товара на рынке.

```json
{
  "item_id": "minecraft:diamond",
  "name": "Diamond",
  "desc": "High quality diamond",
  "total_price": 1000,
  "quantity": 5
}
```

**Поля:**
- `item_id` (string, required) - ID предмета (Minecraft item ID)
- `name` (string, required) - Название
- `desc` (string, required) - Описание
- `total_price` (int, required) - Общая цена
- `quantity` (int, required) - Количество

---

### MarketItemResponse

Ответ с данными товара на рынке.

```json
{
  "id": 1,
  "item_id": "minecraft:diamond",
  "seller_id": 123,
  "name": "Diamond",
  "desc": "High quality diamond",
  "total_price": 1000,
  "quantity": 5
}
```

**Поля:**
- `id` (int, required) - ID товара
- `item_id` (string, required) - ID предмета
- `seller_id` (int, required) - ID продавца
- `name` (string, required) - Название
- `desc` (string, required) - Описание
- `total_price` (int, required) - Общая цена
- `quantity` (int, required) - Количество

---

### MarketItemUpdate

Схема для обновления товара на рынке.

```json
{
  "name": "Updated Name",
  "desc": "Updated description",
  "total_price": 1500,
  "quantity": 10
}
```

**Поля:**
- `name` (string, optional) - Название
- `desc` (string, optional) - Описание
- `total_price` (int, optional) - Общая цена
- `quantity` (int, optional) - Количество

---

## Shops (Магазины)

### ShopCreate

Схема для создания магазина.

```json
{
  "name": "My Shop"
}
```

**Поля:**
- `name` (string, optional, default: "Shop") - Название магазина

---

### ShopResponse

Ответ с данными магазина.

```json
{
  "id": 1,
  "owner_id": 123,
  "name": "My Shop"
}
```

**Поля:**
- `id` (int, required) - ID магазина
- `owner_id` (int, required) - ID владельца
- `name` (string, required) - Название

---

### ShopUpdate

Схема для обновления магазина.

```json
{
  "name": "New Shop Name"
}
```

**Поля:**
- `name` (string, optional) - Название

---

### ShopItemCreate

Схема для создания товара в магазине.

```json
{
  "item_id": "minecraft:diamond",
  "price": 100,
  "quantity": 10
}
```

**Поля:**
- `item_id` (string, required) - ID предмета
- `price` (int, required) - Цена за единицу
- `quantity` (int, required) - Количество

---

### ShopItemResponse

Ответ с данными товара в магазине.

```json
{
  "id": 1,
  "item_id": "minecraft:diamond",
  "shop_id": 1,
  "price": 100,
  "quantity": 10
}
```

**Поля:**
- `id` (int, required) - ID товара
- `item_id` (string, required) - ID предмета
- `shop_id` (int, required) - ID магазина
- `price` (int, required) - Цена
- `quantity` (int, required) - Количество

---

### ShopItemUpdate

Схема для обновления товара в магазине.

```json
{
  "price": 150,
  "quantity": 20
}
```

**Поля:**
- `price` (int, optional) - Цена
- `quantity` (int, optional) - Количество

---

### ShopWithItemsResponse

Ответ с данными магазина и его товарами.

```json
{
  "id": 1,
  "owner_id": 123,
  "name": "My Shop",
  "items": [
    {
      "id": 1,
      "item_id": "minecraft:diamond",
      "shop_id": 1,
      "price": 100,
      "quantity": 10
    }
  ]
}
```

**Поля:**
- Все поля из `ShopResponse`
- `items` (array, required) - Массив товаров (`ShopItemResponse`)

---

## QOL (Quality of Life)

### ScenarioCreate

Схема для создания сценария.

```json
{
  "name": "first_join_welcome",
  "scenario_type": "first_join",
  "description": "Welcome scenario for new players",
  "arguments": {
    "required": [["player_name", "str"]],
    "optional": []
  },
  "command_stack": [
    {
      "send_message": ["Welcome ${player_name}!"]
    }
  ]
}
```

**Поля:**
- `name` (string, required) - Название сценария
- `scenario_type` (enum, required) - Тип: `first_join`, `quest_complete`, `level_up`, `custom`
- `description` (string, optional, default: "") - Описание
- `arguments` (object, optional) - Аргументы сценария (JSON)
- `command_stack` (array, optional) - Стек команд для выполнения (JSON)

---

### ScenarioResponse

Ответ с данными сценария.

```json
{
  "id": 1,
  "name": "first_join_welcome",
  "description": "Welcome scenario for new players",
  "arguments": {
    "required": [["player_name", "str"]],
    "optional": []
  },
  "command_stack": [
    {
      "send_message": ["Welcome ${player_name}!"]
    }
  ],
  "scenario_type": "first_join",
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID сценария
- `name` (string, required) - Название
- `description` (string, required) - Описание
- `arguments` (object, required) - Аргументы
- `command_stack` (array, required) - Стек команд
- `scenario_type` (string, required) - Тип сценария
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### ScenarioUpdate

Схема для обновления сценария.

```json
{
  "name": "new_name",
  "description": "New description",
  "scenario_type": "custom",
  "arguments": {},
  "command_stack": []
}
```

**Поля:**
- `name` (string, optional) - Название
- `description` (string, optional) - Описание
- `scenario_type` (enum, optional) - Тип
- `arguments` (object, optional) - Аргументы
- `command_stack` (array, optional) - Стек команд

---

### ScenarioExecute

Схема для выполнения сценария.

```json
{
  "args": {
    "player_name": "Player123"
  }
}
```

**Поля:**
- `args` (object, required) - Аргументы для выполнения сценария

---

### NPCCreate

Схема для создания NPC.

```json
{
  "name": "Merchant",
  "description": "A friendly merchant",
  "locate": ["overworld", 100, 64, 200],
  "dialog": {
    "greeting": "Hello, traveler!",
    "farewell": "Goodbye!"
  },
  "skin": "https://example.com/skin.png"
}
```

**Поля:**
- `name` (string, required) - Имя NPC
- `description` (string, optional, default: "") - Описание
- `locate` (array, optional) - Локация: `[dimension, x, y, z]`
- `dialog` (object, optional) - Диалоги (ключ: ID сообщения, значение: текст)
- `skin` (string, optional, default: "") - URL скина

---

### NPCResponse

Ответ с данными NPC.

```json
{
  "id": 1,
  "name": "Merchant",
  "description": "A friendly merchant",
  "locate": ["overworld", 100, 64, 200],
  "dialog": {
    "greeting": "Hello, traveler!",
    "farewell": "Goodbye!"
  },
  "skin": "https://example.com/skin.png",
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID NPC
- `name` (string, required) - Имя
- `description` (string, required) - Описание
- `locate` (array, required) - Локация
- `dialog` (object, required) - Диалоги
- `skin` (string, required) - URL скина
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### NPCUpdate

Схема для обновления NPC.

```json
{
  "name": "New Name",
  "description": "New description",
  "locate": ["nether", 0, 64, 0],
  "dialog": {
    "greeting": "New greeting"
  },
  "skin": "https://example.com/new_skin.png"
}
```

**Поля:**
- `name` (string, optional) - Имя
- `description` (string, optional) - Описание
- `locate` (array, optional) - Локация
- `dialog` (object, optional) - Диалоги
- `skin` (string, optional) - URL скина

---

### NPCLocationSearch

Схема для поиска NPC по локации.

```json
{
  "dim": "overworld",
  "x": 100.0,
  "y": 64.0,
  "z": 200.0,
  "radius": 10.0
}
```

**Поля:**
- `dim` (string, required) - Измерение
- `x` (float, required) - Координата X
- `y` (float, required) - Координата Y
- `z` (float, required) - Координата Z
- `radius` (float, optional, default: 10.0, max: 100.0) - Радиус поиска

---

### CraftCreate

Схема для создания крафта.

```json
{
  "icon": "https://example.com/icon.png",
  "name": "Diamond Sword",
  "description": "A powerful sword",
  "recipe": [
    {
      "item_id": "minecraft:diamond",
      "quantity": 2
    },
    {
      "item_id": "minecraft:stick",
      "quantity": 1
    }
  ]
}
```

**Поля:**
- `icon` (string, required) - URL иконки
- `name` (string, required) - Название
- `description` (string, optional, default: "") - Описание
- `recipe` (array, optional) - Рецепт (массив объектов с `item_id` и `quantity`)

---

### CraftResponse

Ответ с данными крафта.

```json
{
  "id": 1,
  "icon": "https://example.com/icon.png",
  "name": "Diamond Sword",
  "description": "A powerful sword",
  "recipe": [
    {
      "item_id": "minecraft:diamond",
      "quantity": 2
    },
    {
      "item_id": "minecraft:stick",
      "quantity": 1
    }
  ],
  "created_at": "2024-01-01T12:00:00",
  "updated_at": "2024-01-01T12:00:00"
}
```

**Поля:**
- `id` (int, required) - ID крафта
- `icon` (string, required) - URL иконки
- `name` (string, required) - Название
- `description` (string, required) - Описание
- `recipe` (array, required) - Рецепт
- `created_at` (datetime, required) - Дата создания
- `updated_at` (datetime, required) - Дата обновления

---

### CraftUpdate

Схема для обновления крафта.

```json
{
  "icon": "https://example.com/new_icon.png",
  "name": "New Name",
  "description": "New description",
  "recipe": []
}
```

**Поля:**
- `icon` (string, optional) - URL иконки
- `name` (string, optional) - Название
- `description` (string, optional) - Описание
- `recipe` (array, optional) - Рецепт

---

## Enums (Перечисления)

### PlayerGender

Пол игрока:
- `male` - Мужской
- `female` - Женский
- `other` - Другой

### PlayerRoles

Роли игрока:
- `guest` - Гость
- `regular` - Обычный игрок
- `premium` - Премиум игрок
- `admin` - Администратор
- `owner` - Владелец

### PlayerWorldRating

Рейтинг мира игрока:
- `wood` - Дерево
- `stone` - Камень
- `iron` - Железо
- `gold` - Золото
- `diamond` - Алмаз
- `netherite` - Незерит

### EraStamp

Эра игрока:
- `alpha` - Альфа
- `beta` - Бета
- `release` - Релиз

### TransactionType

Тип транзакции:
- `deposit` - Пополнение
- `withdrawal` - Вывод
- `transfer` - Перевод
- `reward` - Награда
- `payment` - Платеж
- `refund` - Возврат

### QuestType

Тип квеста:
- `system` - Системный
- `player` - От игрока

### QuestStatus

Статус квеста:
- `active` - Активен
- `completed` - Завершен
- `cancelled` - Отменен

### FriendStatus

Статус друга:
- `pending` - Ожидает подтверждения
- `accepted` - Принят
- `rejected` - Отклонен
- `blocked` - Заблокирован

### ClanRole

Роль в клане:
- `leader` - Лидер
- `moderator` - Модератор
- `member` - Участник

### TargetAudience

Целевая аудитория:
- `all` - Все
- `premium` - Премиум
- `regular` - Обычные
- `new` - Новые игроки

### NotificationType

Тип уведомления:
- `info` - Информация
- `warning` - Предупреждение
- `error` - Ошибка
- `success` - Успех

### ScenarioType

Тип сценария:
- `first_join` - Первый вход
- `quest_complete` - Завершение квеста
- `level_up` - Повышение уровня
- `custom` - Пользовательский

---

## Примечания

1. Все даты и время передаются в формате ISO 8601: `YYYY-MM-DDTHH:MM:SS`
2. Все опциональные поля могут быть `null` в JSON
3. Все enum значения передаются как строки в нижнем регистре
4. Все ID являются целыми числами (int)
5. Все суммы денег передаются как целые числа (int) в минимальных единицах валюты
