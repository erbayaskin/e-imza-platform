# Faz 6 — Sertifika ve İmza Doğrulama Motoru

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Durum: Yazılım MVP'si tamamlandı; gerçek ESHS OCSP/SİL servisleri, haricî doğrulayıcı ve hukuk/bilgi güvenliği kabulü bekliyor  
> Tarih: 28 Temmuz 2026  
> Kapsam: X.509 sertifika yolu, iptal durumu, Türkiye NES koşulları, CAdES B-T ve RFC 3161 zaman damgası doğrulaması

## 1. Amaç

Faz 6, bir sertifika veya imza için yalnız `true/false` döndürmek yerine sonucu oluşturan bütün kontrolleri kanıtlarıyla raporlar. Sistem:

1. İstenen doğrulama zamanını belirler.
2. O zamanda yürürlükte olan DB güven deposu snapshot'ını seçer.
3. Sertifika yolunu güvenilen köke kadar kurar.
4. Algoritma, anahtar, kullanım, NES ve sertifika politikası kurallarını denetler.
5. Zincirdeki kök dışındaki sertifikaların iptal durumunu OCSP, ardından SİL/CRL ile araştırır.
6. CAdES bütünlüğünü, imzalayanı ve varsa RFC 3161 imza zaman damgasını doğrular.
7. Kriptografik geçerlilik ile Türkiye nitelikli/güvenli e-imza uygunluğunu ayrı raporlar.
8. Kullanılan politika sürümünü, güven deposu sürümünü, sertifika yolunu ve kanıt özetlerini JSON raporuna koyar.

## 2. Normatif başlangıç tabanı

Bu uygulama aşağıdaki sabitlenmiş kaynaklarla geliştirilmiştir. “En güncel sürüm” ifadesi çalışma zamanı kuralı değildir; yeni sürüm çıktığında [standart değişikliği etki haritasındaki](STANDART_DEGISIKLIGI_ETKI_HARITASI.md) süreç uygulanır.

| Kaynak | Sabitlenen sürüm/tarih | Faz 6 kullanımı |
|---|---|---|
| BTK Elektronik İmza Kullanım Profilleri Rehberi | Sürüm 1.0, Haziran 2012 | Türkiye P1–P4 yaklaşımı, imza politikası ve doğrulama bağlamı |
| BTK 2012/DK-15/299 Kurul Kararı | 02.07.2012 | Güvenli elektronik imzada profil/standart dayanağı |
| ETSI EN 319 102-1 | V1.4.1, 2024-06 | Doğrulama süreci, ana/alt sonuç ve doğrulama zamanı yaklaşımı |
| RFC 5280 | Mayıs 2008 | X.509 yol kurma, geçerlilik, uzantı ve SİL temeli |
| RFC 6960 | Haziran 2013 | OCSP istek, cevap, responder ve tazelik kontrolleri |
| RFC 3161 / RFC 5816 | Ağustos 2001 / Mart 2010 | Zaman damgası ve güncel özet algoritması kullanımı |
| ETSI EN 319 122-1 | Projenin Faz 1'de sabitlenen sürümü | CAdES Baseline imza özellikleri |

Kaynakların URL'leri ve değişiklik izleme yöntemi etki haritasında tek yerde tutulur.

## 3. Gerçeklenen bileşenler

| Bileşen | Sorumluluk |
|---|---|
| `DefaultCertificateValidator` | Snapshot seçimi, PKIX yolu, algoritma/anahtar/kullanım/NES/politika ve iptal kontrolleri |
| `NetworkRevocationDataProvider` | AIA'dan OCSP, CRL Distribution Points'ten SİL adresi bulma; cevap imzası, yetki ve tazelik kontrolü |
| `DatabaseTrustedCertificateProvider` | Doğrulama zamanındaki immutable DB güven snapshot'ını kriptografik motora verme |
| `CadesSignatureInspector` | Ayrık CAdES içindeki imzalayan ve gömülü sertifikaları çıkarma |
| `CadesSignatureVerifier` | CMS bütünlüğü, imza özellikleri ve RFC 3161 zaman damgası kontrolü |
| `ValidationService` | Sertifika/imza kontrollerini tek ayrıntılı raporda birleştirme |
| `ValidationController` | Sertifika ve imza doğrulama REST uçları |

