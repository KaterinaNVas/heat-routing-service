# Локальный запуск и сравнение вариантов

Сервис принимает исходный GeoJSON и возвращает варианты подключения ОКС к тепловой сети. Для расчёта используется Java 11, Spring Boot 2.6.3 и PostgreSQL/PostGIS. Команды ниже запускаются в PowerShell из корня `heat-routing-service`.

## Сборка и запуск

Запустите Docker Desktop, затем базу данных:

```powershell
docker compose up -d db
docker compose ps
```

В `docker compose ps` база `heat-routing-db` должна иметь статус `healthy`. Укажите установленный JDK 11 в текущем окне PowerShell:

```powershell
$jdk11 = Get-ChildItem "C:\Program Files\Amazon Corretto" -Directory |
  Where-Object { $_.Name -like "jdk11*" } | Select-Object -First 1
$env:JAVA_HOME = $jdk11.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Проверьте, что Maven использует Java 11, и соберите приложение с тестами:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -version
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" clean package -f pom.xml
```

Запустите JAR. Если порт 8080 занят, используйте 8081, как в примере:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar ".\target\heat-routing-service-0.0.1-SNAPSHOT.jar" --server.port=8081
```

Оставьте это окно открытым. Во втором окне проверьте:

```powershell
curl.exe -i http://127.0.0.1:8081/api/health
```

Ожидается `HTTP 200` и JSON со статусом `UP`. Для остановки приложения нажмите Ctrl+C в окне с JAR. Адрес Swagger UI при этом запуске: `http://127.0.0.1:8081/swagger-ui.html`.

## Расчёт вариантов

Передайте исправленный исходный GeoJSON как поле формы `file`:

```powershell
$dataset = "C:\Users\User\Downloads\Датасет скорректированный.geojson"
curl.exe -sS -X POST "http://127.0.0.1:8081/api/v1/route/preview-variants" `
  -F "file=@$dataset;type=application/geo+json" `
  -o "preview-variants.geojson" `
  -w "HTTP %{http_code}`n"
```

Ответ `HTTP 200` сохраняется в `preview-variants.geojson` в текущей папке. При некорректных входных данных API возвращает `HTTP 422` с описанием ошибки. Файл результата нужен для просмотра и не должен добавляться в Git.

Выведите сводки вариантов:

```powershell
$result = Get-Content ".\preview-variants.geojson" -Raw -Encoding UTF8 | ConvertFrom-Json
$result.features |
  Where-Object { $_.properties.object_type -eq "variant_summary" } |
  ForEach-Object { $_.properties |
    Select-Object variant_id,route_strategy,rank,score,calculated_cost,new_network_length,unconnected_oks_ids }
```

`SHORTEST` выбирает кратчайшие локальные маршруты; `LOWEST_STANDALONE_COST` выбирает локальные маршруты по предварительной стоимости. После объединения маршрутов каждого варианта сервис рассчитывает общую стоимость, длину и итоговый балл и сортирует варианты по баллу (`rank=1` — лучший). Веса итогового балла: стоимость 0,7, длина 0,3. Одинаковые варианты удаляются, поэтому в ответе может быть один или два варианта.

Для исправленного тестового датасета получены два варианта, каждый с 17 участками `heat_network` и без неподключённых ОКС. Их показатели зависят от версии исходного файла и настроек расчёта; ориентируйтесь на сводку в ответе.

Для прежнего расчёта одного варианта используется `POST /api/v1/route/preview-all` с тем же полем `file`.

## Просмотр в QGIS

GeoJSON содержит объекты с `properties.object_type`: `heat_network`, `heat_chamber` и `variant_summary`. Поле `properties.variant_id` разделяет варианты `v1` и `v2`. Откройте результат в QGIS и отфильтруйте линии `heat_network` по `variant_id`; задайте вариантам разные цвета. Исходные здания и существующую сеть откройте отдельными слоями из входного датасета.
