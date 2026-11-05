# 🛴 Martı Takip

Saha operatörleri için **sıfır maliyetli**, çevrimdışı öncelikli batarya değişim takip uygulaması.

Kotlin + Jetpack Compose + Material 3 · Room (SQLite) · OpenStreetMap · Tek seferlik GPS

---

## ✨ Ne yapar?

| Özellik | Davranış |
|---|---|
| **Dev sayaçlar** | Scooter ve Moped için iki büyük dokunmatik kart. Geniş `+1`, yanında `-1` geri alma. |
| **Canlı kazanç** | `(Scooter × Fiyat) + (Moped × Fiyat)` her dokunuşta anında güncellenir. |
| **Haptik onay** | Her `+1`/`-1` dokunuşunda titreşim (ayarlardan kapatılabilir). |
| **Olay bazlı GPS** | `+1`'e basıldığı **an** arka planda tek seferlik konum alınır. GPS gelmezse kayıt yine de yazılır, koordinat `null` kalır. Arka plan servisi **yoktur** — pil tüketmez. |
| **Ayarlar** | Sağ üstteki dişli ikonu: Scooter fiyatı, Moped fiyatı, günlük hedef, haptik, otomatik konum ve ses anahtarları. Alt panel olarak açılır. |
| **Vardiya yönetimi** | "Vardiyayı Kaydet & Sıfırla" o anki tüm kayıtları atomik olarak arşive alır ve sayaçları sıfırlar. |
| **Paylaşım** | Günlük özet WhatsApp / Telegram / herhangi bir uygulamaya `ACTION_SEND` Intent'i ile gider. |
| **Isı haritası** | OpenStreetMap üzerinde Scooter = 🟢 yeşil, Moped = 🟠 turuncu yarı saydam daireler. Zaman aralığı filtresi parçalı düğme (Bugün / 7 / 30 / Tümü). |
| **Arşiv** | Vardiya geçmişi alt sayfası; her vardiya için süre, adet, kazanç ve paylaş butonu. |
| **CSV dışa aktarım** | Tüm kayıtlar tek dokunuşla CSV olarak paylaşılır (cihazda yedek). Arşiv büyüdüğü için metin gövdesi yerine `FileProvider` üzerinden **dosya eki** olarak gönderilir. |
| **Saatlik hız** | Değişim/saat, kazanç/saat, dk/değişim ve 08–22 saat şeridi. Hız, vardiyanın *aktif* süresi üzerinden hesaplanır. |
| **Bölge kırılımı** | Değişimler yakınlık tabanlı çevrimdışı bölge çözümlemesiyle gruplanır (Kadıköy, Beşiktaş…). Ağ gerekmez. |
| **Trend** | Son 14 günün günlük değişim grafiği + düne göre fark. |
| **Motivasyon** | İlerlemeye göre değişen kısa teşvik mesajı. |
| **Sesli onay** | Her kayıtta kısa bip (ayarlardan kapatılabilir). |
| **Açılış ekranı** | Android 12 `SplashScreen` API'si (`core-splashscreen`), API 26'ya kadar geri taşınmış. Soğuk başlangıçta marka rengi + uygulama ikonu; ilk Compose karesine kadar boş beyaz kare yok. |
| **Uyarlanabilir arayüz** | Navigasyon bileşeni ve kart yerleşimi pencere genişliğine göre kendini seçer: telefonda alt çubuk + tek kolon, tablette yan ray + yan yana sayaçlar, masaüstü penceresinde çekmece. |
| **Arşivden geri alma** | Yanlışlıkla kaydedilen vardiya tek dokunuşla aktife döner. Yalnızca **bugünün** vardiyası geri alınabilir; eski günler geçmişte kalır. |
| **Günü sil** | Arşivlemeden silme; onay diyaloğu ile korunur. |

---

## 🏗️ Mimari