## 4. Sertifika doğrulama akışı

1. API, Base64 DER son kullanıcı sertifikasını ve varsa ara sertifikaları ayrıştırır.
2. `validationTime` verilmemişse sunucu zamanı kullanılır; izin verilen saat kaymasından fazla gelecek zaman reddedilir.
3. `DatabaseTrustedCertificateProvider`, doğrulama zamanında geçerli snapshot'ı yükler.
4. Snapshot'taki `ROOT` sertifikaları PKIX trust anchor, `INTERMEDIATE` sertifikaları yol adayı olur.
5. İstekle gönderilen ara sertifikalar da yol kurma adaylarına eklenir; güven kaynağı sayılmaz.
6. Java PKIX builder, son kullanıcıdan DB'deki köke kadar yolu kurar ve X.509 kısıtlarını denetler.
7. Sertifikanın imza algoritması ve açık anahtar büyüklüğü politika ile karşılaştırılır.
8. `digitalSignature` veya `nonRepudiation/contentCommitment` anahtar kullanımı aranır.
9. Yapılandırılmışsa QCStatements içindeki `qcCompliance` ve kabul edilen CertificatePolicies OID'leri aranır.
10. Kök dışındaki her sertifika için OCSP denenir; kesin sonuç alınamazsa SİL/CRL denenir.
11. İptal kanıtının imzası, responder/issuer yetkisi, üretim zamanı ve geçerlilik aralığı doğrulanır.
12. Bütün kontroller ana sonuç, kriptografik sonuç ve Türkiye uygunluk sonucu olarak toplanır.

## 5. İmza doğrulama akışı

1. API, ayrık içerik ile Base64 DER CAdES imzasını alır.
2. CMS yapısı ayrıştırılır; ilk imzalayan ve gömülü sertifikalar çıkarılır.
3. CAdES imza değeri ile içerik bütünlüğü doğrulanır.
4. İmzalı özellikler, imza politikası ve varsa imza zaman damgası denetlenir.
5. Zaman damgası varsa TSA token imzası, `messageImprint`, nonce/politika ve TSA sertifika yolu doğrulanır.
6. Güvenilir zaman damgası zamanı sertifika doğrulama zamanı olarak kullanılır; yoksa istek zamanı kullanılır ve bu ayrım raporlanır.
7. İmzalayan sertifikası, CAdES içindeki ara sertifikalar ve DB güven snapshot'ıyla sertifika motoruna verilir.
8. İmza ve sertifika alt kontrolleri tek `ValidationReport` içinde birleştirilir.

İlk MVP tek imzalayanlı, ayrık CAdES B-T içindir. Çoklu imza ile XAdES/PAdES Faz 7 kapsamındadır.

## 6. Sonuç anlamları

| Sonuç | Anlam |
|---|---|
| `VALID` | Gerekli kontrol kanıtları mevcut ve seçilen politika altında başarılıdır. |
| `INVALID` | Bütünlük bozukluğu, doğrulanmış iptal, süre/kullanım/politika ihlali gibi kesin olumsuz kanıt vardır. |
| `INDETERMINATE` | Güven yolu/iptal kanıtı/zaman damgası güveni gibi zorunlu bir bilgi elde edilememiştir; bu sonuç `INVALID` değildir. |

Raporun `cryptographicValidity` alanı matematiksel ve X.509 geçerliliğini, `turkishQualifiedSignatureCompliance` alanı ise yapılandırılmış NES/Türkiye politika koşullarını gösterir. Kriptografik olarak doğru bir imza, gerekli QCStatement veya politika OID'si yoksa Türkiye'de güvenli elektronik imza olarak raporlanmaz.

