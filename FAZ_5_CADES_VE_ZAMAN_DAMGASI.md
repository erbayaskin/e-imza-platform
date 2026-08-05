# Faz 5 — CAdES B-T ve RFC 3161 Zaman Damgası MVP

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

Tarih: 28.07.2026  
Durum: **Yazılım MVP'si tamamlandı; gerçek ESHS/TSA ve ikinci haricî doğrulayıcı kabulü bekliyor**

## 1. Alınan ürün kararı

Faz 1'de açık bırakılan ilk format kararı, geliştirmeyi ilerletmek amacıyla bu MVP için
**ayrık CAdES Baseline B-T** olarak alınmıştır. Genel dosya türlerinde kullanılabilir ve
Faz 4'teki kart aracısının ham SHA-256 özet imzalama protokolüyle uyumludur.

Bu karar PAdES ve XAdES desteğini kaldırmaz; sonraki format fazlarına erteler.

Normatif teknik taban:

- ETSI EN 319 122-1 V1.3.1 (2023-06), CAdES building blocks ve baseline imzalar:
  https://www.etsi.org/deliver/etsi_en/319100_319199/31912201/01.03.01_60/en_31912201v010301p.pdf
- RFC 3161 ve SHA-256 sertifika tanımlaması için RFC 5816:
  https://www.rfc-editor.org/rfc/rfc3161
  ve https://www.rfc-editor.org/rfc/rfc5816
- Kriptografik CMS/TSP uygulaması: Bouncy Castle Java 1.84.

## 2. Üretilen imza

Yeni `signature-cades` modülü iki aşamalı dış imza akışı sağlar:

1. İçeriğin SHA-256 özeti hesaplanır.
2. CMS imzalı özellikleri DER ile hazırlanır:
   - `content-type`
   - `message-digest`
   - `signing-time` — iddia edilen zaman, güvenilir zaman değildir
   - `signing-certificate-v2` — SHA-256
   - Yapılandırılmışsa `signature-policy-identifier` OID/özet/URL
3. DER imzalı özelliklerinin SHA-256 özeti yerel kart aracısına gönderilir.
4. Kartın RSA PKCS#1 SHA-256 veya ECDSA SHA-256 imzası merkezi tarafta açık
   anahtarla doğrulanır.
5. Kart imzasının SHA-256 özeti RFC 3161 TSA'ya gönderilir.
6. Doğrulanmış `TimeStampToken`, `signature-time-stamp` unsigned attribute olarak
   CMS içine eklenir.
7. Ayrık DER CAdES B-T çıktısı ve teknik profil raporu üretilir.

Özel anahtar ve belge TSA'ya gitmez. TSA yalnız imza değerinin özetini görür.

## 3. Türkiye imza politikası sınırı

BTK P2/P3/P4 politika değerleri kod içinde uydurulmaz veya varsayılmaz. Gerçek politika
OID, özet algoritması OID'si, politika dokümanı özeti ve isteğe bağlı politika URL'siyle
birlikte yapılandırılırsa CAdES imzalı özelliğine alınır ve doğrulamada aynı değerlerle
karşılaştırılır.

Bu değerler ve onaylı ESHS/TSA seçimi olmadan çıktı yalnız teknik olarak `CAdES B-T`
sayılır; “Türkiye güvenli/nitelikli elektronik imzası” sonucu verilmez.

## 4. RFC 3161 istemcisi

`timestamp-client` modülündeki üretim istemcisi:

- Yalnız HTTPS TSA adresi kabul eder ve HTTP yönlendirmesini reddeder.
- İstek zaman aşımı ve 1 MiB cevap sınırı uygular.
- `application/timestamp-query` / `application/timestamp-reply` kullanır.
- SHA-256 message imprint, kriptografik nonce, istenen politika ve `certReq=true`
  alanlarıyla istek üretir.
- RFC 3161 cevap durumu ve token varlığını kontrol eder.
- Özet OID/değeri, nonce ve politika OID bağını doğrular.
- TSA token CMS imzasını ve imzalayan sertifika bağını doğrular.
- TSA sertifikasında kritik ve yalnız `id-kp-timeStamping` EKU ister.
- `genTime` anında sertifika geçerliliğini kontrol eder.
- Yapılandırılmış TSA güven köklerine PKIX yolu kurar.

TSA sertifikası ve zincirinin OCSP/SİL iptal kontrolü Faz 6'ya bırakılmıştır. Bu nedenle
MVP doğrulama raporu `revocationStatus=NOT_CHECKED_PHASE_6` üretir.

## 5. Doğrulama ve negatif testler

Üretim yolundan ayrı `CadesSignatureVerifier`; ayrık içerik bağını, CMS imzasını,
tekil imzalayan sertifikasını, `signing-certificate-v2` özetini, beklenen imza
politikasını, gömülü zaman damgasını ve TSA güven yolunu kontrol eder.

Otomatik test paketi geçerli CAdES B-T yanında değiştirilmiş içerik, yanlış kart
imzası, beklenmeyen/eksik imza politikası ve farklı RFC 3161 `messageImprint`
senaryolarını içerir.

Testlerde bağımsız imzalayan ve kök → TSA sertifika zinciri üretilmektedir. İkinci ürün
doğrulayıcısı (örneğin Avrupa Komisyonu DSS) bu çalışma ortamında bulunmadığı için
haricî birlikte çalışabilirlik kabulü tamamlanmış sayılmaz.

## 6. Üretim kabul kapıları

Canlı kullanımdan önce:

1. BTK profili ve gerçek imza politikası OID/özet/URL değeri onaylanmalı.
2. 5070 kapsamındaki ESHS/TSA, uç adresi, kimlik doğrulaması ve politika OID'si belirlenmeli.
3. TSA kök/ara sertifikaları sürümlü güvenilir sertifika deposuna alınmalı.
4. TSA sertifika iptal kontrolü Faz 6 ile bağlanmalı.
5. Gerçek kart + gerçek TSA ile örnek `.p7s` üretilmeli.
6. Aynı örnek en az bir haricî doğrulayıcıda ve Türkiye profil testlerinde geçmeli.

Bu kapılar kapanmadan hukuki uygunluk iddiası yapılamaz.
