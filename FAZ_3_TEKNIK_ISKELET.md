# Faz 3 — Teknik İskelet ve Geliştirme Altyapısı

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Durum: Tamamlandı  
> Belge sürümü: 1.0  
> Tarih: 28 Temmuz 2026  
> Bağlı belgeler: [Proje Planı](E_IMZA_PROJE_PLANI.md), [Faz 2 Mimari ve Tehdit Modeli](FAZ_2_MIMARI_VE_TEHDIT_MODELI.md)

## 1. Gerçeklenen teknoloji tabanı

- Java 21
- Spring Boot 4.1.0
- Maven 3.8.8+
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security ve OAuth2 Resource Server
- Flyway
- PostgreSQL üretim sürücüsü
- H2 yerel geliştirme/test veritabanı
- JUnit 5, AssertJ ve Spring Boot Test
- JaCoCo
- OpenAPI 3.1

Spring Boot 4.1.0 seçimi 28 Temmuz 2026 tarihinde resmî Spring Boot sistem gereksinimleri ve sürüm bilgileri kontrol edilerek yapılmıştır. Java 21 ve Maven 3.8.8 Maven Enforcer ile zorunlu tutulmuştur.

## 2. Modüller

| Modül | Gerçeklenen iskelet |
|---|---|
| `signature-core` | Format/seviye/profil modelleri, doğrulama sonucu ve imzalama oturumu durum makinesi |
| `certificate-validation` | Faz 6 ile tamamlanan PKIX, NES/politika ve OCSP/SİL doğrulama motoru |
| `timestamp-client` | Faz 5–6 ile tamamlanan RFC 3161 istemcisi ve token doğrulaması |
| `signature-cades` | Faz 5 ile eklenen ayrık CAdES B-T üretim ve doğrulama modülü |
| `smartcard-agent` | ATR normalizasyonu, maskeli eşleştirme ve kart profili modeli |
| `signature-api` | Spring Boot uygulaması, REST uçları, güvenlik profilleri, persistence ve migration |

Kriptografik çekirdek Spring Framework'e bağımlı değildir. Haricî servisler ve kart erişimi port/arayüzlerle ayrılmıştır.

## 3. Çalışan özellikler

- Spring Boot uygulaması başlatılabiliyor.
- Sağlık, info ve metrics actuator uçları yapılandırıldı.
- İmzalama oturumu oluşturma, okuma ve iptal etme uçları eklendi.
- Oturumlar tenant, kullanıcı ve idempotency anahtarıyla bağlanıyor.
- Tekrarlanan aynı idempotency anahtarı mevcut oturumu döndürüyor.
- Faz 3 algoritma kapısı yalnız `SHA-256` belge özetine izin veriyor.
- İmzalama durum geçişleri merkezi durum makinesiyle korunuyor.
- Hatalar HTTP Problem Details biçiminde kod, korelasyon kimliği ve retry bilgisi taşıyor.
- Tüm HTTP yanıtlarında `X-Correlation-Id` üretiliyor/taşınıyor.
- Yerel profil H2, üretim profili PostgreSQL kullanıyor.
- Üretim profilinde OAuth2 JWT kaynak sunucusu ve scope bazlı yetki kontrolü var.
- Veritabanı şeması Flyway ile oluşturuluyor ve Hibernate tarafından doğrulanıyor.

## 4. Veritabanı migration'ları

### V1

- `signing_session`
- `signature_artifact`
- `audit_event`
- Idempotency, tenant/durum, süre sonu ve denetim indeksleri

### V2

- `policy_version`
- `trust_store_version`
- `validation_run`
- `validation_check`
- Politika ve güven deposu yabancı anahtarları

### V3

- `trusted_certificate`
- `trust_store_entry`
- `trust_store_lock`
- Güven deposu sürüm yürürlük indeksleri
- Kök/alt kök sertifikaların immutable snapshot üyelikleri

Migration'lar H2 PostgreSQL uyumluluk modunda test edilmiştir. PostgreSQL üzerinde Testcontainers doğrulaması ilerleyen altyapı çalışmasına eklenmelidir.