```
com.martitakip.app
├── MartiTakipApplication.kt      # osmdroid User-Agent + DB warm-up
├── MainActivity.kt               # Tek activity, izin akışı, Intent'ler, snackbar'lar
├── data/
│   ├── local/
│   │   ├── SwapEvent.kt          # @Entity + VehicleType enum
│   │   ├── SwapDao.kt            # Flow tabanlı reaktif sorgular
│   │   └── AppDatabase.kt        # Room singleton + TypeConverter
│   ├── repository/
│   │   └── SwapRepository.kt     # Gün anahtarı politikası + arşiv işlemleri
│   └── settings/
│       └── SettingsRepository.kt # DataStore (fiyatlar, hedef, anahtarlar)
├── location/
│   └── LocationHelper.kt         # Tek seferlik FusedLocation + cache fallback
├── util/
│   ├── RegionResolver.kt         # Çevrimdışı bölge çözümleme (28 merkez)
│   └── SoundFeedback.kt          # SoundPool tabanlı kısa onay tonu
└── ui/
    ├── TrackerViewModel.kt       # Tüm state (StateFlow) + olaylar (SharedFlow)
    ├── components/
    │   └── CommonComponents.kt   # Sayaç kartı, hedef çubuğu, GPS çipi
    ├── screens/
    │   ├── MainTrackerScreen.kt
    │   ├── OsmMapScreen.kt
    │   ├── SettingsDialog.kt
    │   ├── ShiftHistorySheet.kt
    │   └── TrackerAppShell.kt
    └── theme/                    # Material 3 renk şeması + tipografi
```

**Tek yönlü veri akışı:** UI → niyet (lambda) → ViewModel → Repository → Room → `Flow` → UI.
Ekranda hiçbir yerde elle "yenile" yoktur; her yazma otomatik olarak yeniden çizim tetikler.

### Neden bu tasarım kararları?

**1. Kazanç, satır bazında saklanır.**
`SwapEvent.earnings`, dokunma anındaki birim fiyatın **anlık görüntüsüdür**. Ayarlardan fiyatı
değiştirmek geçmiş kayıtları asla yeniden hesaplamaz. Günlük toplam, SQLite tarafında
`SUM(CASE WHEN type = 'SCOOTER' THEN earnings ELSE 0 END)` ile toplanır.

**2. Arşiv "taşıma" değil "damgalama"dır.**
"Vardiyayı Kaydet & Sıfırla" satırları başka tabloya kopyalamaz; `shiftId` alanını tek bir
`UPDATE` ile damgalar. Canlı sayaçlar `WHERE shiftId IS NULL` ile filtrelenir. Sonuç:
sıfırlama anında ve atomiktir, veri kaybı imkânsızdır ve **ısı haritası arşivlenmiş noktaları
da göstermeye devam eder**.

**3. `+1` asla beklemez.**
Dokunuş önce satırı `null` koordinatla yazar (milisaniyeler), sonra GPS arka planda alınıp
aynı satıra `UPDATE` ile iliştirilir. Konum gecikirse ya da hiç gelmezse sayaç ve kazanç
çoktan güncellenmiştir.

**4. Isı haritası tek geçişte çizilir.**
Binlerce `Marker` nesnesi yerine tek bir `Overlay`, tüm noktaları zoom'a göre boyutlanan
ızgara hücrelerine toplayıp tek canvas geçişinde çizer. Eski/ucuz telefonlarda akıcı kalır.

**5. GPS pili tüketmez.**
`requestLocationUpdates` yok, foreground service yok, wake lock yok. Sadece:
önbellek taze ise (< 2 dk) anında kullan → değilse tek `CurrentLocationRequest`
(6 sn sert zaman aşımı, `CancellationTokenSource` ile iptal edilebilir) → son çare eski önbellek.

**6. Geri alma yalnızca bugünün vardiyasını kapsar.**
Canlı sayaçlar `dayKey = bugün AND shiftId IS NULL` ile okunur. Eski bir vardiyanın
damgası silinirse satırları ne bugünün sayacına girer ne de geçmiş listesinde kalır —
uygulamada hiçbir yerde görünmez olur. Bu yüzden gün filtresi `unarchiveShift` SQL'inin
içindedir, çağıranın kontrolüne bırakılmamıştır; eski günler için buton kapalıdır.

**7. Navigasyon pencere boyutuna göre kendini seçer.**
İskelet elle yazılmış bir `NavigationBar` değil, `material3-adaptive-navigation-suite`
içindeki `NavigationSuiteScaffold`. Telefonda alt çubuk, tablet/kabarık ekranda yan ray,
masaüstü penceresinde çekmece çizilir; seçim pencere boyut sınıfından gelir, uygulama
kodundan değil. İçerik de aynı eşiğe uyar: sayaç kartları 600 dp'nin üzerinde yan yana,
altında alt alta. Animasyon süreleri elle seçilmiş `tween` değerleri yerine
`MaterialTheme.motionScheme` jetonlarından gelir; böylece arayüz Material
bileşenleriyle aynı ritimde hareket eder.

