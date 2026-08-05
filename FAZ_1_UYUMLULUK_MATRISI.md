# Faz 1 — Mevzuat ve Teknik Standart Uyumluluk Matrisi

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Durum: Teknik temel tamamlandı; hukuk ve bilgi güvenliği onayı bekliyor  
> Araştırma kesim tarihi: 28 Temmuz 2026  
> Belge sürümü: 1.0  
> Bağlı belge: [E-İmza API Proje Planı](E_IMZA_PROJE_PLANI.md)
> Bakım süreci: Standart veya mevzuat değiştiğinde
> [Standart Değişikliği Etki Haritası](STANDART_DEGISIKLIGI_ETKI_HARITASI.md)
> izlenir; eski politika sürümleri tarihsel doğrulama için korunur.

## 1. Amaç ve sınır

Bu belge, E-İmza API'sinin Türkiye'de güvenli elektronik imza oluşturma ve doğrulama kabiliyeti için uygulanacak ilk normatif tabanı tanımlar. Gereksinimleri yazılım bileşenleri ve doğrulanabilir kabul testleriyle eşleştirir.

Bu proje mevcut tanımıyla bir **imza oluşturma/doğrulama uygulamasıdır**; Elektronik Sertifika Hizmet Sağlayıcısı (ESHS) değildir. Proje ileride sertifika veya zaman damgası hizmeti sunacak şekilde genişletilirse ESHS yükümlülükleri ayrıca ele alınmalı ve bu matris yeniden hazırlanmalıdır.

Bu belge hukukî görüş değildir. Canlıya geçiş kapısı için:

1. Güncel konsolide mevzuat hukuk uzmanı tarafından,
2. Kriptografik ve operasyonel politika bilgi güvenliği uzmanı tarafından,
3. Kullanılan kart/araçların güvenli elektronik imza oluşturma aracı şartları ürün sahibi tarafından

onaylanmalıdır.

## 2. Normatif kaynak envanteri

| Kod | Kaynak | Esas alınan sürüm/tarih | Projeye etkisi |
|---|---|---|---|
| TR-KANUN-5070 | 5070 sayılı Elektronik İmza Kanunu | Güncel konsolide metin, kontrol: 28.07.2026 | Güvenli elektronik imzanın şartları, hukukî sonucu, NES ve taraf yükümlülükleri |
| TR-YON | Elektronik İmza Kanununun Uygulanmasına İlişkin Usul ve Esaslar Hakkında Yönetmelik | RG 06.01.2005/25692; güncel konsolide metin kontrol edilecek | Güvenli imza oluşturma/doğrulama aracı, ESHS, zaman damgası ve taraf yükümlülükleri |
| TR-TEB | Elektronik İmza ile İlgili Süreçlere ve Teknik Kriterlere İlişkin Tebliğ | RG 06.01.2005/25692; Madde 6 son değişiklik RG 04.10.2025/33037 | İzin verilen algoritma ve asgari anahtar parametreleri; araç ve zaman damgası teknik kriterleri |
| TR-NES | Nitelikli Elektronik Sertifika, SİL ve OCSP İstek/Cevap Mesajları Profilleri Rehberi | 2007/DK-77/207 ve 2012/DK-15/374 değişikliği | NES alanları, sertifika politikası, SİL ve OCSP profili |
| TR-PROFIL | BTK Elektronik İmza Kullanım Profilleri Rehberi | Sürüm 1.0, Haziran 2012; 2012/DK-15/299 | P1–P4 profilleri, doğrulama verisi, kesinleşme süresi ve uzun dönem kullanım |
| RFC-5280 | Internet X.509 PKI Certificate and CRL Profile | RFC 5280 | Sertifika yolu ve SİL/CRL doğrulamasının teknik tabanı |
| RFC-6960 | Online Certificate Status Protocol | RFC 6960 | OCSP istek/cevap doğrulaması |
| RFC-3161 | Time-Stamp Protocol | RFC 3161 ve uygulanabilir güncellemeleri | Zaman damgası isteği ve belirtecinin doğrulanması |
| PKCS11 | Cryptographic Token Interface | Kart/üretici desteğine göre sabitlenecek sürüm | Akıllı kart üzerindeki özel anahtarla imzalama |
| PCSC | PC/SC Workgroup Specifications | Hedef işletim sistemine göre | Okuyucu/kart keşfi ve ATR alma |
| ISO-7816 | Identification cards — Integrated circuit cards | Uygulanabilir bölümler | ATR ve APDU seviyesinde kart iletişimi |
| ETSI-CADES | CAdES standard ailesi | BTK profili için TS 101 733 V1.8.1; modern uygulama için EN 319 122 serisi uyumluluğu ayrıca test edilir | CMS tabanlı gelişmiş imza |
| ETSI-XADES | XAdES standard ailesi | BTK profili için TS 101 903 V1.4.2; modern uygulama için EN 319 132 serisi uyumluluğu ayrıca test edilir | XML tabanlı gelişmiş imza |
| ETSI-PADES | PAdES standard ailesi | BTK profili için TS 102 778-3 V1.2.1, -4 V1.1.2, -5 V1.1.2; modern uygulama için EN 319 142 serisi uyumluluğu ayrıca test edilir | PDF tabanlı gelişmiş imza |
| ETSI-VAL | Signature validation procedures and policies | EN 319 102-1 ve seçilecek ilgili sürüm | Doğrulama durumları, süreç ve raporlama modeli |
| ETSI-ALG | Cryptographic suites | Tebliğ Madde 6'nın atıf yaptığı TS 119 312; uygulanabilir sürüm hukuk/güvenlik onayında sabitlenir | Algoritma uygunluğu ve kullanım süresi |

