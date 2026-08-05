# Faz 7 — CAdES Uzun Dönem Doğrulama

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Durum: Faz 7A CAdES B-LT/B-LTA yazılım MVP'si tamamlandı; canlı TSA/ESHS ve haricî doğrulayıcı kabulü bekliyor  
> Tarih: 28 Temmuz 2026  
> Seçilen sıra: CAdES B-T → B-LT → B-LTA → arşiv zaman damgası yenileme; PAdES/XAdES sonraki alt fazlar

## 1. Amaç

Faz 7A, Faz 5'te üretilen ayrık CAdES B-T imzasının sertifika ve iptal kanıtlarıyla çevrimdışı/tarihsel doğrulanabilir hale getirilmesini ve bu materyalin arşiv zaman damgasıyla korunmasını sağlar.

Uygulanan sıra:

1. CAdES B-T imzası ve `signature-time-stamp` varlığı kontrol edilir.
2. İmzayı, TSA'yı ve iptal kanıtlarını doğrulamak için kullanılan sertifikalar toplanır.
3. Kullanılan SİL/CRL ve OCSP cevapları toplanır.
4. Sertifikalar root `SignedData.certificates` alanına, iptal değerleri `SignedData.crls` alanına DER olarak ve tekrarsız eklenir.
5. Sonuç CAdES B-LT olarak raporlanır.
6. B-LTA için tüm sertifika, iptal ve unsigned attribute değerlerinin SHA-256 indeksleriyle `ATSHashIndexV3` oluşturulur.
7. Ayrık içerik özeti, değişmez `SignerInfo` alanları ve `ATSHashIndexV3` ETSI sırasıyla birleştirilir.
8. Bu girdinin özeti için RFC 3161 zaman damgası alınır.
9. `ats-hash-index-v3`, TSA token imzalayanının unsigned attributes alanına eklenir.
10. Token, root imzalayanın `archive-time-stamp-v3` unsigned attribute'u olarak eklenir.
11. Yenilemede önceki arşiv zaman damgaları da yeni indeks tarafından korunur; eski değerler değiştirilmez.
12. Yükseltmenin giriş/çıkış SHA-256 özetleri ve kanıt sayıları DB denetim kaydına yazılır.

## 2. Sabitlenen normatif taban

| Kaynak | Sürüm | Uygulanan konu |
|---|---|---|
| ETSI EN 319 122-1 | V1.3.1, 2023-06 | CAdES B-LT/B-LTA, `SignedData.certificates`, `SignedData.crls`, ATSHashIndexV3 ve archive-time-stamp-v3 |
| ETSI EN 319 102-1 | V1.4.1, 2024-06 | Uzun dönem doğrulama ve proof-of-existence yaklaşımı |
| ETSI TS 119 102-2 | V1.4.1, 2023-06 | Doğrulama raporu modeli |
| RFC 3161 / RFC 5816 | 2001 / 2010 | Arşiv zaman damgası protokolü ve SHA-2 çevikliği |
| RFC 5280 / RFC 6960 / RFC 5940 | 2008 / 2013 / 2010 | X.509, SİL, OCSP ve OCSPResponse'un CMS içinde taşınması |

ETSI EN 319 122-1 V1.3.1 Baseline B-LT/B-LTA için eski `certificate-values`, `complete-certificate-references`, `revocation-values` ve `complete-revocation-references` attribute'larının bulunmamasını; doğrulama değerlerinin root `SignedData` alanlarında taşınmasını ister. Kod bu güncel Baseline modelini kullanır.

## 3. Gerçeklenen çekirdek

### `CadesLongTermService`

- `augmentToBaselineLT`: B-T imzasına tekrarsız X.509, SİL ve OCSP değerleri ekler.
- `augmentToBaselineLTA`: B-LT imzasına ilk `archive-time-stamp-v3` değerini ekler.
- `renewBaselineLTA`: Mevcut B-LTA imzasına yeni bir arşiv zaman damgası ekler.
- Girdi imzasındaki signed attributes ve imza değerini değiştirmez.
- Tek imzalayanlı ayrık CAdES modelini zorunlu tutar.

### `CadesLongTermVerifier`

- B-T, B-LT ve B-LTA seviyesini tespit eder.
- Her `ATSHashIndexV3` bileşenini yeniden hesaplar.
- İndeksteki her sertifika, iptal ve unsigned attribute referansının imzada bulunduğunu kontrol eder.
- Archive time-stamp message imprint girdisini yeniden oluşturur.
- Token özeti, imzası, TSA EKU'su, sertifika yolu ve politikasını doğrular.
- Birden fazla yenileme zaman damgasını sırasıyla doğrular.

### Gömülü iptal kanıtı

`CadesSignatureInspector`, root `SignedData` içindeki:

- X.509 sertifikalarını,
- SİL/CRL değerlerini,
- RFC 5940 `id-ri-ocsp-response` OCSP değerlerini

çıkarır. `EmbeddedFirstRevocationDataProvider`, sertifika doğrulamasında önce bu kanıtları imza/issuer/tazelik açısından doğrular; uygun gömülü kanıt yoksa Faz 6 ağ sağlayıcısına döner.

## 4. REST API

`POST /api/v1/signatures/cades/augmentations`

Hedefler:

- `B_LT`
- `B_LTA`
- `RENEW_B_LTA`

Örnek B-LT isteği:

```json
{
  "signature": "BASE64_CADES_B_T",
  "target": "B_LT",
  "certificates": ["BASE64_DER_CERTIFICATE"],
  "revocationValues": [
    {
      "type": "CRL",
      "value": "BASE64_DER_CRL"
    }
  ]
}
```

B-LTA ve yenileme için ayrık `content` zorunludur. `timestampPolicyOid` verilmezse sunucunun sürümlü TSA politikası kullanılır.