**8. Açılış ekranı bir tema ve bir çağrıdır.**
Soğuk başlangıçta gösterilen pencere `Theme.MartiTakip.Splash`'tır; `Theme.SplashScreen`
üzerine kurulur ve `postSplashScreenTheme` ile hemen `Theme.MartiTakip`e döner.
`installSplashScreen()` `super.onCreate()`'dan **önce** çağrılır — tema değişimi o anda
yapılır. API 31 ve üzerinde pencereyi sistem çizer, altında kütüphane; iki yol da aynı
görünür, çünkü ikisi de aynı `windowSplashScreen*` niteliklerini okur. Tema yalnızca
`values/` içindedir: ikon ve marka rengi karanlıkta da aynı olduğu için gece varyantı
kopya olurdu, fark değil.

**9. Ekran görüntüsü testleri Gradle eklentisi olmadan çalışır; karşılaştırmayı testin kendisi yapar.**
Roborazzi'nin kendi eklentisi `recordRoborazzi*` / `verifyRoborazzi*` görevlerini ekler, ama
o görevler referans görüntü yokken kırmızı olur — yani test altyapısı, korumak istediği
derlemeyi kırar. Eklenti bu yüzden yok: PNG üretimi sıradan `testDebugUnitTest` çalışmasının
yan ürünü. Karşılaştırma `ScreenshotReference.kt` içinde `ImageIO` ile yapılır. Kazancı üç
tanedir: tolerans (kanal başına 8/255, kare başına %0.1 piksel) depoda okunabilir bir yerde
durur, Roborazzi'nin dizin/özellik varsayılanlarına hiç bağlı kalınmaz ve **eksik referans
asla derlemeyi kıramaz** — referansı olmayan kare yalnızca kaydedilir ve konsola not düşer.
Referanslar `app/src/test/screenshots/` altında commit'lenir.

---

## 🚀 Kurulum (yerel derleme gerekmez)

### Yol A — GitHub Actions ile APK (önerilen, 0 maliyet)

1. Repoyu GitHub'a push'la (`main` veya `master` dalı).
2. **Actions → Build APK** workflow'u otomatik çalışır.
3. Çalışma bitince **Artifacts → `marti-takip-apk`** dosyasını indir.
4. APK'yı telefona kopyala, "Bilinmeyen kaynaklara izin ver" deyip kur.

**Sürüm yayınlamak için** (otomatik GitHub Release):

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow APK'yı derler ve `v1.0.0` etiketiyle bir GitHub Release'e ekler.
Release sayfasındaki doğrudan indirme linki paylaşılabilir.

**Manuel tetikleme:** Actions → Build APK → *Run workflow* → `debug` veya `release` seç.