### 2.1 Sürümleme kuralı

- BTK profilinin açıkça belirttiği eski ETSI sürümleri, Türkiye profiline uyumluluk için görmezden gelinmeyecektir.
- Yeni ETSI EN standartları eski BTK politika OID'lerini veya özelliklerini kendiliğinden ikame etmiş sayılmayacaktır.
- Uygulama, ürettiği imzanın hem seçilen BTK profiline hem seçilen modern ETSI baseline seviyesine uygunluğunu ayrı sonuçlar olarak raporlayacaktır.
- Çelişki halinde mevzuat sahibi ve hukuk uzmanından yazılı karar alınmadan “uyumlu” sonucu verilmeyecektir.
- Her politika kaydında kaynak, sürüm, yayın tarihi, yürürlük başlangıcı, yürürlük sonu ve belge özeti saklanacaktır.

## 3. Ürün profil kararı

Belge türü henüz seçilmediği için CAdES/XAdES/PAdES arasında nihai öncelik kararı verilmemiştir. Faz 1 politika tabanı üçünü de kapsar.

### 3.1 BTK P1–P4 profilleri

| Profil | Kullanım | Zaman damgası | İptal bilgisi | Kesinleşme | Tarihsel biçim karşılığı | Ürün kararı |
|---|---|---|---|---|---|---|
| P1 | Anlık, düşük güvenlik ihtiyacı; gelecekte doğrulama beklenmez | Yok | SİL veya tercihen gerçek zamanlı OCSP | Uygulanmaz | BES | Varsayılan üretim profili olmayacak |
| P2 | Kısa ömürlü; OCSP erişimi yok | Var | SİL | Uygulanır | ES-T | İhtiyaç halinde desteklenecek |
| P3 | Uzun ömürlü; SİL tabanlı | Var | SİL ve gömülü doğrulama verisi | Uygulanır | Önce ES-T, kesinleşme sonrası ES-XL | Uzun dönem saklama için aday |
| P4 | Uzun ömürlü; gerçek zamanlı OCSP erişimi var | Var | OCSP ve gömülü doğrulama verisi | Uygulanmaz | ES-XL/PAdES-LTV | **Varsayılan hedef profil adayı** |

