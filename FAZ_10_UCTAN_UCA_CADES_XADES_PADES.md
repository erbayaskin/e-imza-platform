# Faz 10 — Uçtan Uca CAdES, XAdES ve PAdES

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.
> Durum: Yazılım teslimi tamamlandı; geçerli kart/HSM, canlı hizmet ve haricî ürün kabulü bekliyor.  

## Amaç

Bu faz merkez API ile yerel akıllı kart ajanı arasındaki eksik orkestrasyonu
tamamlar. Özel anahtar karttan çıkmaz. Merkez formata özgü imzalanacak yapıyı
hazırlar; ajan seçilen SHA-256/SHA-384/SHA-512 özetini PKCS#11 kartıyla imzalar.

Doğrudan attached/detached CAdES, enveloped/enveloping/detached XAdES ve
enveloped PAdES için B-B/B-T üretilir. CAdES B-LT/B-LTA yükseltmesi Faz 7
uçlarıyla sürer. XAdES/PAdES LT/LTA doğrudan oturum hedefi olarak kabul edilmez;
böylece üretilmeyen bir seviye beyan edilmez.

## Eklenen bileşenler

| Modül | Sorumluluk |
|---|---|
| `signature-cades` | Belge özetinden signed attributes, B-B/B-T tamamlama |
| `signature-xades` | XMLDSig SignedInfo, XAdES SignedProperties ve SignatureTimeStamp |
| `signature-pades` | PDF byte-range hazırlığı ve `ETSI.CAdES.detached` CMS yerleştirme |
| `signature-api` | Oturum, Ed25519 manifest, durum geçişleri, güven kontrolü ve artifact |
| `smartcard-agent` | Manifest doğrulama, sertifika DER aktarımı ve PKCS#11 imzası |

## Adım adım işlem

1. `POST /api/v1/signing-sessions`: oturum `CREATED` olur.
   İstek `signaturePackaging` ve `signatureAlgorithm` seçimlerini içerir.
2. `POST /{id}/manifest`: kart sertifikası alınır, formata özgü veri hazırlanır.
3. Merkez iki dakika ömürlü manifesti Ed25519 ile imzalar; durum `MANIFEST_ISSUED` olur.
4. `POST /{id}/agent-connected`: ajan bağlantısı kaydedilir, kullanıcı onayı beklenir.
5. `POST /{id}/approve`: açık kullanıcı onayı sonrası durum `CARD_SIGNING` olur.
6. Ajan manifest imzası, cihaz, okuyucu, sertifika parmak izi, süre, nonce ve
   algoritmayı doğrular. PIN yalnız yerel pencerede alınır.
7. `POST /{id}/complete`: kart imzası hazırlanmış veriyle doğrulanır.
8. Sertifika DB'deki güvenilir kök/alt kök snapshot'ına ve Türkiye NES
   politikasına göre `VALID` olmalıdır.
9. B-T için RFC 3161 token formata özgü yere eklenir.
10. Çıktı ve SHA-256 özeti DB'ye yazılır; durum `COMPLETED` olur.
11. `GET /{id}/artifact`: idempotent sonuç indirilir.

## Formata özgü kurallar

### CAdES

Belge özeti `messageDigest` özelliğine alınır. `contentType`, `signingTime`,
`SigningCertificateV2` ve varsa politika DER signed attributes içine konur.
`DETACHED` çıktıda belgeyi içermez ve doğrulamada özgün belge ister. `ATTACHED`
çıktıda belge CMS `eContent` alanında bulunur ve tek başına doğrulanabilir.

### XAdES

`DETACHED` belgeyi `urn:uuid:{documentId}` dış referansıyla imzalar.
`ENVELOPED` imzayı XML belgesinin kök öğesine ekler ve enveloped-signature
transformunu kullanır. `ENVELOPING` belgeyi Base64 kodlu `ds:Object` içinde
taşır. SignedProperties; imzalama zamanı, sertifika özeti, issuer ve serial
içerir. Kart canonical `SignedInfo` özetini imzalar. B-T tokenı
`SignatureValue` üzerinden üretilir.

### PAdES

PDF'e imza alanı ve `ETSI.CAdES.detached` sözlüğü eklenir. PDFBox kesin
`ByteRange` üretir. CMS ayrılmış `/Contents` alanına yazılır; imzalı byte-range
değişmez.

## Güvenlik kararları

- Manifest Ed25519 anahtarı üretimde zorunludur; geçici anahtar yalnız `local`
  profilinde üretilir.
