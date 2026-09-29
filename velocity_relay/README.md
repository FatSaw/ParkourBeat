# ParkourBeatPackRelay

Плагин для Velocity. Делает доставку музыкальных ресурспаков AMusic предсказуемой.

## Сборка

    mvn -f velocity-relay/pom.xml package

Готовый `target/ParkourBeatPackRelay.jar` кладётся в `plugins/` **прокси**, не игрового сервера.

## Что делает

1. Пересылает статус ресурспака на бэкенд по каналу `parkourbeat:packstatus`.
   Velocity этот статус бэкенду не отдаёт, поэтому без релея плагин всегда видит NO_REPLY.
2. Повторяет отправку пака при FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED.
3. Сторожевой таймер: если клиент молчит дольше `stuck-timeout-millis`, пак отправляется заново.
4. Снимает ограничение strictaccess у AMusic через рефлексию.
   Без этого HTTP-скачивание с адреса, отличного от адреса входа (IPv6 против IPv4,
   мобильный NAT, VPN), получает 403 Forbidden и клиент рапортует FAILED.
5. Снимает блокировку waitacception: отдача файла больше не ждёт статуса ACCEPTED.
6. При DECLINED пишет игроку, как включить ресурспаки в настройках сервера.

## Команда

    /packrelay                 состояние патчей
    /packrelay player <ник>    последний статус и текущее ожидание
    /packrelay repatch         применить патчи заново

Право: `parkourbeat.packrelay`

## Конфиг

`plugins/parkourbeatpackrelay/config.properties` создаётся при первом запуске.