P2, P3 ve P4 imzalarında BTK rehberindeki ilgili açık politika OID'si, politika özeti ve politika erişim adresi imzaya eklenmeli ve doğrulamada kontrol edilmelidir. Rehberde görülen sürüm 1 OID'leri:

- P2: `2.16.792.1.61.0.1.5070.3.1.1`
- P3: `2.16.792.1.61.0.1.5070.3.2.1`
- P4: `2.16.792.1.61.0.1.5070.3.3.1`

> OID adlandırmasındaki “Politika-1/2/3”, sırasıyla ürün profil adları P2/P3/P4 ile ilişkilidir. Üretimde OID, özet ve URL üçlüsü BTK'nın güncel yayımladığı politika dosyasından yeniden doğrulanmadan kullanılmayacaktır.

### 3.2 Modern ETSI seviye eşlemesi

Yeni ETSI terminolojisiyle hedef seviyeler:

| İşlev | CAdES | XAdES | PAdES |
|---|---|---|---|
| Temel kriptografik imza | B-B | B-B | B-B |
| Güvenilir imza zamanı | B-T | B-T | B-T |
| Uzun dönem doğrulama verisi | B-LT | B-LT | B-LT |
| Arşiv/algoritma eskimesine karşı koruma | B-LTA | B-LTA | B-LTA |

Bu eşleme kavramsaldır. BTK P1–P4 uygunluğu ile ETSI B-B/B-T/B-LT/B-LTA uygunluğu ayrı kurallar ve ayrı test sonuçlarıdır.

### 3.3 Faz 1 varsayılan ürün politikası

Kullanım senaryosu aksi yönde karar vermedikçe:

- Üretimde salt P1/BES kullanılmayacak.
- İmza en az güvenilir zaman damgası içerecek.
- Uzun dönem saklanacak belgelerde doğrulama verisi gömülecek.
- Gerçek zamanlı ve doğrulanmış OCSP mümkünse P4 benzeri akış tercih edilecek.
- OCSP uygun değilse SİL tabanlı P3 akışı; imza zamanından sonra politika ile belirlenen kesinleşme sürecinin ardından tamamlanacak.
- Sadece dosya türü belirlendikten sonra CAdES, XAdES veya PAdES varsayılanı seçilecek.

## 4. Algoritma politikası

### 4.1 28 Temmuz 2026 itibarıyla mevzuat tabanı

Teknik Kriterler Tebliği Madde 6 için belirlenen taban:

| Amaç | İzin verilen/asgari değer |
|---|---|
| İmza sahibinin RSA anahtarı | En az 2048 bit |
| İmza sahibinin DSA anahtarı | En az 3072 bit |
| İmza sahibinin eliptik eğri DSA anahtarı | En az 256 bit |
| ESHS RSA anahtarı | En az 4096 bit ve imzalamada RSA-PSS |
| ESHS DSA anahtarı | En az 3072 bit |
| ESHS eliptik eğri DSA anahtarı | En az 256 bit |
| Özet | SHA2-256, SHA2-384, SHA2-512, SHA3-256, SHA3-384 veya SHA3-512 |
| Mevcut hükmün geçerlilik sonu | 31 Aralık 2027 |

Proje ESHS olmadığı için ESHS özel anahtar üretmeyecektir; buna rağmen güven zinciri ve zaman damgası doğrulamasında karşılaşılan ESHS imzaları yürürlükteki/doğrulama zamanındaki kurala göre değerlendirilecektir.

### 4.2 Ürün varsayılanları