Yanıt:

- Denetim `augmentationId`
- Base64 yükseltilmiş CAdES
- Seviye
- Kaynak/sonuç SHA-256 özetleri
- Sertifika, iptal değeri ve arşiv zaman damgası sayıları
- Son arşiv zaman damgası zamanı ve politika OID'si

Üretimde uç `eimza.sign` OAuth scope'u gerektirir.

## 5. TSA yapılandırması

| Ortam değişkeni | Amaç |
|---|---|
| `EIMZA_TSA_ENABLED` | B-LTA servisini etkinleştirir |
| `EIMZA_TSA_ENDPOINT` | HTTPS RFC 3161 TSA adresi |
| `EIMZA_TSA_PROVIDER_ID` | Raporlama sağlayıcı kimliği |
| `EIMZA_TSA_REQUEST_TIMEOUT` | İstek zaman aşımı |
| `EIMZA_TSA_AUTHORIZATION_HEADER` | Sır deposundan gelen Authorization değeri |
| `EIMZA_TSA_ARCHIVE_POLICY_OID` | Varsayılan arşiv TSA politika OID'si |

TSA sertifika yolu, istek anındaki DB güven deposu snapshot'ındaki `ROOT` sertifikalarıyla doğrulanır. Yönlendirmeler kapalıdır; endpoint HTTPS olmak zorundadır. Yetkilendirme sırrı kaynak kodda veya varsayılan YAML'da tutulmaz.

## 6. Kalıcı denetim kaydı

Flyway V4 ile `signature_augmentation` tablosu eklenmiştir:

- İşlem UUID'si
- Kaynak ve sonuç SHA-256 özetleri
- Hedef seviye
- Sertifika ve iptal kanıtı sayıları
- Arşiv zaman damgası sayısı ve politika OID'si
- Oluşturulma zamanı

Belgenin, imzanın veya ham iptal kanıtının kendisi bu tabloda saklanmaz. Uzun süreli ham kanıt saklama gereksinimi kurumun veri saklama/KVKK politikasıyla ayrıca onaylanmalıdır.

## 7. Testler

- B-T → B-LT yükseltme
- Sertifika/SİL değerlerinin tekrarsız gömülmesi
- İptal kanıtı olmadan B-LT üretiminin reddi
- B-LT → B-LTA ATSHashIndexV3 üretimi
- Archive time-stamp imprint, token ve güven yolu doğrulaması
- İkinci arşiv zaman damgasıyla yenileme
- İki arşiv zaman damgasının doğrulanması
- Gömülü SİL'in ağ servisine gitmeden sertifika durumunda kullanılması
- TSA yapılandırılmamış API'de kontrollü `TSA_NOT_CONFIGURED`
- Flyway V4 ve JPA denetim kaydı

## 8. Kritik güvenlik kuralları

- Yükseltme, mevcut imzalayanın signed attributes veya imza değerini değiştirmez.
- B-LT iptal kanıtı olmadan üretilemez.
- B-LTA, B-LT iptal materyali olmadan üretilemez.
- İlk arşiv zaman damgası ile yenileme operasyonları ayrıdır.
- `ATSHashIndexV3` algoritması Faz 7 politikasında SHA-256'dır; farklı algoritma açıkça reddedilir.
- B-LTA için ayrık özgün içerik zorunludur.
- Arşiv token'ında tam olarak bir TSA imzalayanı ve bir ATSHashIndexV3 aranır.
- Eski arşiv zaman damgası veya kanıt değerleri yenilemede üzerine yazılmaz.

## 9. Bilinen sınırlar

- Faz 7A yalnız tek imzalayanlı ayrık CAdES B-LT/B-LTA'yı kapsar. PAdES, XAdES, paralel/seri imza ve countersignature sonraki alt fazlardadır.
- API, B-LT sertifika ve iptal değerlerini çağırandan alır; ESHS/TSA zincirlerinin bütün kanıtlarını otomatik toplama orkestrasyonu henüz yoktur.
- Yapısal olarak geçerli bir SİL/OCSP'nin doğru sertifikaya ait olduğu asıl doğrulama çağrısında kontrol edilir. Üretim iş akışı yükseltmeden önce Faz 6 `VALID` raporunu zorunlu kapı yapmalıdır.
- TSA sertifikasının iptal kanıtını ve delegated OCSP responder zincirini tamamen otomatik toplama henüz yoktur.
- Delta/indirect SİL ve Evidence Record Syntax desteklenmez.
- Arşiv zaman damgası yenileme REST ile elle tetiklenir; algoritma/sertifika sona erme tarihine göre scheduler henüz yoktur.
- Eski algoritmalar kriptografik olarak kullanılamaz hale geldikten sonraki geçmiş doğrulama, tam proof-of-existence karar ağacı ve politika geçmişi Faz 7B/8 sertleştirmesidir.
- Canlı TSA/ESHS, gerçek PostgreSQL ve bağımsız DSS/başka doğrulayıcıyla birlikte çalışabilirlik testi yapılmamıştır.
- TSA güven kökü için DB'de ayrı `purpose=TSA` ayrımı henüz yoktur.

Bu sınırlar kapanmadan “ETSI CAdES B-LTA tam uyumlu” veya mevzuata tam uygun uzun dönem koruma hizmeti iddiası yapılmamalıdır.

## 10. Sonraki sıra

1. Canlı ESHS/TSA kanıt toplama ve birlikte çalışabilirlik kabulü.
2. Otomatik kanıt toplama ve yenileme scheduler'ı.
3. PAdES B-T/B-LT/B-LTA.
4. XAdES B-T/B-LT/B-LTA.
5. Çoklu/seri imza ve tarihsel proof-of-existence karar ağacı.
