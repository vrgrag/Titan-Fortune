# Titan Fortune (Native Kotlin + Jetpack Compose)

Офлайн action-roguelite с top-down камерой 90°. Игра полностью перенесена с LibGDX на Native Android: Kotlin, Jetpack Compose и аппаратно-ускоренный Canvas.

## Три версии (product flavors)

| Flavor | ApplicationId | Что внутри |
|---|---|---|
| **v1Simple** | `com.titanfortune.game.simple` | Splash, премиальное меню, Canvas-арена, Sapphire, волны, коллизии, пауза, победа/поражение |
| **v2Standard** | `com.titanfortune.game.standard` | + 5 самоцветов, заряд/overcharge, dash, экспедиции, элиты и Титан |
| **v3Complete** | `com.titanfortune.game` | + мета-прогрессия, арсенал, боги, коллекция Титанов, Zeus power, Privacy/Support |

## Подпись (keystore)

Файл: `android/keystore/titan-fortune-release.jks` (JKS)

- Alias: `titanfortune`
- Store/Key password: `TitanFortune#Release26`
- Алгоритм: RSA 2048, срок ~10000 дней
- SHA256: `D4:CF:65:BD:48:AF:CC:FD:4A:91:AF:7E:42:58:8F:E8:C8:B3:59:17:DB:CD:21:ED:3E:A9:C9:A8:93:C7:19:49`

Пароли также в `android/keystore/key.properties` (файл в `.gitignore`). Release-сборки подписываются этим ключом автоматически.

```bat
gradlew.bat :android:assembleV3CompleteRelease
```

**Важно:** без этого JKS нельзя обновлять приложение в Google Play. Сделайте резервную копию.

## Сборка

Нужны **JDK 17** и **Android SDK**.

Путь к JDK задан в `gradle.properties` (`org.gradle.java.home`), поэтому `JAVA_HOME` выставлять не обязательно. На другой машине поменяйте это значение. В Android Studio: Settings → Build Tools → Gradle → Gradle JDK = 17.

```bat
gradlew.bat :android:assembleV1SimpleDebug
gradlew.bat :android:assembleV2StandardDebug
gradlew.bat :android:assembleV3CompleteDebug
```

APK: `android/build/outputs/apk/<flavor>/debug/`

Установка:

```bat
gradlew.bat :android:installV1SimpleDebug
gradlew.bat :android:installV2StandardDebug
gradlew.bat :android:installV3CompleteDebug
```

## Ассеты

Исходники: `assets/`. Нарезка и PNG: `tools/preprocess_assets.py` → `android/assets/`.

Спрайт-листов кадровой анимации в исходниках **нет**. Статичные композиты автоматически нарезаны; движение, орбита, ауры, молнии и частицы анимируются средствами Compose Canvas.

## Зависимости

- Jetpack Compose BOM `2024.02.02`
- Material 3
- AndroidX Activity Compose
- Kotlin `1.9.22`
- Android Gradle Plugin `8.2.2`
- minSdk 24, compileSdk 34

INTERNET в манифесте **нет** (офлайн). Privacy/Support — локальный текст до появления URL.

## Управление

- Левый стик — движение
- Правый стик (v2+) — прицел / выбор самоцвета, отпускание с сильным отклонением — разряд
- DASH / DISCHARGE / переключение самоцветов — кнопки HUD