- Yeni imzada varsayılan özet: `SHA-256`.
- Kart ve sertifika destekliyorsa imza algoritması sertifikadaki anahtar türüyle uyumlu seçilir.
- RSA anahtarında 2048 bit altı yeni imza reddedilir.
- DSA anahtarında 3072 bit altı yeni imza reddedilir.
- EC anahtarında 256 bit altı yeni imza reddedilir.
- SHA-1, MD5 ve Tebliğ listesindeki olmayan özetlerle yeni imza üretilmez.
- Algoritma uygunluğu yalnız ada göre değil; parametre, anahtar boyu, eğri ve değerlendirme zamanına göre yapılır.
- İmza doğrulamada eski/yasak algoritma “kriptografik bütünlük doğru” sonucundan ayrı bir **politika ihlali** üretir.
- 31 Aralık 2027 için en geç 1 Temmuz 2027'de zorunlu politika gözden geçirme alarmı oluşturulur.

### 4.3 Politika veri modeli

Her algoritma kuralı en az şu alanları taşımalıdır:

`algorithmOid`, `usage`, `minimumKeySize`, `allowedParameters`, `validFrom`, `validUntil`, `sourceDocument`, `sourceVersion`, `severity`, `policyVersion`.

Kurallar kod içine dağınık sabitler olarak yazılmayacak; imzalı/sürümlü politika paketi olarak yayımlanacaktır.

## 5. Sertifika ve güven politikası

### 5.1 NES kabulü

Bir sertifika yalnızca ayrıştırılabildiği veya zinciri teknik olarak kurulduğu için NES sayılmaz. Doğrulama motoru:

1. Sertifikanın imza zamanında geçerlilik aralığında olduğunu,
2. BTK profiline göre gerekli nitelikli sertifika alanlarını ve politika OID'lerini,
3. Anahtar kullanımının elektronik imzaya izin verdiğini,
4. Sertifikadaki kullanım ve maddi kapsam kısıtlarını,
5. Zincirin, ilgili doğrulama zamanında güvenilen ve BTK kapsamında yönetilen ESHS köküne ulaştığını,
6. Zincirdeki tüm uygulanabilir sertifikaların iptal durumunu

ayrı kontroller olarak yürütür.

### 5.2 Güven deposu

- İşletim sisteminin genel kök deposu tek başına hukukî güven kaynağı kabul edilmez.
- Türkiye güvenli elektronik imzası için ayrı, sürümlü bir `TR-ESIGN-TRUST` deposu tutulur.
- Depo PostgreSQL üzerinde kök ve alt kök sertifika üyeliklerini immutable snapshot'lar halinde saklar.
- Her kök/ara sertifika için kaynak URL, SHA-256 parmak izi, edinme zamanı, etkinlik aralığı ve onay kaydı tutulur.
- Güven deposu güncellemesi çift kontrol/onay ve denetim kaydı gerektirir.
- Tarihsel doğrulama için eski fakat ilgili zamanda güvenilir sertifikalar silinmez; durumları zaman aralığıyla modellenir.
- API silme işlemi fiziksel silme değil, sertifikayı yeni snapshot'tan çıkarma olarak uygulanır.
- Doğrulama zamanı için uygun snapshot bulunamazsa işletim sistemi deposuna sessiz fallback yapılmaz; sonuç belirsizdir.
- Yabancı sertifikalar varsayılan olarak Türkiye'de NES sayılmaz; 5070 kapsamındaki kabul koşulu ayrıca kanıtlanmadıkça sonuç `QUALIFICATION_UNDETERMINED` olur.

### 5.3 Yol oluşturma

- RFC 5280 yol oluşturma ve doğrulama uygulanır.
- Sertifika adı eşleme, basic constraints, path length, key usage, name constraints ve kritik eklentiler kontrol edilir.
- Birden fazla olası yol varsa seçilen yol ve reddedilen yollar rapora eklenir.
- AIA üzerinden sertifika indirme kontrollü, boyut/zaman sınırlı ve SSRF korumalı olur.

## 6. İptal kontrolü politikası

### 6.1 OCSP