## 7. REST API

### Sertifika

`POST /api/v1/validations/certificates`

```json
{
  "certificate": "BASE64_DER",
  "intermediateCertificates": ["BASE64_DER"],
  "validationTime": "2026-07-28T10:00:00Z"
}
```

### İmza

`POST /api/v1/validations/signatures`

```json
{
  "content": "BASE64_CONTENT",
  "signature": "BASE64_CADES_DER",
  "validationTime": "2026-07-28T10:00:00Z"
}
```

Rapor; `validationId`, ana/kriptografik/Türkiye uygunluk sonuçları, zaman, politika ve güven deposu sürümleri, format/seviye, sertifika yolu, alt kontroller, kanıt SHA-256 özetleri ve insan okunabilir özeti içerir. Tam sözleşme `signature-api/src/main/resources/static/openapi/e-signature-api-v1.yaml` dosyasındadır.

## 8. Yapılandırılabilir politika

`signature-api/src/main/resources/application.yml` içindeki `eimza.validation` alanları:

- Politika sürümü
- Asgari RSA ve EC anahtar büyüklükleri
- QC compliance zorunluluğu
- Kabul edilen sertifika politika OID'leri
- İptal kontrolünün zorunlu olup olmadığı
- İptal kanıtının azami yaşı ve saat toleransı
- OCSP/SİL HTTP zaman aşımı
- Beklenen CAdES imza politikası OID, özet ve URI değerleri

Standart değişikliğinde önce yeni bir politika sürümü tanımlanmalı; tarihsel raporlardaki eski sürüm değiştirilmemelidir.

### 8.1 Politika çalışma modları

Yönetim ekranı ve `GET/PUT /api/v1/admin/validation-policy` uçları üç çalışma modu sunar:

- `STRICT`: Tüm zorunlu ve isteğe bağlı politikalar aktiftir.
- `CUSTOM`: Yalnız `local` ve `test` profillerinde kullanılabilir. Zorunlu kontroller
  kilitli kalır; QC, sertifika politika OID, iptal kanıtı erişilebilirliği ve imza
  politika OID kontrolleri ayrı ayrı aktif/pasif yapılabilir.
- `AUDIT_ONLY`: İsteğe bağlı politikalar pasiftir. Bu mod yalnız `local` ve `test`
  Spring profillerinde kabul edilir ve doğrulama ana sonucu hiçbir zaman `VALID` olmaz.

`dev`, `stage`, `prod` ve diğer profillerde etkin ayar servis katmanında zorunlu
olarak `STRICT` değerine yükseltilir; arayüzde `CUSTOM` ve `AUDIT_ONLY` seçilemez.

Kriptografik imza bütünlüğü, imzalı içerik bağı, imzalayan sertifikanın tespiti,
sertifika yolu/güven kökü ve algoritma güvenliği kapatılamaz. Ortam kısıtı hem
arayüzde hem sunucu servisinde uygulanır. Her değişiklik
`validation_policy_configuration` tablosuna yeni bir sürüm olarak eklenir; önceki
sürümler değiştirilmez. Doğrulama raporundaki `policyMode`, `policyVersion` ve
`passivePolicies` alanları kararın hangi ayarlarla verildiğini gösterir.

## 9. Ağ ve güvenlik kontrolleri

- Yalnız HTTP/HTTPS dağıtım adresleri kabul edilir; kullanıcı bilgisi içeren URI reddedilir.
- DNS çözümlemesinde loopback, link-local, site-local, wildcard ve multicast adresleri reddedilir.
- HTTP yönlendirmesi izlenmez.
- Bağlantı/istek zaman aşımı ve 5 MiB cevap sınırı uygulanır.
- OCSP nonce üretilir; responder nonce döndürürse eşleşmesi zorunludur.
- Doğrudan issuer veya `id-kp-OCSPSigning` yetkili delegated responder imzası doğrulanır.
- SİL issuer'ı, imzası, `thisUpdate/nextUpdate` ve seri numarası kontrol edilir.
- Ham kanıt rapora konmaz; SHA-256 özeti ve zaman/URI üst verileri konur.

