# CAdES Seri ve Paralel İmza Örnekleri

Bu rehber, mevcut bir CAdES `.p7s` artifact'ine REST API veya doğrudan Java/JAR
kullanımıyla imza eklemeyi gösterir.

| Paketleme | PARALLEL | SERIAL |
|---|---|---|
| `ATTACHED` | Orijinal belge gönderilmez; gömülü içerik `.p7s` içinden alınır | Orijinal belge gönderilmez; hedef imzaya counter-signature eklenir |
| `DETACHED` | Orijinal belge zorunludur; bağımsız üst seviye `SignerInfo` eklenir | Orijinal belge zorunludur; hedef imzaya counter-signature eklenir |

`PARALLEL`/`SERIAL` imzalar arasındaki ilişkiyi, `ATTACHED`/`DETACHED` ise
orijinal belgenin ayrıca gerekli olup olmadığını belirler. DETACHED belge gereksiniminin
paralel imzaya özgü olmadığı özellikle dikkate alınmalıdır.

## REST API örnekleri

Tüm örnekler `POST /api/v1/signing-sessions` içindir. Geçerli OAuth `eimza.sign` yetkisi,
tenant ile eşleşen `X-Tenant-Id` ve her mantıksal işlem için ayrı `Idempotency-Key`
başlıkları ayrıca gönderilir. `size`, önceki `.p7s` dosyasının değil orijinal belgenin
bayt uzunluğudur.

### ATTACHED + PARALLEL

`documentBase64` ve `documentDigest` bilinçli olarak yoktur.

```json
{
  "documentId": "11111111-1111-4111-8111-111111111111",
  "documentName": "sozlesme.pdf",
  "mediaType": "application/pdf",
  "size": 245760,
  "format": "CADES",
  "targetLevel": "B_B",
  "turkishProfile": "P1",
  "purpose": "Ikinci bagimsiz imza",
  "signingMode": "SERVER_SIDE",
  "serverKeyId": "tenant-smart-card-2",
  "signaturePackaging": "ATTACHED",
  "signatureAlgorithm": "RSA_PKCS1_SHA256",
  "multiSignatureType": "PARALLEL",
  "existingArtifactBase64": "ONCEKI_ATTACHED_P7S_BASE64"
}
```

### ATTACHED + SERIAL

Orijinal belge yine gönderilmez. `targetSignatureIndex` sıfır tabanlı hedef imzadır.

```json
{
  "documentId": "22222222-2222-4222-8222-222222222222",
  "documentName": "sozlesme.pdf",
  "mediaType": "application/pdf",
  "size": 245760,
  "format": "CADES",
  "targetLevel": "B_B",
  "turkishProfile": "P1",
  "purpose": "Seri onay imzasi",
  "signingMode": "SERVER_SIDE",
  "serverKeyId": "tenant-smart-card-2",
  "signaturePackaging": "ATTACHED",
  "signatureAlgorithm": "RSA_PKCS1_SHA256",
  "multiSignatureType": "SERIAL",
  "existingArtifactBase64": "ONCEKI_ATTACHED_P7S_BASE64",
  "targetSignatureIndex": 0
}
```

### DETACHED + PARALLEL

Orijinal belge ve ona ait SHA-256 özeti zorunludur.

```json
{
  "documentId": "33333333-3333-4333-8333-333333333333",
  "documentDigest": {
    "algorithm": "SHA-256",
    "value": "ORIJINAL_BELGE_SHA256_BASE64URL"
  },
  "documentName": "sozlesme.pdf",
  "mediaType": "application/pdf",
  "size": 245760,
  "format": "CADES",
  "targetLevel": "B_B",
  "turkishProfile": "P1",
  "purpose": "Ikinci bagimsiz imza",
  "signingMode": "SERVER_SIDE",
  "serverKeyId": "tenant-smart-card-2",
  "documentBase64": "ORIJINAL_BELGE_BASE64",
  "signaturePackaging": "DETACHED",
  "signatureAlgorithm": "RSA_PKCS1_SHA256",
  "multiSignatureType": "PARALLEL",
  "existingArtifactBase64": "ONCEKI_DETACHED_P7S_BASE64"
}
```

### DETACHED + SERIAL

Seri imza olmasına rağmen orijinal belge zorunludur; paket belgeyi içinde taşımaz.