## 5. API sözleşmesi

OpenAPI 3.1 sözleşmesi:

`signature-api/src/main/resources/static/openapi/e-signature-api-v1.yaml`

Gerçeklenen uçlar:

- `POST /api/v1/signing-sessions`
- `GET /api/v1/signing-sessions/{sessionId}`
- `POST /api/v1/signing-sessions/{sessionId}/cancel`
- `GET /api/v1/admin/trusted-certificates`
- `POST /api/v1/admin/trusted-certificates`
- `PUT /api/v1/admin/trusted-certificates/{certificateId}`
- `DELETE /api/v1/admin/trusted-certificates/{certificateId}`
- `GET /api/v1/admin/trusted-certificates/versions/{versionId}`

Sözleşmede tanımlanıp sonraki faza bırakılan uçlar:

- İmza yükleme/tamamlama
- İmza doğrulama
- Sertifika doğrulama
- Zaman damgası doğrulama
- Yönetim uçları

OpenAPI'deki `X-Tenant-Id` Faz 3 geçiş çözümüdür. Üretimde başlık değeri JWT içindeki doğrulanmış tenant claim'iyle zorunlu olarak eşleştirilmeli veya tamamen token'dan türetilmelidir.

## 6. Güvenlik iskeleti

### Yerel profil

- Yalnız geliştirici bilgisayarında kullanılmak üzere tüm isteklere açıktır.
- Açıkça `local` seçilmeden etkinleşmez.
- Kullanıcı kimliği yoksa `local-development-user` kullanılır.

### Üretim profili

- Güvenli varsayılan profildir; gerekli sır ve bağlantılar yoksa uygulama başlamaz.
- JWT issuer URI ortam değişkeninden alınır.
- `/actuator/health` anonim erişime açıktır.
- İmzalama için `eimza.sign`,
- Doğrulama için `eimza.validate`,
- Yönetim için `eimza.admin`

scope'u gerekir.

### Henüz gerçeklenmeyen güvenlik işleri

- İmzalı manifest/JWS veya COSE
- Cihaz eşleştirme ve cihaz anahtarı
- Yerel aracının loopback güvenliği
- Denetim hash zincirinin yazılması
- Dört göz politika ve güven deposu onay akışı
- Hassas veri log filtresinin içerik testleri

Bu işler Faz 4 ve ilgili sonraki fazların kapsamındadır. Faz 4 ile yerel kart aracısı
gerçeklenmiştir; merkezi API'nin oturum manifestini Ed25519 ile üretip imzalaması ve
imza sonucunu oturuma bağlaması Faz 5 entegrasyonunda tamamlanacaktır.

## 7. Test ve kalite kapıları

Çalıştırılan komut:

```shell
mvn --batch-mode --no-transfer-progress verify
```

Kurumsal Windows sertifika deposunun Maven tarafından kullanılması gereken mevcut geliştirme ortamında:

```powershell
mvn --batch-mode --no-transfer-progress `
  "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" `
  "-Djavax.net.ssl.trustStoreType=Windows-ROOT" `
  clean verify
```

Son doğrulama sonucu:

- Reactor modülü: 6/6 başarılı
- Birim/context/entegrasyon testi: 7 başarılı
- Hata: 0
- Flyway migration: 3/3 başarılı
- Spring uygulama context'i: başarılı
- Çalıştırılabilir JAR: üretildi

Test edilen davranışlar:

- Mutlu yol imzalama durum geçişleri
- Kullanıcı onayını atlayan geçersiz durum geçişinin reddi
- P2/P3 kesinleşme durum geçişi
- ATR hex normalizasyonu
- Maskeli ATR eşleştirme
- Spring context + H2 + Flyway + JPA şema doğrulaması
- Güvenilir CA sertifikası ekleme, güncelleme, tarihsel snapshot seçme ve çıkarma

### HTTP duman testi

- `GET /actuator/health` → `UP`
- `POST /api/v1/signing-sessions` → `MANIFEST_ISSUED`
- `GET /api/v1/signing-sessions/{id}` → aynı oturum ve durum

Test sonrasında yerel uygulama süreci kapatılmıştır.

## 8. CI

`.github/workflows/ci.yml` ile:

- Ubuntu üzerinde Temurin Java 21 kurulumu,
- Maven dependency cache,
- `mvn clean verify`,
- Surefire ve JaCoCo raporlarının artifact olarak yüklenmesi

tanımlandı.

Repository GitHub dışında tutulacaksa aynı komut kurumun CI sistemine taşınabilir.

## 9. Yapılandırma

### Yerel çalıştırma

```shell
mvn -pl signature-api -am package -DskipTests
java -jar signature-api/target/signature-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