Üretimde DNS rebinding riskini kapatmak için kontrollü egress proxy/firewall da kullanılmalıdır; uygulama içi URI kontrolü tek başına ağ güvenlik sınırı sayılmaz.

## 10. Test kapsamı

- Geçerli DB köküyle PKIX yolu ve iyi iptal durumu
- Doğrulanmış iptal durumunda `INVALID`
- İptal servisi kullanılamadığında `INDETERMINATE`
- QC ve sertifika politika OID kontrolü
- Kesin güven deposu snapshot sürümünün raporlanması
- CAdES B-T oluşturma/doğrulama ve bozuk içerik/imza senaryoları
- RFC 3161 token doğrulama negatif/pozitif senaryoları
- Spring/H2/Flyway API entegrasyonu

Gerçek ESHS OCSP/SİL ve TSA uçlarıyla kabul testi, kurumun seçtiği sağlayıcı bilgileri temin edilince ayrıca yapılacaktır.

## 11. Bilinen sınırlar ve Faz 7/8 işleri

- Tek imzalayanlı ayrık CAdES B-LT/B-LTA Faz 7A'da eklenmiştir; XAdES, PAdES ve çoklu imza henüz yoktur.
- Delta/indirect SİL, AIA üzerinden eksik issuer indirme ve tam OCSP responder iptal zinciri henüz desteklenmez.
- Canlı ESHS uçları ve ikinci bağımsız doğrulama ürünüyle birlikte çalışabilirlik testi yapılmamıştır.
- Yerel profilde dış ağa çıkılmaz; iptal kanıtı zorunluysa beklenen sonuç `INDETERMINATE` olur.
- Doğrulama cevabı ayrıntılıdır ancak kanıtların kalıcı `validation_run/validation_check` denetim kaydına yazılması sonraki sertleştirme işidir.
- TSA güven kökleri için ayrı amaç/purpose kataloğu henüz yoktur; DB kökleri adaydır ve TSA EKU kontrolü ayrıca uygulanır.
- Türkiye NES sınıflandırması QC compliance ve yapılandırılmış politika OID'lerine dayanır. Canlı kullanım için seçilen ESHS'lerin resmî politika kataloğu hukuk ve bilgi güvenliği tarafından onaylanmalıdır.
- P2/P3 için 24 saatlik ön/kesin doğrulama worker'ı Faz 7 kapsamındadır.
- Üretim öncesinde SSRF kontrollü egress, dört göz güven deposu yönetimi, audit hash zinciri ve gerçek PostgreSQL testi tamamlanmalıdır.

Bu sınırlar kapanmadan sistem için mevzuata tam uyumluluk veya resmî “güvenli elektronik imza doğrulama aracı” iddiası yapılmamalıdır.

## 12. Faz 6 kabul özeti

Yazılım MVP'si; sertifika ve CAdES doğrulama REST uçları, tarihsel DB güven deposu, PKIX yolu, yapılandırılabilir NES/politika kuralları, OCSP→SİL doğrulaması, RFC 3161 bağlantısı ve ayrıntılı üç durumlu raporla tamamlanmıştır.

Canlı kabul için kalanlar:

1. Seçilen ESHS/TSA sertifika ve uç bilgilerinin sağlanması.
2. Gerçek iyi/iptal/eski/erişilemez OCSP ve SİL örnekleriyle test.
3. En az ikinci bağımsız doğrulayıcıyla karşılaştırma.
4. Hukuk ve bilgi güvenliği politika onayı.
5. Faz 8 güvenlik ve performans kapılarının geçilmesi.