- İstek doğru issuer name hash, issuer key hash ve seri numarasıyla oluşturulur.
- Nonce desteği sağlayıcı uyumluluğuna göre politika ile yönetilir.
- Yanıtın `producedAt`, `thisUpdate` ve varsa `nextUpdate` alanları kontrol edilir.
- OCSP yanıt imzası ve yetkili responder sertifikası/zinciri doğrulanır.
- `good`, `revoked`, `unknown`, ağ hatası ve protokol hatası ayrı sonuçlardır.
- Önbellek anahtarı sertifika + issuer + responder + doğrulama zamanı bağlamını içerir.
- Süresi geçmiş yanıt yeni doğrulama için kullanılmaz.

### 6.2 SİL/CRL

- Dağıtım noktası kontrollü biçimde okunur.
- SİL imzası, issuer, AKI/SKI ilişkisi, `thisUpdate` ve `nextUpdate` kontrol edilir.
- Delta CRL kullanılıyorsa base/delta ilişkisi doğrulanır.
- İndirme başarısızlığı `REVOKED` veya `VALID` olarak yorumlanmaz.
- İmza zamanı için arşivlenmiş iptal verisi kullanılıyorsa verinin o zamanı kapsadığı kanıtlanır.

### 6.3 Kesinleşme

BTK P2/P3 politikalarında zaman damgasından sonra 24 saatlik kesinleşme süresi esas alınır. Bu akış iki aşamalıdır:

1. **Ön doğrulama:** İş sürecinin devam edip etmeyeceğine dair geçici sonuç.
2. **Kesin doğrulama:** Zaman damgası zamanından 24 saat sonra NES, TSA sertifikası ve ilgili güven zincirlerinin iptal durumu o zaman referans alınarak yeniden kontrol edilir.

P4'te gerçek zamanlı OCSP koşulları sağlandığında kesinleşme beklenmez. “Gerçek zamanlı” niteliği yalnız HTTP yanıtı alınmasıyla varsayılmaz; ESHS hizmet/politika bilgisiyle doğrulanır.

## 7. Zaman damgası politikası

- Yalnız 5070 kapsamında faaliyet gösteren, yapılandırılmış ve güven deposunda onaylı ESHS/TSA servisleri kullanılır.
- İstek RFC 3161 `TimeStampReq` yapısında oluşturulur.
- Mesaj özeti yerelde hesaplanır; belgenin kendisi TSA'ya gönderilmez.
- Yanıtta durum, `messageImprint`, algoritma OID'si, nonce, istenmişse politika OID'si ve sertifika talebi kontrol edilir.
- `genTime`, seri numarası ve TSA politika OID'si kaydedilir.
- TimeStampToken CMS imzası, TSA sertifikasının extended key usage değeri, zinciri ve iptal durumu doğrulanır.
- TSA HTTP başarısı zaman damgasının geçerli olduğu anlamına gelmez.
- Her TSA bağlantısı için kimlik doğrulama, zaman aşımı, yeniden deneme ve devre kesici politikası ayrı yapılandırılır.
- P2/P3 kesinleştirme işi kalıcı bir görev olarak zaman damgasından 24 saat sonrasına planlanır.

## 8. İmza oluşturma gereksinimleri