- Üretim özel anahtarı KMS/HSM veya secret manager'dan sağlanır.
- Ajan yalnız önceden yapılandırılmış merkez açık anahtarına güvenir.
- Nonce tekrar kullanımı reddedilir; manifest cihaz ve sertifikaya bağlıdır.
- Belge içeriği verilirse bildirilen SHA-256 özetiyle eşleşmesi zorunludur.
- PAdES, attached CAdES, enveloped/enveloping XAdES ve SHA-384/SHA-512 CAdES
  için belge içeriği zorunludur.
- PKCS#11 DLL yolu HTTP ile kabul edilmez. ATR/DLL profilleri imzalı ajan kurulum
  paketi veya yönetilen yerel konfigürasyonla sürümlenir. Bu, uzaktan DLL yolu
  enjekte edilmesini önleyen bilinçli güvenlik sınırıdır.

## İmza algoritması matrisi

| Anahtar | Seçilebilir algoritmalar |
|---|---|
| RSA | `RSA_PKCS1_SHA256`, `RSA_PKCS1_SHA384`, `RSA_PKCS1_SHA512` |
| EC | `ECDSA_SHA256`, `ECDSA_SHA384`, `ECDSA_SHA512` |

Demo sertifika anahtar türüne göre seçenekleri filtreler. API ve agent seçim ile
sertifika anahtar türünü tekrar doğrular. SHA-1 sunulmaz. Algoritma adı oturum,
manifest ve kart/HSM imzasında aynı olmak zorundadır.

## Yapılandırma

```text
# Merkez
EIMZA_MANIFEST_PRIVATE_KEY=<Base64 PKCS#8 Ed25519>
EIMZA_MANIFEST_PUBLIC_KEY=<Base64 X.509 Ed25519>
EIMZA_MANIFEST_KEY_ID=<anahtar sürümü>
EIMZA_TSA_ENABLED=true
EIMZA_TSA_ENDPOINT=https://...

# Ajan
EIMZA_AGENT_DEVICE_ID=<oturumdaki deviceId>
EIMZA_AGENT_MANIFEST_PUBLIC_KEY=<merkezin Ed25519 açık anahtarı>
EIMZA_AGENT_ALLOWED_ORIGIN=http://localhost:8080
```

PKCS#11 aygıt türü `device-type` ile ayrılır. `SMART_CARD` profillerinde ATR,
ATR maskesi ve mutlak PKCS#11 DLL yolu tanımlanır. Slot, kartın ATR profili
seçildikten sonra sağlayıcıdaki dolu tokenlar taranarak otomatik bulunur.
Akıllı kart profili için `slot-list-index` boş bırakılır. Bazı sürücüler
(fiziksel kabul testindeki AKİS sürücüsü gibi) sertifika deposunu PIN'siz
göstermediğinden slot taraması, istekle gelen tek kullanımlık PIN kullanılarak
yapılır. Bulunan slot `serverKeyId` bazında süreç belleğinde önbelleğe alınır;
PIN kalıcılaştırılmaz ve günlüklenmez.

### 18 Ağustos 2026 server-side SMART_CARD PIN kararı

Yukarıdaki PIN'li AKİS slot keşfi tarihsel fiziksel test akışıdır; güncel API
PIN'i bütün SMART_CARD cihazları için genel ön koşul saymaz:

1. `/server-sign` istek PIN'i opsiyoneldir.
2. PIN verilirse yalnız işlem kapsamındaki `char[]` kopyaları kullanılır ve temizlenir.
3. PIN verilmezse profilin opsiyonel güvenli `credentialRef` değeri, yoksa mevcut
   veya PIN gerektirmeyen middleware/token oturumu denenir.
4. Token gerçekten giriş istiyorsa cihaz/sağlayıcı cevabı kararlı
   `SERVER_SMART_CARD_LOGIN_REQUIRED` Problem Details koduna çevrilir.
5. HSM için istek PIN'i reddedilmeye ve zorunlu güvenli `credentialRef` çözümlemesi
   kullanılmaya devam eder.
6. Client-side PIN akışı değişmemiştir; PIN yalnız agent Swing penceresinde alınır.
7. Otomatik kabul testleri PIN'siz middleware denemesini, opsiyonel profil credential'ını,
   tek kullanımlık istek PIN'i kopyasını, HSM sınırını ve login-required hata kodunu kapsar;
   fiziksel AKİS server-side tekrar koşusu ayrı kabul kapısıdır.

