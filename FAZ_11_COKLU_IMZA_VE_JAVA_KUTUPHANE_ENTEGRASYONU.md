# Faz 11 - Çoklu İmza ve Doğrudan Java Kütüphane Entegrasyonu

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.
> Durum: Yazılım teslimi ve otomatik testleri tamamlandı; haricî çoklu imza kabulü bekliyor.  

## Amaç

Bu faz aynı belge üzerinde birden fazla onay ve onay sırası gerektiren süreçleri
destekler. İlk imza akışı geriye uyumlu olarak `SINGLE` kalır. Ek imza oturumu,
önceki artifact'i `existingArtifactBase64` ile alır ve `PARALLEL` ya da
`SERIAL` semantiğini açıkça taşır.

## Format ve semantik matrisi

| Format | PARALLEL | SERIAL |
|---|---|---|
| CAdES | Aynı CMS SignedData içine bağımsız SignerInfo (co-signature) | Hedef SignerInfo üzerine CMS counterSignature unsigned attribute |
| XAdES | DETACHED/ENVELOPING imzaları standart XMLDSig öğeleri olarak çoklu kapsayıcıda | Hedef SignatureValue'yu referanslayan XAdES CounterSignature |
| PAdES | Desteklenmez | İmzalı PDF üzerine yeni incremental revision ve yeni ETSI.CAdES.detached imza |

XAdES `ENVELOPED + PARALLEL` kabul edilmez. Eski enveloped imzaya sonradan
kardeş Signature eklemek ilk imzanın belge özetini değiştireceğinden, paralel
XAdES için `DETACHED` veya `ENVELOPING` kullanılır.

## API sözleşmesi

`POST /api/v1/signing-sessions` isteğine üç geriye uyumlu alan eklenmiştir:

- `multiSignatureType`: `SINGLE` (varsayılan), `PARALLEL`, `SERIAL`
- `existingArtifactBase64`: ek imzada zorunlu önceki artifact
- `targetSignatureIndex`: seri CAdES/XAdES için 0 tabanlı hedef; varsayılan 0

Sonraki server-side ve client-side adımlar ilk imza akışıyla aynıdır. Manifest,
yeni imzalayanın sertifikasına ve yeni hazırlanmış digest'e bağlanır. PIN ve özel
anahtar davranışı değişmez.

## Adım adım çoklu imza

1. Önceki artifact ve gerekiyorsa orijinal belge okunur.
2. İstek `PARALLEL` veya `SERIAL` ile oluşturulur.
3. API format/paketleme kombinasyonunu ve önceki artifact'i doğrular.
4. Format modülü yeni SignerInfo, CounterSignature veya PDF revision hazırlığını
   üretir.
5. Server-side kart/HSM ya da client-side agent yalnız yeni hazırlığın özetini
   imzalar.
6. `complete` veya `server-sign` önceki artifact'i koruyarak yeni imzayı ekler.
7. Doğrulayıcı tüm CAdES SignerInfo/counter-signature, tüm XAdES Signature
   öğeleri veya tüm PAdES signature dictionary kayıtlarını denetler.

## Kütüphane API'leri

- `CadesSignatureService.prepareParallel(...)`
- `CadesSignatureService.prepareParallelDigest(...)`
- `CadesSignatureService.prepareCounterSignature(...)`
- `XadesSignatureService.prepareParallel(...)`
- `XadesSignatureService.prepareCounterSignature(...)`
- `PadesSignatureService.prepareSequential(...)`

Bu metotlar mevcut dış anahtar desenini korur: `prepare` imzalanacak özeti
döndürür, uygulama kart/HSM/özel anahtarla ham imza üretir, `completeBaseline`
veya `completeWithTimestamp` artifact'i tamamlar.

## Veritabanı ve geriye uyumluluk

Flyway `V12__add_multi_signature_sessions.sql` aşağıdaki alanları ekler:

- `multi_signature_type` - varsayılan `SINGLE`
- `existing_artifact`
- `target_signature_index` - varsayılan `0`

Mevcut istemciler yeni alanları göndermediğinde tek imza davranışı değişmez.

## Demo

Ayrı sayfa: `http://localhost:8080/multi-signature/`

Sayfada önceki imzalı dosya, gerekiyorsa orijinal belge, format, paketleme,
PARALLEL/SERIAL tipi, hedef indeks, algoritma, server/client modu ve sertifika
seçilir. Client-side PIN yalnız Smart Card Agent penceresinde alınır.

## Test ve kabul

- CAdES testi iki üst seviye SignerInfo ve bir counter-signature üretip tümünü
  kriptografik olarak doğrular.
- XAdES testi iki paralel Signature ve bir nested CounterSignature üretip tümünü
  XMLDSig doğrulayıcıyla doğrular.
- PAdES testi iki incremental signature dictionary üretip iki CMS imzasını da
  doğrular.
- Tam Maven reactor testi ve fiziksel kart kabul testi ayrıca çalıştırılır.

## Standart değişikliğinde güncelleme sırası

1. `FAZ_1_UYUMLULUK_MATRISI.md`
2. `STANDART_DEGISIKLIGI_ETKI_HARITASI.md`
3. Bu Faz 11 belgesi ve OpenAPI
4. İlgili `signature-cades/xades/pades` modülü
5. Format doğrulayıcıları ve çoklu imza testleri
6. Demo, Java entegrasyon örnekleri ve PDF kılavuzu