| ID | Gereksinim | Bileşen | Kabul testi |
|---|---|---|---|
| SIG-CRE-001 | Özel anahtar akıllı karttan çıkarılmayacak | `smartcard-agent` | PKCS#11 imzası sonrası anahtar dışa aktarma girişimi başarısız |
| SIG-CRE-002 | PIN yalnız yerel aracıda alınacak; API/loglara girmeyecek | `smartcard-agent`, `audit` | Trafik ve log taramasında PIN bulunmuyor |
| SIG-CRE-003 | Kullanıcının onayladığı belge ile imzalanan özet bağlanacak | `smartcard-agent`, `signature-core` | Özet/belge değiştirme saldırısı reddediliyor |
| SIG-CRE-004 | Sertifika, anahtar türü ve algoritma politikası imza öncesi kontrol edilecek | `signature-core`, `certificate-validation` | RSA-1024 ve SHA-1 ile üretim reddediliyor |
| SIG-CRE-005 | İmzalayan NES veya referansı formata uygun gömülecek | Format modülleri | Profil örneği haricî doğrulayıcıda geçiyor |
| SIG-CRE-006 | P2/P3/P4 için doğru politika OID/özet/URL imzalı özellik olacak | Format modülleri, `trust-config` | Değiştirilmiş politika özeti doğrulamada reddediliyor |
| SIG-CRE-007 | Hedef profil gerektiriyorsa RFC 3161 zaman damgası eklenecek | `timestamp-client` | `messageImprint` farklı yanıt reddediliyor |
| SIG-CRE-008 | Profilin istediği sertifika ve iptal verileri eklenecek | Format modülleri | B-LT/P3/P4 örneği çevrimdışı tarihsel doğrulanabiliyor |
| SIG-CRE-009 | Çoklu imzada önceki imzalar bozulmayacak | Format modülleri | İkinci imzadan sonra ilk imza bütünlüğü geçerli |
| SIG-CRE-010 | Üretim çıktısı format ve politika raporuyla dönecek | `signature-api` | API cevabı format, seviye, politika ve algoritmayı içeriyor |

## 9. İmza ve sertifika doğrulama gereksinimleri

| ID | Gereksinim | Bileşen | Kabul testi |
|---|---|---|---|
| SIG-VAL-001 | İmzalı veri referansı ve kriptografik bütünlük doğrulanacak | Format modülleri | Bir baytı değişmiş içerik `INVALID` |
| SIG-VAL-002 | Format, seviye ve BTK profili ayrı tespit edilecek | `signature-core` | B-T/P2 örneği iki sınıflandırmayı da döndürüyor |
| SIG-VAL-003 | İmza politikası OID, özet ve erişim adresi doğrulanacak | `trust-config` | Bilinmeyen OID `INDETERMINATE/POLICY_NOT_FOUND` |
| CERT-VAL-001 | X.509 yolu doğrulama zamanına göre kurulacak | `certificate-validation` | Ara sertifikası eksik ve mevcut senaryolar ayrılıyor |
| CERT-VAL-002 | NES niteliği, key usage ve kısıtlar raporlanacak | `certificate-validation` | Niteliksiz sertifika kriptografik olarak doğru olsa da güvenli e-imza sayılmıyor |
| CERT-VAL-003 | Zincirin tüm uygulanabilir sertifikalarında iptal kontrolü yapılacak | `certificate-validation` | İptal ara sertifika sonucu geçersiz yapıyor |
| REV-001 | OCSP imzası, responder yetkisi ve tazeliği doğrulanacak | `certificate-validation` | Sahte/eski OCSP cevabı reddediliyor |
| REV-002 | SİL imzası ve tazeliği doğrulanacak | `certificate-validation` | Süresi geçmiş veya yanlış issuer SİL belirsiz/geçersiz kanıt |
| TSA-VAL-001 | Zaman damgası özeti, imzası, zinciri ve politikası doğrulanacak | `timestamp-client` | Başka belgeye ait token reddediliyor |
| TIME-001 | İddia edilen imza zamanı ile kanıtlı zaman ayrılacak | `signature-core` | Sadece signingTime içeren imza güvenilir zaman sayılmıyor |
| TIME-002 | P2/P3 ön ve kesin doğrulama durumları ayrılacak | `signature-core`, scheduler | 24 saat öncesi `PROVISIONAL`, sonrası kesin sonuç |
| REP-001 | Sonuç `VALID`, `INVALID`, `INDETERMINATE` ana durumlarından biri olacak | `signature-api` | Ağ kesintisi `INVALID` olarak dönmüyor |
| REP-002 | Her alt kontrol kanıt, kod ve açıklama içerecek | `signature-api` | JSON raporu zincir, iptal, zaman ve politika alt sonuçlarını içeriyor |

## 10. Akıllı kart/ATR uyumluluk gereksinimleri

