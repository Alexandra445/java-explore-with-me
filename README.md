# java-explore-with-me

Template repository for ExploreWithMe project.

## Состав проекта

- `ewm-main-service` — основной сервис (будет реализован на следующем этапе).
- `ewm-stats` — сервис статистики:
    - `stats-dto` — общие DTO;
    - `stats-server` — HTTP-сервис статистики (порт 9090);
    - `stats-client` — HTTP-клиент для сервиса статистики.

## Запуск

```
docker compose up --build
```

Поднимет сервис статистики и его базу данных (PostgreSQL).

## Дополнительная функциональность

Комментарии: возможность оставлять комментарии к событиям и модерировать их.