`HSM` profillerinde ATR kullanılmaz. Mutlak sürücü kitaplığı ve
`slot-list-index` açıkça verilmek zorundadır; `auto-discover-slot=false` olur
ve HSM slotları otomatik taranmaz. Bu ayrım yanlış HSM partition/slotunun
seçilmesini engeller.

## Yerel deneme

```powershell
mvn clean verify
mvn -pl signature-api,smartcard-agent -am package -DskipTests
java -jar signature-api/target/signature-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
java -jar smartcard-agent/target/smartcard-agent-0.1.0-SNAPSHOT.jar
```

Merkez ve ajan JAR komutları ayrı PowerShell pencerelerinde çalıştırılır. Kök
reaktörde `-am spring-boot:run` hedefi üst `pom` modülünde de çalıştığı için
`Unable to find a suitable main class` hatası verir.

Test arayüzü: `http://localhost:8080/demo/index.html`

Demo `CLIENT_SIDE` ve `SERVER_SIDE` imzalama modlarını ayrı yürütür.
Client-side yalnız Smart Card Agent ve akıllı kart kullanır; UUID yanında
kayıtlı Ed25519 cihaz kanıtı zorunludur. Server-side, yönetici tanımlı
`serverKeyId` üzerinden HSM veya sunucu akıllı kartını kullanır. Ayrıntılı
kararlar `IKI_MODLU_IMZALAMA_MIMARISI.md` dosyasındadır.

Yerel profilde merkez geçici bir Ed25519 anahtarı üretir. Ajanı başlatmadan önce
`GET /api/v1/signing-configuration/manifest-key` cevabındaki `publicKey` değeri
`EIMZA_AGENT_MANIFEST_PUBLIC_KEY` olarak verilmelidir. Üretimde iki süreç aynı
sürümlü, kalıcı anahtar çiftiyle yapılandırılır.

Fiziksel kart olmadan format modül testleri yazılım RSA anahtarıyla imzalar.
Fiziksel kart geldiğinde `FAZ_8_FIZIKSEL_KART_KABUL_PROSEDURU.md` uygulanır.

### 30 Temmuz 2026 AKİS fiziksel kabul ara sonucu

1. PC/SC taramasında kart ATR'si katalogdaki `akis-smart-card` profiliyle eşleşti.
2. Profil, slot numarası verilmeden `SMART_CARD` olarak oluşturuldu.
3. PKCS#11 slotu tek kullanımlık PIN ile otomatik bulundu ve sertifika okundu.
4. Karttaki sertifikanın süresi dolmuş olduğundan imza üretilmedi; API
   `SERVER_SIGNER_CERTIFICATE_EXPIRED` döndürdü.
5. Bu sonuç ATR eşleştirmesi, sürücü yükleme, otomatik slot keşfi, PIN ile token
   oturumu ve sertifika okuma adımlarının çalıştığını; geçerli sertifikayla gerçek
   imza/doğrulama kabulünün hâlâ yapılması gerektiğini gösterir.
6. Paketleme/algoritma geliştirmesinden sonra test `ATTACHED` CAdES ve
   `RSA_PKCS1_SHA384` seçimiyle tekrarlandı. Oturum bu iki seçimi korudu, kart
   otomatik slot ve PIN ile açıldı, RSA anahtar türü doğrulandı ve yalnız süresi
   dolmuş sertifika kontrolünde güvenli biçimde durdu.

## Standart değişikliğinde güncelleme sırası

1. `FAZ_1_UYUMLULUK_MATRISI.md`
2. `STANDART_DEGISIKLIGI_ETKI_HARITASI.md`
3. Bu belgedeki format ve akış kararları
4. İlgili `signature-*` modülü
5. OpenAPI sözleşmeleri ve geriye dönük testler
6. Fiziksel kart ve ETSI doğrulayıcı kabul testleri

| Değişiklik | Etkilenen yer |
|---|---|
| CAdES signed attributes | `signature-cades`, Faz 5/7 |
| XAdES namespace/canonicalization | `signature-xades`, bu belge |
| PAdES byte-range, subfilter, DSS | `signature-pades`, bu belge |
| NES/güven zinciri | `certificate-validation`, Faz 6, güven deposu |
| TSA politikası | `timestamp-client`, Faz 5 |
| Manifest alanı/ömrü | `signature-api` ve `smartcard-agent` birlikte |
| ATR/PKCS#11 sürücüsü | Faz 4 ve fiziksel kart kabul prosedürü |