ATR mevzuat açısından güven kararının kaynağı değildir; yalnız teknik kart profili seçimine yardımcı olur.

| ID | Gereksinim | Kabul testi |
|---|---|---|
| CARD-001 | ATR normalize edilerek tam veya açıkça tanımlı maskeyle eşleştirilir | Boşluk/büyük-küçük harf farkı aynı profile gider |
| CARD-002 | Birden fazla profil eşleşirse otomatik imza atılmaz | Belirsiz ATR kullanıcı/yönetici seçimi ister |
| CARD-003 | Profil PKCS#11 kitaplığı, slot seçimi ve desteklenen mekanizmaları tanımlar | Yanlış sürücü açık hata verir |
| CARD-004 | PKCS#11 kitaplık yolu yönetici kontrollü allowlist'ten gelir | İstemcinin gönderdiği rastgele DLL yüklenmez |
| CARD-005 | Kart mekanizması algoritma politikasıyla kesiştirilir | Yalnız SHA-1 destekleyen kartla yeni imza reddedilir |
| CARD-006 | ATR güvenilir kart/sertifika sonucu üretmez | Sahte/aynı ATR, NES doğrulamasını atlatamaz |

## 11. Doğrulama sonuç modeli

Ana sonuçlar:

- `VALID`: Kriptografik, sertifika, iptal, zaman ve seçilen politika kontrollerinin tümü başarılı.
- `INVALID`: İmza/belge bütünlüğü bozuk, sertifika ilgili zamanda iptal edilmiş veya kesin bir politika ihlali var.
- `INDETERMINATE`: Gerekli kanıt yok, güven yolu kurulamıyor, iptal servisine erişilemiyor, kesinleşme bekleniyor ya da politika belirlenemiyor.

Önerilen alt kodlar:

`CRYPTO_FAILURE`, `CONTENT_MISMATCH`, `CERT_EXPIRED_AT_VALIDATION_TIME`, `CERT_REVOKED`, `CERT_STATUS_UNKNOWN`, `TRUST_ANCHOR_NOT_FOUND`, `NOT_QUALIFIED_CERTIFICATE`, `KEY_USAGE_VIOLATION`, `ALGORITHM_NOT_ALLOWED`, `POLICY_NOT_FOUND`, `POLICY_HASH_MISMATCH`, `TIMESTAMP_INVALID`, `TIMESTAMP_MISSING`, `REVOCATION_DATA_STALE`, `REVOCATION_SERVICE_UNAVAILABLE`, `GRACE_PERIOD_PENDING`, `FORMAT_UNSUPPORTED`.

API ayrıca iki ayrı alan döndürmelidir:

- `cryptographicValidity`
- `turkishQualifiedSignatureCompliance`

Böylece kriptografik olarak doğru fakat Türkiye güvenli elektronik imza koşullarını karşılamayan bir imza yanlışlıkla “geçerli güvenli e-imza” diye sunulmaz.

## 12. Uyum test paketleri

Her format/profil için en az:

1. Geçerli örnek,
2. İçeriği değiştirilmiş örnek,
3. İmza değerini değiştirilmiş örnek,
4. Yanlış/eksik politika OID'li örnek,
5. Politika özeti bozuk örnek,
6. Süresi geçmiş fakat geçerli zaman damgasıyla tarihsel olarak doğrulanabilir sertifika,
7. İmza öncesinde iptal edilmiş sertifika,
8. İmza sonrasında iptal edilmiş sertifika,
9. Zincirinde iptal edilmiş ara sertifika,
10. Eski veya sahte OCSP cevabı,
11. Süresi geçmiş/yanlış imzalı SİL,
12. Yanlış `messageImprint` içeren zaman damgası,
13. Güvenilmeyen TSA,
14. SHA-1 veya yetersiz anahtar boyu,
15. Ağ kesintisinde belirsiz sonuç,
16. P2/P3 için 24 saat öncesi ve sonrası doğrulama