### Üretim

```shell
java -jar signature-api/target/signature-api-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

Gerekli üretim değişkenleri:

- `EIMZA_DB_URL`
- `EIMZA_DB_USERNAME`
- `EIMZA_DB_PASSWORD`
- `EIMZA_OIDC_ISSUER_URI`

Sırlar kaynak kodda veya varsayılan yapılandırmada bulunmaz.

## 10. Faz 3 anındaki bilinen sınırlar ve sonraki gerçekleşmeler

- Gerçek elektronik imza Faz 4 yazılım MVP'sinde gerçekleştirildi; fiziksel kart kabulü bekliyor.
- PKCS#11 sürücüsü ve kart erişimi Faz 4'te gerçekleştirildi; üretici sürücüsü kabulü bekliyor.
- Manifest Faz 4'te Ed25519 ile imzalı ve replay korumalı hale getirildi.
- CAdES B-T Faz 5'te gerçekleştirildi; XAdES/PAdES Faz 7'yi bekliyor.
- TSA Faz 5'te, OCSP ve SİL/CRL ağ istemcileri Faz 6'da gerçekleştirildi; canlı sağlayıcı kabulü bekliyor.
- Sertifika ve imza doğrulama motorları Faz 6'da gerçekleştirildi.
- PostgreSQL migration testi henüz gerçek PostgreSQL örneğinde çalıştırılmamıştır.
- Yerel H2 2.4.240 sürümü, kullanılan Flyway sürümünün resmen doğruladığı son H2 sürümünden daha yenidir; test başarılı olsa da uyarı alınmıştır.
- OpenAPI sözleşmesinin otomatik lint/generation kapısı henüz eklenmemiştir.
- Test kapsam yüzdesi için zorunlu eşik henüz belirlenmemiştir.

## 11. Faz 4 giriş koşulları ve gerçekleşen durum

Faz 4 başlamadan önce mümkünse:

1. İlk desteklenecek işletim sistemi kesinleştirilmeli.
2. İlk akıllı kart, ATR, okuyucu ve üretici PKCS#11 kitaplığı sağlanmalı.
3. Üretici sürücüsünün lisans ve dağıtım koşulları doğrulanmalı.
4. Yerel aracının kullanıcı arayüzü teknolojisi seçilmeli.
5. Manifest için JWS/JCS veya COSE/CBOR kararı PoC ile verilmeli.
6. Uygulama paketlerini imzalayacak kod imza sertifikası/altyapısı belirlenmeli.

Kart sağlanmadan Faz 4 yazılım MVP'si tamamlanmış; ham RSA/ECDSA özet imzalama
uyumluluğu yazılımsal anahtarlarla doğrulanmıştır. Gerçek PC/SC okuyucu, kart ATR'si,
üretici PKCS#11 kitaplığı ve hata senaryoları için saha kabulü beklemektedir.
Detay: [Faz 4 teslim belgesi](FAZ_4_AKILLI_KART_YEREL_ARACI.md).

## 12. Değişiklik günlüğü

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 28.07.2026 | Java/Spring Boot çok modüllü iskelet, API, migration, güvenlik profilleri, testler, CI ve çalıştırma doğrulaması tamamlandı. |
