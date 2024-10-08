# Информация о плагине

### Зависимости плагина:

- Paper 1.16.5+ (более новые версии не протестированы, но, вероятно, работают тоже)
- [AMusic 0.13](https://spigotmc.org/resources/108835)

# Сборка, тестирование, развертывание

### Ручное тестирование плагина:

- Склонируйте проект
- Выполните `./gradlew runServer`
- Зайдите на сервер по адресу `localhost:25565`

### Сборка плагина:

- Склонируйте проект
- Выполните `./gradlew build`
- Собранный плагин будет расположен в `build/libs/ParkourBeat-<версия>-all.jar`

### Развертывание плагина на ваш сервер:

- Убедитесь, что AMusic установлен
- Создайте мир "pb_lobby" при помощи [MultiVerse](https://spigotmc.org/resources/390) или просто скопируйте папку на
  сервер и назовите её "pb_lobby"
- Создайте мир "pb_default_level" при помощи [MultiVerse](https://spigotmc.org/resources/390) или просто скопируйте
  папку на сервер и назовите её "pb_default_level"
- Собрать плагин
- Скопируйте `ParkourBeat-<version>-all.jar` в папку `plugins`
- Выполните рестарт сервера, чтобы загрузить плагин
- Отредактируйте все конфиги в директории `plugins/ParkourBeat`
- Выполните рестарт сервера, чтобы применить новые конфигурации

# Информация о коллаборации

### Discord-сервер
https://discord.gg/eMbPn7nAUD
