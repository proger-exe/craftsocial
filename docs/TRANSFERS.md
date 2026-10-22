# Переводы вайш между игроками

Документация описывает новую систему переводов между игроками через отдельный endpoint.

---

## Endpoint

```http
POST /user/transactions/transfer
```

## Аутентификация

Обязателен заголовок:

```http
X-Player-ID: <id_отправителя>
```

Отправитель всегда берется из авторизованного игрока (`X-Player-ID`), подменять `from_player_id` нельзя.

---

## Тело запроса

Схема: `TransferCreate`

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `to_player_id` | int | да | ID получателя |
| `amount` | int | да | Сумма перевода (`> 0`) |
| `description` | string | нет | Комментарий к переводу |

Пример:

```json
{
  "to_player_id": 2,
  "amount": 500,
  "description": "Guild support"
}
```

---

## Успешный ответ

Код: `201 Created`

Схема: `TransferResponse`

| Поле | Тип | Описание |
|------|-----|----------|
| `transaction_id` | int | ID созданной транзакции |
| `from_player_id` | int | ID отправителя |
| `to_player_id` | int | ID получателя |
| `amount` | int | Сумма перевода |
| `from_balance_after` | int | Баланс отправителя после перевода |
| `to_balance_after` | int | Баланс получателя после перевода |
| `created_at` | datetime | Время создания транзакции |

Пример:

```json
{
  "transaction_id": 12345,
  "from_player_id": 1,
  "to_player_id": 2,
  "amount": 500,
  "from_balance_after": 1500,
  "to_balance_after": 2400,
  "created_at": "2026-02-02T18:25:41.120000+00:00"
}
```

---

## Бизнес-правила

1. Перевод самому себе запрещен.
2. Сумма должна быть строго больше 0.
3. Получатель должен существовать как игрок.
4. У получателя должен существовать профиль игрока.
5. У отправителя должно хватать баланса.

---

## Коды ошибок

| Код | Когда |
|-----|-------|
| `400` | Нарушение правил перевода (например, self-transfer, amount <= 0) |
| `402` | Недостаточно баланса у отправителя |
| `404` | Получатель не найден или не найден его профиль |
| `401` | Отсутствует/невалиден `X-Player-ID` |
| `500` | Внутренняя ошибка |

---

## Гарантии консистентности (анти-дюп)

Перевод выполняется атомарно в одной DB transaction:

- строки отправителя и получателя блокируются `SELECT ... FOR UPDATE`,
- сначала проверяется баланс и правила,
- затем одновременно:
  - списание у отправителя,
  - зачисление получателю,
  - создание записи в `transactions` (`transaction_type = transfer`).

Если любой шаг падает, весь перевод откатывается.

---

## Интеграция с историей транзакций

Перевод попадает в общую таблицу `transactions` как `TransactionType.TRANSFER` и отображается в существующих endpoints:

- `GET /user/transactions`
- `GET /user/transactions/{transaction_id}`
- `GET /user/transactions/balance-history`

---

## Быстрый пример cURL

```bash
curl -X POST "http://localhost:8000/user/transactions/transfer" \
  -H "Content-Type: application/json" \
  -H "X-Player-ID: 1" \
  -d '{
    "to_player_id": 2,
    "amount": 500,
    "description": "Guild support"
  }'
```