```json
{
  "documentId": "44444444-4444-4444-8444-444444444444",
  "documentDigest": {
    "algorithm": "SHA-256",
    "value": "ORIJINAL_BELGE_SHA256_BASE64URL"
  },
  "documentName": "sozlesme.pdf",
  "mediaType": "application/pdf",
  "size": 245760,
  "format": "CADES",
  "targetLevel": "B_B",
  "turkishProfile": "P1",
  "purpose": "Seri onay imzasi",
  "signingMode": "SERVER_SIDE",
  "serverKeyId": "tenant-smart-card-2",
  "documentBase64": "ORIJINAL_BELGE_BASE64",
  "signaturePackaging": "DETACHED",
  "signatureAlgorithm": "RSA_PKCS1_SHA256",
  "multiSignatureType": "SERIAL",
  "existingArtifactBase64": "ONCEKI_DETACHED_P7S_BASE64",
  "targetSignatureIndex": 0
}
```

DETACHED belge eksikse `DETACHED_CONTENT_REQUIRED`, önceki üst seviye imzaların
`messageDigest` değerleriyle eşleşmiyorsa `CADES_DETACHED_CONTENT_MISMATCH` döner.
ATTACHED olarak bildirilen artifact gömülü içerik taşımıyorsa
`CADES_ATTACHED_CONTENT_MISSING` döner.

### Server-side tamamlatma

```http
POST /api/v1/signing-sessions/{sessionId}/server-sign
Authorization: Bearer <access-token>
X-Tenant-Id: <tenant-uuid>
Content-Type: application/json

{}
```

Boş gövde hem HSM hem SMART_CARD için geçerlidir. HSM yalnız yönetim profilindeki güvenli
`credentialRef` yaklaşımını kullanır. Server-side SMART_CARD PIN'i opsiyoneldir ve verilirse
yalnız işlem süresince bellekte tutulur; gerçek PIN komut satırına, loga veya örnek dosyaya
yazılmaz. PIN verilmezse mevcut token oturumu veya güvenli profil credential'ı denenir;
cihaz giriş isterse `SERVER_SMART_CARD_LOGIN_REQUIRED` döner.

Client-side akış değişmez: PIN yalnız Smart Card Agent'ın yerel Swing penceresinde alınır.

## Doğrudan Java/JAR örnekleri

`signWithCardOrHsm(...)` kart/HSM içindeki imza callback'ini temsil eder; özel anahtar
cihazdan çıkarılmaz.

### ATTACHED: paralel, ardından seri

```java
byte[] embedded = cades.extractAttachedContent(previousAttachedP7s);
CadesSigningPreparation parallel = cades.prepareParallel(
        previousAttachedP7s, embedded, secondCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), true, true);
byte[] parallelP7s = cades.completeBaseline(
        parallel, signWithCardOrHsm(parallel.signedAttributes()))
        .encodedSignature();

CadesSigningPreparation serial = cades.prepareCounterSignature(
        parallelP7s, 0, approverCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), true);
byte[] serialP7s = cades.completeBaseline(
        serial, signWithCardOrHsm(serial.signedAttributes()))
        .encodedSignature();
```

ATTACHED akışta kullanıcıdan orijinal belge yeniden istenmez.

### DETACHED: paralel, ardından seri

```java
cades.validateDetachedContent(previousDetachedP7s, originalDocument);
CadesSigningPreparation parallel = cades.prepareParallel(
        previousDetachedP7s, originalDocument, secondCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), false, true);
byte[] parallelP7s = cades.completeBaseline(
        parallel, signWithCardOrHsm(parallel.signedAttributes()))
        .encodedSignature();

cades.validateDetachedContent(parallelP7s, originalDocument);
CadesSigningPreparation serial = cades.prepareCounterSignature(
        parallelP7s, 0, approverCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), true);
byte[] serialP7s = cades.completeBaseline(
        serial, signWithCardOrHsm(serial.signedAttributes()))
        .encodedSignature();
```

XAdES paralel imza yalnız `DETACHED` veya `ENVELOPING` paketlemede desteklenir; seri imza
`xades:CounterSignature` üretir. PAdES'te paralel imza desteklenmez; seri imza incremental
PDF revision olarak eklenir. Bu formatların Java örnekleri
[`JAVA_KUTUPHANE_ENTEGRASYONU.md`](JAVA_KUTUPHANE_ENTEGRASYONU.md) içindedir.
