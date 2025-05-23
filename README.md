# Maniac Game

## Что нужно установить для работы:
### Для Clojure (сервер):
Установите Java JDK (минимум версия 8, лучше 11+).
Установите Leiningen (инструмент для сборки Clojure):
→ https://leiningen.org/

Проверьте установку в cmd:
```
lein --version
```

### Для Prolog (клиент):
Установите SWI-Prolog (лучший выбор для работы с сокетами):
→ https://www.swi-prolog.org/
Проверьте установку в cmd:
```
swipl --version
```

### Установка зависимостей
Перейдите в папку сервера:
```
cd server
```
Установите зависимости (автоматически скачает библиотеки из project.clj):
```
lein deps
```

Если возникают ошибки - Попробуйте очистить кэш
```
lein clean
lein deps
```

## Структура проекта
- `server/` - серверная часть (Clojure)
- `client/` - клиентская часть (SWI-Prolog)

## Запуск
Заходите в cmd через cd в папку с проектом   
Далее запускаем сервер
### Сервер
```bash
cd server
lein run
```
Далее запускаем еще одну cmd и запускаем клиента
### Клиент
```bash
swipl client/client.pl
```

## Управление
- WASD - движение
- Q - выход
