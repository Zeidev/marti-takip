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
| **Ayarlar** | Sağ üstteki dişli ikonu: Scooter fiyatı, Moped fiyatı, günlük hedef, haptik ve otomatik konum anahtarları. |
| **Vardiya yönetimi** | "Vardiyayı Kaydet & Sıfırla" o anki tüm kayıtları atomik olarak arşive alır ve sayaçları sıfırlar. |
| **Paylaşım** | Günlük özet WhatsApp / Telegram / herhangi bir uygulamaya `ACTION_SEND` Intent'i ile gider. |
| **Isı haritası** | OpenStreetMap üzerinde Scooter = 🟢 yeşil, Moped = 🟠 turuncu yarı saydam daireler. Zaman aralığı filtresi (Bugün / 7 / 30 / Tümü). |
| **Arşiv** | Vardiya geçmişi alt sayfası; her vardiya için süre, adet, kazanç ve paylaş butonu. |
| **CSV dışa aktarım** | Tüm kayıtlar tek dokunuşla CSV olarak paylaşılır (cihazda yedek). |

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

Android Studio (Ladybug veya üstü) ile aç, Gradle senkronizasyonunu bekle, **Run** ▶.
JDK 17 ve Android SDK 35 gerekir.

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

14 birim testi şunları doğrular: gün anahtarı hesaplama (gece yarısı sınırı dahil), fiyat
normalizasyonu/yuvarlama, hedef ilerlemesi, araç tipi serileştirme, para/süre biçimlendirme.

---

## 🛠️ Teknik sürümler

| Bileşen | Sürüm |
|---|---|
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |
| Gradle | 8.9 |
| Compose BOM | 2024.12.01 |
| Material 3 | 1.3.1 |
| Room | 2.6.1 (KSP) |
| osmdroid | 6.1.20 |
| play-services-location | 21.3.0 |
| minSdk / targetSdk | 26 / 35 |

---

## 📄 Lisans

Kişisel saha kullanımı için geliştirilmiştir. OpenStreetMap verisi
[ODbL](https://www.openstreetmap.org/copyright) lisansı altındadır.