> İmzalı release APK istiyorsan repoya şu 4 secret'ı ekle:
> `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
> Secret yoksa release varyantı imzasız üretilir; **debug APK her zaman imzalıdır** ve kurulabilir.

### Yol B — Android Studio

Android Studio (Meerkat veya üstü) ile aç, Gradle senkronizasyonunu bekle, **Run** ▶.
JDK 17+ ve Android SDK 37 gerekir.

---

## 🔐 İzinler

| İzin | Neden |
|---|---|
| `ACCESS_FINE_LOCATION` | Değişim noktasının hassas koordinatı (ısı haritası için) |
| `ACCESS_COARSE_LOCATION` | Fine reddedilirse yaklaşık konum |
| `INTERNET` | OpenStreetMap döşemeleri |
| `ACCESS_NETWORK_STATE` | Döşeme önbelleği / çevrimdışı algılama |

**Konum izni reddedilirse uygulama tamamen çalışmaya devam eder** — sadece noktalar haritada
görünmez. Uygulama hiçbir veriyi sunucuya göndermez; her şey cihazdaki SQLite dosyasındadır.

---

## 🗺️ OpenStreetMap politikası

`Configuration.getInstance().userAgentValue`, uygulamanın paket adını içeren benzersiz bir
değere ayarlanır (`com.martitakip.app/1.0.0 (Android; saha takip uygulamasi)`). Bu,
[OSM döşeme kullanım politikası](https://operations.osmfoundation.org/policies/tiles/) gereğidir
ve `MartiTakipApplication.onCreate()` içinde, ilk döşeme isteğinden **önce** yapılır.
Döşeme önbelleği disk üzerinde sınırlı tutulur (max 300 MB / trim 250 MB).

---

## 🧪 Testler

```bash
./gradlew :app:testDebugUnitTest
```

20 birim testi şunları doğrular: gün anahtarı hesaplama (gece yarısı sınırı dahil), fiyat
normalizasyonu/yuvarlama, hedef ilerlemesi, araç tipi serileştirme, para/süre biçimlendirme,
bölge çözümleme (Kadıköy eşleşmesi, uzak noktada "Diğer", koordinatsız kayıtlar, Haversine
mesafesi ve gruplama sıralaması).

Aynı komut **ekran görüntüsü testlerini** de çalıştırır. Robolectric Compose ağacını JVM'de
gerçek kaynaklarla çizer, Roborazzi sonucu PNG olarak yazar; görüntüler
`app/build/outputs/roborazzi/` altında oluşur ve CI her push'ta bunları `ui-screenshots`
artifact'i olarak yükler. Yani yerleşimi görmek için telefona kurmak gerekmez. Aynı koşuda her
kare, `app/src/test/screenshots/` altındaki aynı adlı referansla karşılaştırılır.

| Kare | Neyi görünür kılar |
|---|---|
| `counter-scooter` · `counter-moped-empty` | Dev `+1` hedefinin okunurluğu, boş durumda `-1`'in pasif görünmesi |
| `goal-reached` | Hedef çubuğunun tamamlanmış hâli |
| `shell-phone` · `tracker-screen-phone` | Alt çubuğun arkasında kalan içerik, çift durum çubuğu boşluğu |
| `shell-tablet` | 1000 dp'de ray mı çıkıyor, kartlar yan yana mı geliyor |

> **Referans karşılaştırması açık.** Her kare, `app/src/test/screenshots/` altındaki aynı adlı
> referansla karşılaştırılır. Kanal başına 8/255 fark ve kare başına %0.1 piksel tolere edilir;
> **boyut değişimi toleranssızdır**. Kasıtlı bir görsel değişiklik referansları bayat bırakır ve
> unit test kapısını kırar — yani kırmızı sonuç her zaman gerileme demek değildir, "referansı
> yenile" de olabilir. Hata mesajı hem farkı hem de o koşuda çizilen PNG'nin tam yolunu yazar.
>
> Yenilemek için (kareler `build/outputs/roborazzi/` altında yeniden üretilir):
>
> ```bash
> MT_RECORD_SCREENSHOTS=1 ./gradlew :app:testDebugUnitTest
> ```
>
> ```bash
> cp app/build/outputs/roborazzi/*.png app/src/test/screenshots/
> ```
>
> Yerel Gradle yoksa: Actions → Build APK → *Run workflow* → **record_references** işaretle.
> O iş kayıt modunda çizer, kareleri referans klasörüne kopyalar ve commit'i kendisi push eder.
> Referansı henüz olmayan yeni bir kare kırmızı olmaz: yalnızca `build/outputs/roborazzi/`
> altına yazılır ve konsola `no reference for <ad>` düşer.
>
> **`--no-build-cache` şart.** Gradle `testDebugUnitTest` görevini cache'ten geri yüklerse
> (`FROM-CACHE`) test hiç çalışmaz — ve Roborazzi'nin PNG'leri bu görevin bildirilmiş çıktısı
> olmadığı için cache'ten gelmez. Sonuç: yeşil ama boş bir koşu. CI'da bu yüzden test adımı
> `--no-build-cache` ile çağrılır; yerelde de kareleri üretmek için aynısını yap.

---

## 🛠️ Teknik sürümler

| Bileşen | Sürüm |
|---|---|
| Kotlin | 2.4.21 |
| AGP | 9.4.0 |
| Gradle | 9.8.2 |
| Compose BOM | 2026.09.00 |
| Material 3 | 1.5.0-beta01 |
| material3-adaptive-navigation-suite | 1.5.0-beta01 |
| core-splashscreen | 1.2.0 |
| Robolectric | 4.17 |
| Roborazzi | 1.77.0 |
| Room | 2.8.5 (KSP 2.3.13) |
| osmdroid | 6.1.20 |
| play-services-location | 21.4.0 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |

> **AGP 9 notu:** AGP 9 Kotlin'i kendisi derler (*built-in Kotlin*), bu yüzden
> `org.jetbrains.kotlin.android` plugin'i artık uygulanmıyor. AGP 9.4 varsayılan
> olarak KGP 2.2.10'a bağlıdır; Kotlin 2.4.21 ve KSP 2.3.13 sürümleri kök
> `build.gradle.kts` içindeki `buildscript` classpath'inde sabitlenir.
>
> **compileSdk 37 notu:** Compose BOM 2026.09.00 (Compose 1.12.1) derleme için
> en az API 37 ister. `targetSdk` 36'da kalır — bu, çalışma zamanı davranışını
> belirler ve derleme SDK'sından bağımsızdır.

---

## 📄 Lisans

Kişisel saha kullanımı için geliştirilmiştir. OpenStreetMap verisi
[ODbL](https://www.openstreetmap.org/copyright) lisansı altındadır.