örneği bulunacaktır.

Birlikte çalışabilirlik testinde en az iki bağımsız doğrulama motoru kullanılmalı; bunlardan biri seçilmesi halinde Avrupa Komisyonu DSS olabilir. Türkiye profil uygunluğu ayrıca proje testleriyle doğrulanır.

## 13. Uygulama kapıları

| Kapı | Tamamlanma şartı | Durum |
|---|---|---|
| G1 Teknik mevzuat tabanı | Kaynak envanteri, algoritma ve doğrulama politikası yazılı | Tamamlandı |
| G2 Ürün profil seçimi | Belge türü, imza ömrü, P profili ve format onaylı | Bekliyor — Faz 0 ürün kararı |
| G3 Hukuk onayı | Güncel mevzuat ve ürün sınıflandırması yazılı onaylı | Bekliyor |
| G4 Güvenlik onayı | Algoritma, güven deposu, iptal ve TSA politikası onaylı | Bekliyor |
| G5 Birlikte çalışabilirlik | Pozitif/negatif test paketi en az iki motorla başarılı | Bekliyor — uygulama sonrası |

Faz 2 mimari çalışması G1 ile başlayabilir. Canlıya çıkış için G2–G5 zorunludur.

## 14. Açık kararlar

| No | Karar | Son tarih/faz |
|---|---|---|
| U-001 | İlk belge formatı: CAdES, XAdES veya PAdES | CAdES B-T MVP olarak seçildi; ürün onayı bekliyor |
| U-002 | Varsayılan BTK profili P3 mü P4 mü? | Kullanım ortamı/ESHS kapasitesi öğrenilince |
| U-003 | İmza saklama ömrü ve B-LT/B-LTA ihtiyacı | Faz 0 kapanışı |
| U-004 | Desteklenecek ESHS, TSA ve gerçek zamanlı OCSP servisleri | Faz 0 kapanışı |
| U-005 | Hangi PKCS#11 kartları ve imza mekanizmaları desteklenecek? | Faz 4 öncesi |
| U-006 | BTK politika dosyalarının güncel dağıtım adresleri ve özetleri | İlk imza formatı geliştirilmeden önce |
| U-007 | TS 119 312'nin projede sabitlenecek baskısı | Güvenlik onayı |
| U-008 | Uygulama “güvenli elektronik imza doğrulama aracı” olarak hangi resmî/ürün kabul sürecine tabi? | Hukuk onayı |

## 15. Kaynak bağlantıları

- [BTK — Elektronik İmza Mevzuatı](https://www.btk.gov.tr/elektronik-imza-mevzuati)
- [BTK — Elektronik İmza Kullanım Profilleri Rehberi, Sürüm 1.0](https://www.btk.gov.tr/uploads/pages/elektronik-imza-kullanim-profilleri-rehberi-5a33ff5b59f93.pdf)
- [Mevzuat Bilgi Sistemi — 5070 sayılı Kanun](https://www.mevzuat.gov.tr/mevzuat?MevzuatNo=5070&MevzuatTertip=5&MevzuatTur=1)
- [Mevzuat Bilgi Sistemi — Teknik Kriterler Tebliği](https://www.mevzuat.gov.tr/mevzuat?MevzuatNo=8716&MevzuatTertip=5&MevzuatTur=9)
- [IETF — RFC 5280](https://www.rfc-editor.org/rfc/rfc5280)
- [IETF — RFC 6960](https://www.rfc-editor.org/rfc/rfc6960)
- [IETF — RFC 3161](https://www.rfc-editor.org/rfc/rfc3161)
- [ETSI Standards Search](https://www.etsi.org/standards-search)
- [European Commission — Digital Signature Service Documentation](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html)

## 16. Değişiklik günlüğü

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 28.07.2026 | İlk normatif envanter, profil/algoritma/güven/iptal/zaman damgası politikaları, gereksinim-test matrisi ve uygulama kapıları oluşturuldu. |
