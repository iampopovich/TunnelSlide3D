# TunnelSlide3D

Открытая рекреация тоннельной аркады SpeedX 3D на libGDX и Kotlin.

SpeedX 3D (2010) была одной из лучших аркад на реакцию для Android, но её давно забросили,
а последние сборки антивирусы помечают как вредоносные из-за рекламных SDK. TunnelSlide3D
повторяет её правила и ощущения заново, с чистого листа: без рекламы, без трекеров, с открытым
кодом. Код и ресурсы оригинала не используются — спецификация собрана из публичных описаний
и видео геймплея ([docs/SPEC.md](docs/SPEC.md)).

> Статус: ранняя разработка. Уже можно ехать по тоннелю и уворачиваться от блоков; бонусов,
> опасных зон и морфинга пока нет. Прогресс — в [docs/PLAN.md](docs/PLAN.md),
> подробный журнал с шагами для повторения — в [docs/DEVLOG.md](docs/DEVLOG.md).

---

## Для разработчиков

```
./gradlew :lwjgl3:run              # запуск на ПК
./gradlew :android:assembleDebug   # debug APK
./gradlew :android:installDebug    # установка на телефон через adb
./gradlew :core:test               # тесты логики
```

Нужны JDK 17+ и Android SDK (путь в `local.properties`: `sdk.dir=...`).

Модули: `core` — вся игра; `android` и `lwjgl3` — тонкие лаунчеры.
Архитектурные правила — в [docs/PLAN.md](docs/PLAN.md#архитектурные-правила).

## Лицензия

GPL-3.0-or-later, см. [LICENSE](LICENSE). Любой форк обязан оставаться открытым — так чужая
сборка с рекламой и закрытым кодом не сможет выдать себя за этот проект.

Шрифт [Orbitron](https://github.com/theleagueof/orbitron) — SIL Open Font License 1.1,
текст лицензии в [assets/fonts/OFL.txt](assets/fonts/OFL.txt).
