# Yönetim ekranı, Smart Card Agent ve server-side test rehberi

Tarih: 29.07.2026

## 1. Ekranlar

- Yönetim ve tanımlama: `http://localhost:8080/admin/`
- İmzalama demosu: `http://localhost:8080/demo/`

API çalıştırma:

```powershell
java -jar .\signature-api\target\signature-api-0.1.0-SNAPSHOT-exec.jar `
  --spring.profiles.active=local
```

Yönetim sayfası şu tanımları ayrı bölümlerde yönetir:

1. Server-side akıllı kart ve HSM profilleri
2. Güvenilir kök/alt kök sertifikaları
3. RFC 3161 zaman damgası profili
4. Sertifika/imza doğrulama politikaları

Server key ve TSA ayarları Flyway V9 ile DB'ye yazılır. TSA yetkilendirme değeri,
HSM PIN'i ve opsiyonel server-side kart PIN'i DB'ye yazılmaz; yalnız güvenli kaynağı
gösteren ortam değişkeni adı `credentialRef` olarak saklanır.

## 2. Agent'ın görünür biçimde başlatılması

Agent paketi yeniden üretildikten sonra başlatıldığında sürekli açık kalan Swing
durum penceresi gösterir:

```powershell
cd D:\ErbayProject

.\scripts\start-local-agent.ps1
```

Başlatıcı API'den Ed25519 manifest public key'ini alır, izinli origin ve yerel profil
yolunu yalnız agent sürecine verir. Agent ilk çalıştırmada `.eimza` altında kalıcı cihaz
UUID + Ed25519 anahtarı oluşturur. Pencerede agent adresi, cihaz UUID'si ve kart durumu
gösterilir. `Kartları yenile` kart/ATR eşleşmesini tekrar tarar. Public sertifika
listeleme PIN istemez; PIN penceresi yalnız özel anahtarla imza sırasında bir kez açılır.

Agent yalnız `CLIENT_SIDE` imzalama için kullanılır.

## 3. Server-side akıllı kart profili

Server-side testte agent çalıştırılmaz. Kart, API'nin çalıştığı makineye takılır.
Yönetim sayfasında:

- Cihaz türü: `SMART_CARD`
- PKCS#11: `C:/Windows/System32/akisp11.dll`
- ATR: `3B9F978131FE4580655443D3228231C073F621808105D3`
- ATR maskesi: `FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF`
- Slot: boş; otomatik bulunur
- İzinli tenant: `11111111-1111-1111-1111-111111111111`
- Etkin: seçili

Profil kaydedildiğinde değiştirilemeyen UUID `serverKeyId` sunucu tarafından üretilir.
Demo sayfası yalnız etkin ve seçili tenant'a izin veren profilleri listeler. Profil
görünmüyorsa yönetim ekranındaki `İzinli tenant UUID’leri` alanı demo tenant'ıyla aynı
olmalıdır. Demo formundaki server-side akıllı kart PIN'i opsiyoneldir. Verilirse yalnız
tek işlem için bellekte tutulur; verilmezse profilin opsiyonel güvenli `credentialRef`
değeri veya PIN gerektirmeyen/mevcut token oturumu denenir. Kart middleware'i gerçekten
giriş istiyorsa `SERVER_SMART_CARD_LOGIN_REQUIRED` döner. PIN kalıcı olarak saklanmaz,
loglanmaz veya manifeste yazılmaz. Client-side akış değişmez; PIN yalnız agent Swing
penceresinden alınır.

## 4. HSM profili

- Cihaz türü: `HSM`
- PKCS#11 kütüphane yolu ve slot zorunludur.
- ATR girilmez.
- `credentialRef`, HSM PIN'ini taşıyan ortam değişkeninin adıdır.

Örnek:

```powershell
$env:HSM_PIN = "gercek-pin-degeri"
```

Yönetim ekranına değer olarak PIN değil yalnız `HSM_PIN` yazılır. HSM PIN'i HTTP
isteğinde kabul edilmez.

## 5. Güven deposu ve local politika

İmzalama sertifikasının kök ve gerekiyorsa alt kök CA sertifikaları yönetim
sayfasından eklenmelidir. Son kullanıcı sertifikası güven kökü olarak eklenmez.

`local` profilde gerçek OCSP/SİL ağı kullanılmadığından fiziksel kart kabul
testinde gerekirse politika modu `CUSTOM` seçilip yalnız
`OCSP / SİL erişilebilirlik politikası` pasif yapılabilir. Kriptografik bütünlük,
sertifika yolu, güven kökü ve algoritma kontrolleri yine çalışır.

## 6. TSA

TSA profili etkinse endpoint, timeout ve sağlayıcı ID girilir. Kimlik doğrulama
gerekiyorsa örneğin:

```powershell
$env:TSA_AUTHORIZATION = "Basic xxxxxxxxx"
```

Yönetim ekranındaki credential reference alanına `TSA_AUTHORIZATION` yazılır.
TSA zincirinin güvenilir kökü de güven deposunda bulunmalıdır.

TSA tanımlanmadan ilk test seviyesi `B_B` olmalıdır. `B_T` için etkin ve erişilebilir
TSA gerekir.

## 7. Hazır kart profili ve ATR ile otomatik tanımlama

Yönetim sayfasındaki `Akıllı kart profil kataloğu ve otomatik tanımlama` bölümünde:

1. `Takılı kartları tara ve eşleştir` seçilir.
2. API, sunucuya bağlı PC/SC okuyucularını tarar ve kartın ATR değerini okur.
3. ATR, katalogdaki değer ve maske ile karşılaştırılır.
4. Eşleşme bulunursa PKCS#11 yolu, ATR, ATR maskesi ve otomatik slot seçimi profil
   formuna yazılır.
5. `Profili otomatik oluştur` ile tenant'a ait kalıcı `serverKeyId` üretilir.
6. Server-side imzada PIN alanı opsiyoneldir. PIN varsa API bu işlem kopyasıyla,
   yoksa profil credential'ı veya PIN'siz token oturumuyla slotları tarar ve uygun
   slotu otomatik seçer; yönetici akıllı kart için slot girmek zorunda değildir.
   Giriş gerektiren sürücü PIN/credential olmadan kullanılırsa açıklayıcı
   `SERVER_SMART_CARD_LOGIN_REQUIRED` hatası döner.

İlk hazır şablon AKİS kartıdır:

- Şablon ID: `akis-smart-card`
- PKCS#11: `C:/Windows/System32/akisp11.dll`
- ATR: `3B9F978131FE4580655443D3228231C073F621808105D3`
- Slot: ATR üzerinden otomatik

Karttaki sertifikanın süresi dolmuşsa donanım erişimi başarılı olsa bile yeni
imza oluşturulmaz ve `SERVER_SIGNER_CERTIFICATE_EXPIRED` döner. Bu davranış
güvenlik kontrolüdür; yalnız test amacıyla süresi dolmuş sertifikayla imza
üretilmez.

Yeni kart ailesi desteği eklenirken `CardProfileCatalogService` içindeki kataloğa
üretici tarafından doğrulanmış ATR, maske, sürücü yolu ve izinli mekanizmalar
eklenir. Bilinmeyen ATR otomatik olarak güvenilir bir karta dönüştürülmez.

## 8. Ayrı imza doğrulama sayfası

`http://localhost:8080/validation/` sayfası üç formatı destekler:

1. CAdES: `DETACHED` için orijinal belge ve `.p7s`; `ATTACHED` için yalnız
   `.p7s` seçilir.
2. XAdES: `DETACHED` için orijinal belge ve `.xml`; `ENVELOPED` veya
   `ENVELOPING` için yalnız imzalı `.xml` seçilir.
3. PAdES: Yalnız imzalı PDF seçilir; PDF ByteRange ve içindeki CAdES/CMS doğrulanır.
4. Paketleme türü `AUTO` bırakılabilir veya beklenen tür açıkça seçilebilir.
   Seçim imzada tespit edilen türle uyuşmazsa doğrulama reddedilir.
5. `İmzayı ve sertifika zincirini doğrula` seçilir.
6. Ana sonuç (`VALID`, `INVALID`, `INDETERMINATE`), tespit edilen format/seviye ve
   her politika kontrolü ayrı satırda gösterilir.

Doğrulama, yönetim ekranındaki güvenilir kök/alt kök deposu ile etkin politika
sürümünü kullanır. B-T ve üzeri seviyelerde TSA güven kökü de depoda bulunmalıdır.

## 9. Bağımsız Swing masaüstü imzalama demosu

`desktop-signing-demo`, tarayıcı veya merkez API olmadan proje kütüphanelerini
kullanan örnek uygulamadır:

```powershell
java -jar .\desktop-signing-demo\target\desktop-signing-demo-0.1.0-SNAPSHOT-exec.jar
```

Proje kökündeki `agent-local.yml` otomatik yüklenir. Uygulama kartı ve public
sertifikaları PIN'siz tarar; PIN'i yalnız CAdES imzası sırasında yerel Swing
penceresinde bir kez ister. Attached ve detached CAdES çıktıları `.p7s`
uzantısıyla kaydedilir.
