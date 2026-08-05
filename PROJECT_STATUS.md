# E-İmza Platformu — Güncel Proje Durumu

> Durum tarihi: 5 Ağustos 2026  
> Yazılım sürümü: `0.1.0-SNAPSHOT`  
> Şema sürümü: Flyway V12  
> Sonraki faz: Faz 12 — üretim kabulü ve birlikte çalışabilirlik

Bu dosya projenin güncel durumundaki tek özet kaynaktır. Faz dosyaları ayrıntılı ve tarihsel
kararları saklar; eski ifadeler bu belgeyle birlikte yorumlanmalıdır.

## 1. Yönetici özeti

Faz 1–11 için planlanan yazılım bileşenleri uygulanmıştır. Platform CAdES, XAdES ve PAdES
imza üretir/doğrular; client-side akıllı kart, server-side akıllı kart/HSM, güven deposu,
TSA, politika yönetimi, offline masaüstü ve çoklu imza akışlarını içerir.

Public kaynak kodu [erbayaskin/e-imza-platform](https://github.com/erbayaskin/e-imza-platform)
deposunda Apache-2.0 ile yayımlanmıştır.

Bu durum “Türkiye'de her ortamda hukuken kabul edilmiş üretim hizmeti” anlamına gelmez.
Canlı ESHS/TSA/OCSP/SİL, geçerli fiziksel kart, gerçek HSM, bağımsız doğrulayıcı, yük,
sızma, felaket kurtarma, hukuk ve bilgi güvenliği kabul kanıtları henüz tamamlanmamıştır.

## 2. Faz durumu

| Faz | Yazılım teslimi | Üretim/kabul durumu |
|---|---|---|
| 1 — mevzuat/standart matrisi | Tamamlandı | Güncel kaynak ve hukuk onayı periyodik sürdürülmeli |
| 2 — mimari/tehdit modeli | Tamamlandı | Ürün, KVKK, hukuk ve bilgi güvenliği onayı bekliyor |
| 3 — teknik iskelet | Tamamlandı | Public GitHub CI/CodeQL ve monitor başarıyla çalıştı |
| 4 — akıllı kart agent | Tamamlandı | AKİS ile geliştirme yapıldı; formal geçerli kart matrisi kaydedilmedi |
| 5 — CAdES/TSA | Tamamlandı | Canlı TSA ve bağımsız ürün doğrulaması bekliyor |
| 6 — doğrulama motoru | Tamamlandı | Canlı ESHS OCSP/SİL ve hukuk kabulü bekliyor |
| 7 — CAdES B-LT/B-LTA | Tamamlandı | Canlı kanıt ve haricî doğrulayıcı bekliyor |
| 8 — güvenlik/performans | Altyapı tamamlandı | 30 dk yük, bağımsız sızma ve formal kart kabulü bekliyor |
| 9 — dağıtım/operasyon | Paket tamamlandı | Cluster, imzalı MSI, DR tatbikatı ve pilot bekliyor |
| 10 — CAdES/XAdES/PAdES uçtan uca | Tamamlandı | Haricî birlikte çalışabilirlik matrisi bekliyor |
| 11 — çoklu imza/JAR entegrasyonu | Tamamlandı | Çoklu imza haricî ürün ve fiziksel cihaz kabulü bekliyor |
| 12 — üretim kabulü | Planlandı | Başlanmadı; dış sistem ve kurum kararları gerekli |

## 3. Gerçeklenen yetenek matrisi

### 3.1 İmza üretimi

| Yetenek | Durum | Sınır |
|---|---|---|
| CAdES B-B/B-T | Var | ATTACHED/DETACHED, `.p7s` |
| CAdES B-LT/B-LTA | Var | Yükseltme ve arşiv yenileme |
| XAdES B-B/B-T | Var | DETACHED/ENVELOPED/ENVELOPING |
| PAdES B-B/B-T | Var | PDF ByteRange, ENVELOPED |
| CAdES paralel | Var | Yeni üst seviye `SignerInfo` |
| CAdES seri | Var | CMS `counterSignature` |
| XAdES paralel | Var | DETACHED/ENVELOPING; ENVELOPED yok |
| XAdES seri | Var | `xades:CounterSignature` |
| PAdES seri | Var | Incremental PDF revision |
| PAdES paralel | Yok | Bilinçli kapsam dışı |
| XAdES/PAdES B-LT/B-LTA | Yok | Yeni geliştirme fazı gerekir |

### 3.2 Anahtar ve cihaz kullanımı

| Yetenek | Durum | Not |
|---|---|---|
| Client-side akıllı kart | Var | Loopback agent, yerel Swing PIN |
| Client-side HSM | Yok | Mimari gereği desteklenmez |
| Server-side akıllı kart | Var | ATR ve otomatik slot keşfi |
| Server-side HSM | Var | Kütüphane + slot + secret `credentialRef` |
| PIN'siz public sertifika okuma | Var | Middleware izin verirse; yerel fallback mümkün |
| AKİS kart profili | Var | ATR kayıtlı; formal kabul formu açık |
| Offline masaüstü | Var | CAdES imzalama/doğrulama, manuel kart profili |

### 3.3 Doğrulama ve yönetim

| Yetenek | Durum |
|---|---|
| X.509 süre, yol, key usage ve kısıt doğrulaması | Var |
| Güvenilir kök/alt kök DB CRUD ve tarihsel snapshot | Var |
| CRL/SİL ve OCSP istemcileri | Var; canlı sağlayıcı kabulü açık |
| RFC 3161 TSA istemcisi ve token doğrulaması | Var; canlı TSA kabulü açık |
| Politika kontrollerini aktif/pasif yönetme | Var |
| Sertifika tarih kontrolünü ortam politikasıyla yönetme | Var |
| CAdES/XAdES/PAdES ve bütün çoklu imzaları doğrulama | Var |
| İmzalayan bilgisi ve public `.cer` indirme | Var |
| Yönetim web ekranı | Var |
| Ayrı doğrulama ve çoklu imza sayfaları | Var |

## 4. Otomatik doğrulama tabanı

5 Ağustos 2026 tarihinde kök reaktörde çalıştırılan `mvn verify` başarıyla tamamlanmıştır.
Son Surefire raporlarında dokuz modülde toplam 85 test vardır:

| Modül | Test | Başarısız/Hata |
|---|---:|---:|
| `signature-core` | 4 | 0 |
| `certificate-validation` | 12 | 0 |
| `timestamp-client` | 2 | 0 |
| `signature-cades` | 11 | 0 |
| `signature-xades` | 3 | 0 |
| `signature-pades` | 1 | 0 |
| `smartcard-agent` | 19 | 0 |
| `desktop-signing-demo` | 3 | 0 |
| `signature-api` | 30 | 0 |

Bu sayı üretim kabulü değildir. Test fixture'ları, mock servisler ve geliştirici kartı; gerçek
TSA/ESHS/HSM, geçerli NES ve bağımsız ürün testinin yerine geçmez.

## 5. Bilinen açıklar ve riskler

### 5.1 Üretime çıkışı engelleyen kabul kapıları

1. Geçerli sertifikalı en az bir kartla formal kart/okuyucu/middleware kabul matrisi.
2. En az bir gerçek HSM ile slot, concurrency, failover ve secret rotation testi.
3. Canlı TSA ile B-T; canlı ESHS OCSP/SİL ile VALID/REVOKED/UNKNOWN testleri.
4. CAdES/XAdES/PAdES tek ve çoklu örneklerin bağımsız doğrulayıcılarla karşılaştırılması.
5. Tarihli 30 dakikalık yük testi ve kaynak profiline göre limit raporu.
6. Bağımsız kontrollü sızma testi; kritik/yüksek açıkların kapatılması.
7. PostgreSQL yedek/geri yükleme ve RPO/RTO felaket kurtarma tatbikatı.
8. Kod imzalı agent/MSI, pilot halkaları ve geri alma kanıtı.
9. Hukuk, KVKK, bilgi güvenliği ve ürün sahibi onayı.

### 5.2 Teknik ürün boşlukları

- XAdES ve PAdES B-LT/B-LTA yükseltme/yenileme yoktur.
- Delta/indirect CRL, AIA ile eksik issuer indirme ve tam OCSP responder iptal zinciri
  sınırlıdır; Faz 6 belgesindeki sınırlar geçerlidir.
- Direct-JAR tüketimi için Maven Central/kurumsal artifact repository yayını, semantic
  versioning, source/Javadoc JAR ve BOM henüz tanımlı değildir.
- API/agent SDK'sı üretilmemiştir; OpenAPI ve örnek kod kullanılmaktadır.
- Çoklu XAdES kapsayıcısı ve PAdES incremental örnekleri için bağımsız birlikte
  çalışabilirlik kanıtı yoktur.

### 5.3 Depo/yayın açıkları

- Apache-2.0, telif, DCO, Contributor Covenant, CODEOWNERS, destek ve güvenlik politikaları
  kaynak ağacında tamamlanmıştır.
- erbayaskin/e-imza-platform public repository oluşturuldu; DCO imzalı geçmiş main dalına
  yayımlandı ve GitHub Apache-2.0 lisansını algıladı.
- main dal koruması; PR, bir onay, CODEOWNERS, güncel dal, konuşma çözümü ve gerçek
  build/dependency-review/dco/analyze check adlarıyla etkinleştirildi.
- Private Vulnerability Reporting, Dependabot, secret scanning/push protection etkin; CI,
  CodeQL ve resmî kaynak monitor başarılıdır. Açık CodeQL uyarısı yoktur.
- Kararlı release sürümü, artifact repository, checksum ve imzalı tag yayını henüz yoktur.

## 6. Bir sonraki önerilen çalışma sırası

1. `FAZ_12_URETIM_KABUL_VE_BIRLIKTE_CALISABILIRLIK.md` içindeki dış kabul matrisini
   kurum/sistem bilgileriyle doldurun.
2. Geçerli kart ve gerçek HSM ile aynı format/algoritma/çoklu imza vektörlerini çalıştırın.
3. Canlı TSA/OCSP/SİL ve trust store kayıtlarını onaylı kaynaklardan yükleyin.
4. Bağımsız ürün sonuçlarını, test artifact özetlerini ve tarihli raporları kaydedin.
5. Yük, güvenlik ve DR kapılarını tamamlayın.
6. İlk gerçek katkı PR akışında DCO, dependency review, CODEOWNERS ve review kapılarını sınayın.
7. Üretim kabulünden sonra `0.1.0-SNAPSHOT` yerine sürümlü release hazırlayın.

## 7. Durum güncelleme kuralı

Her faz veya önemli özellik tamamlandığında bu dosyada aşağıdakiler birlikte güncellenir:

- Durum tarihi ve şema/sürüm
- Faz tablosu
- Yetenek matrisi
- Test sayıları
- Açık kabul kapıları
- Önerilen sonraki sıra

“Kod tamamlandı”, “otomatik test geçti”, “fiziksel kabul geçti” ve “üretim onayı verildi”
birbirinden ayrı beyanlardır.
