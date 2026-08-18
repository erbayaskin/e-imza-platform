# E-İmza API Projesi — Kapsam, Kararlar ve Yol Haritası

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Belge durumu: Faz 6 yazılım MVP'si tamamlandı  
> Son güncelleme: 18 Ağustos 2026  
> Çalışma yöntemi: Yeni gereksinimler geldikçe bu belge faz faz güncellenecek; alınan kararlar ve tamamlanan işler burada izlenecek.

## 1. Projenin amacı

Türkiye'de geçerli elektronik imza mevzuatı ve teknik kriterleri gözetilerek, farklı uygulamaların kullanabileceği güvenli ve genişletilebilir bir e-imza altyapısı geliştirmek.

Sistem temel olarak:

- Akıllı kartları/kart okuyucuları algılayacak,
- Tanımlanabilir ATR (Answer to Reset) değerleri üzerinden kart tipini ve uygun sürücü/yapılandırmayı eşleştirecek,
- Karttaki nitelikli elektronik sertifikaları okuyacak,
- Elektronik imza oluşturacak,
- Elektronik imzayı ve imzalayan sertifikanın güven zincirini doğrulayacak,
- SİL/CRL ve OCSP üzerinden iptal durumu kontrolü yapacak,
- Yetkili bir zaman damgası hizmetinden zaman damgası alacak ve doğrulayacak,
- İşlemleri diğer sistemlere REST API üzerinden sunacak.

Hedef, yalnızca kriptografik olarak doğru bir imza üretmek değil; imzanın formatını, sertifika durumunu, güven zincirini, imza zamanını ve uzun dönem doğrulanabilirliğini açıklanabilir bir doğrulama raporuyla sunmaktır.

## 2. Önemli mimari gerçek

Bir sunucuda çalışan Spring Boot API, son kullanıcının bilgisayarındaki USB akıllı karta tarayıcı üzerinden doğrudan erişemez. Bu nedenle çözümün en az iki bileşenli olması öngörülmektedir:

1. **Merkezi E-İmza API:** Doğrulama, politika, zaman damgası, yapılandırma, denetim kaydı ve iş akışı yönetimi.
2. **Yerel İmzalama Aracısı:** Kullanıcının bilgisayarında çalışarak PC/SC ve PKCS#11 üzerinden karta erişen, PIN'i yerelde alan ve imzalama işlemini gerçekleştiren servis/masaüstü uygulaması.

Özel anahtar karttan dışarı çıkarılmayacaktır. PIN merkezi API'ye gönderilmeyecek, loglanmayacak veya kalıcı olarak saklanmayacaktır.

Bu ayrım, mimari keşif fazında kesinleştirilecektir. API yalnızca kartın takılı olduğu aynı makinede çalışacaksa tek bileşenli dağıtım seçeneği ayrıca değerlendirilebilir.

## 3. İlk kapsam

### 3.1 Akıllı kart ve okuyucu yönetimi

- PC/SC üzerinden okuyucu ve kart algılama
- ATR değerini okuma ve normalize etme
- ATR için tam değer veya kontrollü maske eşleştirmesi
- ATR → kart profili → PKCS#11 sağlayıcısı/sürücüsü eşleştirmesi
- Yeni ATR ve kart profillerini kod değişikliği olmadan tanımlayabilme
- Birden fazla okuyucu ve kart bulunduğunda kullanıcıya seçim sunma
- Kart çıkarma/takma olaylarını yönetme
- Desteklenmeyen veya belirsiz ATR için açık hata üretme
- Karttan sertifika listesini, seri numarasını ve gerekli açık bilgileri okuma

> Not: ATR tek başına kartın güvenilirliğini veya kesin kimliğini kanıtlamaz. ATR, sürücü/profil seçimi için kullanılacak; güven kararı sertifika zinciri, politika ve iptal kontrollerine dayanacaktır.

### 3.2 İmza oluşturma

İlk hedef formatlar, kullanım senaryosu kesinleştirildikten sonra önceliklendirilecektir:

- CAdES: ikili/genel dosyalar
- XAdES: XML belgeleri
- PAdES: PDF belgeleri

Değerlendirilecek imza seviyeleri:

- Temel imza
- İmza zaman damgalı imza
- Doğrulama verilerini içeren uzun dönem doğrulanabilir imza
- Arşiv/uzun dönem koruma seviyesi

Ayrıca aşağıdaki seçenekler açıkça modellenmelidir:

- Attached/enveloping ve detached imza
- Tekli ve çoklu/paralel imza
- Seri imza gereksinimi
- Belgenin tamamının mı, belirli bir bölümünün mü imzalanacağı
- İmzalanacak özetin istemciye nasıl ve hangi güven sınırları içinde aktarılacağı

### 3.3 İmza doğrulama

Doğrulama sonucu yalnızca `true/false` olmamalıdır. En az şu sonuçlar ayrı ayrı raporlanmalıdır:

- İmza bütünlüğü geçerli mi?
- İmzalanan içerik doğru biçimde çözümlendi mi?
- İmza formatı ve seviyesi nedir?
- Kullanılan algoritmalar politika tarafından kabul ediliyor mu?
- İmzalama sertifikası imza anında geçerli miydi?
- Sertifika nitelikli elektronik sertifika koşullarını karşılıyor mu?
- Sertifika güven zinciri güvenilen köke kadar kurulabiliyor mu?
- Sertifika veya zincirdeki sertifikalar iptal edilmiş mi?
- Varsa zaman damgası geçerli ve güvenilir mi?
- İmza uzun dönem doğrulamaya elverişli mi?
- Sonuç geçerli, geçersiz veya belirsiz mi?
- Her sonuç için makinece okunabilir hata kodu ve insan tarafından anlaşılır açıklama

Doğrulama politikasında “şu an”, “imza zamanı” ve “güvenilir zaman damgası zamanı” birbirinden ayrılacaktır.

### 3.4 Sertifika doğrulama

- X.509 sertifika ayrıştırma
- Sertifika geçerlilik tarihleri
- Anahtar kullanımı ve genişletilmiş anahtar kullanımı
- Sertifika politikaları ve gerekli nitelikli sertifika alanları
- Güven zinciri oluşturma ve doğrulama
- Güvenilen kök/ara sertifika deposunun kontrollü güncellenmesi
- Güvenilir kök ve alt kök sertifikaların PostgreSQL üzerinde immutable, tarihsel snapshot'larla yönetilmesi
- Güven deposu için ekleme, üyelik güncelleme, güncel sürümden çıkarma ve tarihsel sürüm görüntüleme API'leri
- SİL/CRL indirme, imza kontrolü, önbellekleme ve güncellik kontrolü
- OCSP isteği/cevabı, cevap imzası ve tazelik kontrolü
- Ağ erişilemediğinde `geçersiz` ile `durumu belirlenemedi` sonuçlarının ayrılması
- Sertifika kullanım kısıtlarının raporlanması

### 3.5 Zaman damgası

- RFC 3161 uyumlu TSA istemcisi
- Birden fazla zaman damgası sağlayıcısı tanımlayabilme
- Kimlik bilgilerini güvenli sır yönetimiyle saklama
- İstek özeti, nonce, politika OID'si ve yanıt eşleştirme kontrolleri
- Zaman damgası imzası, sertifikası, zinciri ve iptal durumunun doğrulanması
- Hata halinde kontrollü yeniden deneme ve sağlayıcı değiştirme politikası
- Zaman damgasının imzaya gömülmesi ve bağımsız doğrulanması

## 4. Mevzuat ve teknik dayanak

Nihai uyumluluk matrisi hukuk ve güvenlik uzmanı incelemesiyle onaylanmalıdır. Başlangıçta esas alınacak kaynaklar:

- 5070 sayılı Elektronik İmza Kanunu
- Elektronik İmza Kanununun Uygulanmasına İlişkin Usul ve Esaslar Hakkında Yönetmelik
- Elektronik İmza ile İlgili Süreçlere ve Teknik Kriterlere İlişkin Tebliğ
- BTK Elektronik İmza Kullanım Profilleri Rehberi ve ilgili Kurul kararları
- BTK Nitelikli Elektronik Sertifika, SİL ve OCSP İstek/Cevap Profilleri
- ETSI CAdES, XAdES ve PAdES standart aileleri
- RFC 3161 zaman damgası protokolü
- RFC 5280 X.509 sertifika ve SİL profili
- RFC 6960 OCSP
- PKCS#11 ve PC/SC
- ISO/IEC 7816 akıllı kart standart ailesi

Standartların kesin sürümleri, zorunlu algoritmalar, profil seviyeleri ve geçiş tarihleri **Faz 1 uyumluluk matrisi** içinde sabitlenecektir. Geliştirme sırasında “en güncel sürüm” ifadesine güvenilmeyecek; kullanılan her standardın sürümü ve erişim tarihi kaydedilecektir.

Resmî başlangıç kaynakları:

- [BTK Elektronik İmza Mevzuatı](https://www.btk.gov.tr/elektronik-imza-mevzuati)
- [BTK Elektronik İmza Genel Bilgi](https://btk.gov.tr/elektronik-imza-genel-bilgi)
- [Elektronik İmza Kanununun Uygulanmasına İlişkin Yönetmelik (BTK PDF)](https://www.btk.gov.tr/uploads/pages/eimza-yonetmelik-5a33fe7fe7d86.pdf)
- [BTK Elektronik İmza Kullanım Profilleri Rehberi](https://www.btk.gov.tr/uploads/pages/elektronik-imza-kullanim-profilleri-rehberi-5a33ff5b59f93.pdf)

> Bu belge teknik proje planıdır; tek başına hukukî uygunluk görüşü değildir.

## 5. Önerilen teknoloji ve bileşenler

İlk teknoloji adayı:

- Java 21 LTS veya kurum standardındaki desteklenen LTS sürümü
- Spring Boot
- REST/OpenAPI
- Maven veya Gradle
- PostgreSQL (yapılandırma, politika ve denetim üst verileri için)
- PC/SC (`javax.smartcardio`) ve üretici PKCS#11 kitaplıkları
- Java Cryptography Architecture/Extension
- Uygunluk ve lisans incelemesinden sonra Bouncy Castle ve/veya Avrupa Komisyonu DSS gibi olgun kütüphaneler
- İşletim sistemi sır deposu, Vault veya eşdeğer sır yönetimi
- Yapılandırılmış log, metrik ve izleme

Kütüphane seçimi; Türkiye profillerini destekleme derecesi, lisans, bakım durumu, güvenlik geçmişi, donanım uyumluluğu ve uzun dönem doğrulama yeteneği ölçülerek yapılacaktır.

## 6. Taslak modül yapısı

- `signature-api`: REST uçları, kimlik doğrulama, yetkilendirme ve istek yönetimi
- `signature-core`: Format bağımsız imzalama modelleri ve politikalar
- `signature-cades`: CAdES üretme/doğrulama
- `signature-xades`: XAdES üretme/doğrulama
- `signature-pades`: PAdES üretme/doğrulama
- `certificate-validation`: Zincir, SİL/CRL, OCSP ve politika kontrolleri
- `timestamp-client`: TSA bağlantıları ve RFC 3161 doğrulaması
- `smartcard-agent`: PC/SC, ATR, PKCS#11 ve yerel PIN akışı
- `trust-config`: Güven kökleri, ESHS/TSA ve doğrulama politikaları
- [Güvenilir sertifika deposu](GUVENILIR_SERTIFIKA_DEPOSU.md): DB tabanlı kök/alt kök CRUD, sürümleme ve tarihsel doğrulama
- `audit`: Güvenli denetim kayıtları ve hassas veri maskeleme
- `integration-tests`: Gerçek kart, test kartı, TSA ve bozuk imza senaryoları

Modül isimleri başlangıç önerisidir; Faz 2 sonunda kesinleşecektir.

## 7. Fazlara ayrılmış yol haritası

### Faz 0 — Gereksinim keşfi ve kapsam

Durum: **Başladı**

Yapılacaklar:

1. Kullanım senaryolarını ve sistemi kullanacak tarafları belirlemek.
2. İmzalanacak belge türlerini ve öncelikli imza formatını belirlemek.
3. Merkezi API ile yerel aracının iletişim modelini netleştirmek.
4. Desteklenecek işletim sistemlerini, tarayıcıları, kartları ve okuyucuları listelemek.
5. Kullanılacak ESHS ve zaman damgası sağlayıcılarını belirlemek.
6. Performans, eşzamanlılık, dosya boyutu ve erişilebilirlik hedeflerini yazmak.
7. Veri saklama, denetim kaydı ve KVKK gereksinimlerini belirlemek.
8. İlk MVP kapsamını ve kapsam dışı maddeleri onaylamak.

Çıktılar:

- Onaylı gereksinim listesi
- Kullanım senaryoları
- MVP kapsamı
- Açık sorular ve riskler

### Faz 1 — Mevzuat ve standart uyumluluk matrisi

Durum: **Teknik temel tamamlandı; hukuk ve bilgi güvenliği onayı bekliyor**

Yapılacaklar:

1. BTK mevzuatı, tebliğ, rehber ve Kurul kararlarının güncel sürümlerini toplamak.
2. Her gereksinimi uygulanacak bileşen ve test ile eşleştirmek.
3. CAdES/XAdES/PAdES profil ve seviye kararlarını vermek.
4. Kabul edilen özet ve imza algoritmalarını belirlemek.
5. NES, zincir, SİL/OCSP ve zaman damgası doğrulama politikasını yazmak.
6. Hukuk ve bilgi güvenliği incelemesi yapmak.

Çıktılar:

- [Sürümlendirilmiş uyumluluk matrisi](FAZ_1_UYUMLULUK_MATRISI.md) — tamamlandı
- Doğrulama politikası — uyumluluk matrisinin 5–11. bölümlerinde tanımlandı
- Algoritma ve profil politikası — uyumluluk matrisinin 3–4. bölümlerinde tanımlandı
- Hukuk ve bilgi güvenliği onayı — canlıya geçiş öncesi bekliyor

### Faz 2 — Mimari tasarım ve güvenlik tehdit modellemesi

Durum: **Mimari temel tamamlandı; ürün, hukuk ve güvenlik onayı bekliyor**

Yapılacaklar:

1. Merkezi API/yerel aracı sınırlarını çizmek.
2. Anahtar, PIN, belge ve özet verilerinin güven sınırlarını belirlemek.
3. API kimlik doğrulama ve yetkilendirme modelini tasarlamak.
4. Yerel aracı ile API arasında oturum bağlama, origin kontrolü ve karşılıklı güven mekanizması tasarlamak.
5. Tekrar oynatma, sahte imzalama isteği, belge-özet değiştirme ve kötü amaçlı sürücü risklerini modellemek.
6. Hata kodları, işlem durumları ve denetim olaylarını tanımlamak.
7. Veritabanı, önbellek ve sır yönetimini tasarlamak.

Çıktılar:

- [Mimari tasarım ve tehdit modeli](FAZ_2_MIMARI_VE_TEHDIT_MODELI.md) — tamamlandı
- Mimari karar kayıtları — Faz 2 belgesinin 15. bölümünde
- STRIDE tehdit modeli ve güvenlik kontrolleri — Faz 2 belgesinin 12. bölümünde
- API ve hata modeli taslağı — Faz 2 belgesinin 9–10. bölümlerinde
- Veri modeli — Faz 2 belgesinin 11. bölümünde
- Ürün, hukuk ve güvenlik onayı — bekliyor

### Faz 3 — Teknik iskelet ve geliştirme altyapısı

Durum: **Tamamlandı**

Yapılacaklar:

1. Çok modüllü Java/Spring Boot proje iskeletini kurmak.
2. Kod kalitesi, bağımlılık ve güvenlik taramalarını eklemek.
3. OpenAPI sözleşmesini oluşturmak.
4. Hata modeli, loglama, metrik ve korelasyon kimliğini eklemek.
5. Test katmanlarını ve CI sürecini kurmak.

Çıktılar:

- [Faz 3 teknik iskelet teslim belgesi](FAZ_3_TEKNIK_ISKELET.md) — tamamlandı
- Java 21 / Spring Boot 4.1.0 çok modüllü çalışan uygulama iskeleti — tamamlandı
- Maven Enforcer, test, JaCoCo ve GitHub Actions CI kalite kapıları — tamamlandı
- OpenAPI 3.1 sözleşmesi — tamamlandı
- Flyway veritabanı migration'ları — tamamlandı
- Gerçek kart, imza formatı, TSA ve doğrulama implementasyonları — ilgili sonraki fazları bekliyor

### Faz 4 — Akıllı kart ve yerel aracı MVP

Durum: **Yazılım MVP'si tamamlandı; gerçek kart/sürücü kabul testi bekliyor**

Yapılacaklar:

1. Okuyucu/kart keşfini geliştirmek.
2. ATR profil kayıt ve eşleştirme mekanizmasını geliştirmek.
3. PKCS#11 sağlayıcılarını güvenli biçimde yüklemek.
4. Sertifika listeleme ve seçimini geliştirmek.
5. PIN'in yalnızca yerelde işlendiği imzalama akışını kurmak.
6. Kart çıkarma, kilitli PIN, hatalı sürücü ve çoklu kart hatalarını ele almak.

Çıktılar:

- [Faz 4 akıllı kart ve yerel aracı teslim belgesi](FAZ_4_AKILLI_KART_YEREL_ARACI.md) — tamamlandı
- PC/SC keşfi, yapılandırılabilir ATR profilleri ve güvenli PKCS#11 adaptörü — tamamlandı
- PIN'in yalnız yerel pencerede işlendiği sertifika listeleme ve imzalama akışı — tamamlandı
- Ed25519 imzalı kısa ömürlü manifest, cihaz bağı ve replay koruması — tamamlandı
- RSA/ECDSA ham SHA-256 özeti imzalama yazılımsal teknik kanıtı — tamamlandı
- İlk gerçek kart/sürücüyle saha kabul matrisi — kart ve üretici sürücüsü bekliyor

### Faz 5 — İlk imza formatı ve zaman damgası MVP

Durum: **Yazılım MVP'si tamamlandı; gerçek ESHS/TSA ve haricî doğrulayıcı kabulü bekliyor**

Yapılacaklar:

1. Seçilen ilk formatta imza üretmek.
2. TSA entegrasyonunu ve RFC 3161 kontrollerini geliştirmek.
3. İmza zaman damgasını formata eklemek.
4. Aynı imzanın bağımsız doğrulamasını yapmak.
5. Örnek dosyalar ve negatif testler üretmek.

Çıktılar:

- [Faz 5 CAdES ve zaman damgası teslim belgesi](FAZ_5_CADES_VE_ZAMAN_DAMGASI.md) — tamamlandı
- Ayrık CAdES Baseline B-T iki aşamalı kart imzalama akışı — tamamlandı
- RFC 3161 HTTPS istemcisi ve istek/yanıt/token doğrulaması — tamamlandı
- İmza politikası OID/özet/URL imzalı özelliği — yapılandırılabilir olarak tamamlandı
- Pozitif ve negatif yazılımsal uçtan uca test paketi — tamamlandı
- Gerçek ESHS/TSA ve ikinci ürünle birlikte çalışabilirlik raporu — erişim bilgileri bekliyor

### Faz 6 — Sertifika ve imza doğrulama motoru

Durum: **Yazılım MVP'si tamamlandı; canlı ESHS ve hukuk/bilgi güvenliği kabulü bekliyor**

Yapılacaklar:

1. Sertifika zinciri ve politika kontrollerini geliştirmek.
2. SİL/CRL ve OCSP kontrollerini geliştirmek.
3. İmza formatı, bütünlük ve zaman kontrollerini geliştirmek.
4. Geçerli/geçersiz/belirsiz durum modelini uygulamak.
5. Ayrıntılı JSON ve insan okunabilir doğrulama raporu üretmek.
6. Güven deposu güncelleme ve sürümleme sürecini kurmak.

Çıktılar:

- [Faz 6 sertifika ve imza doğrulama motoru teslim belgesi](FAZ_6_DOGRULAMA_MOTORU.md) — tamamlandı
- Sertifika doğrulama API'si ve tarihsel DB güven deposuyla PKIX yolu — tamamlandı
- Ayrık CAdES B-T imza ve RFC 3161 zaman damgası doğrulama API'si — tamamlandı
- Kanıt özetleri, politika/depo sürümü ve alt kontrolleri içeren üç durumlu rapor — tamamlandı
- [Standart değişikliği etki haritası](STANDART_DEGISIKLIGI_ETKI_HARITASI.md) — tamamlandı
- Gerçek ESHS OCSP/SİL uçları, ikinci doğrulayıcı ve hukuk/bilgi güvenliği kabulü — bekliyor

### Faz 7 — Diğer imza formatları ve uzun dönem doğrulama

Durum: **Faz 7A CAdES B-LT/B-LTA yazılım MVP'si tamamlandı; PAdES/XAdES ve canlı kabul bekliyor**

Yapılacaklar:

1. Kalan CAdES/XAdES/PAdES formatlarını önceliğe göre eklemek.
2. Doğrulama verilerini imzaya eklemek.
3. Uzun dönem ve arşiv seviyelerini geliştirmek.
4. Çoklu/seri imza senaryolarını geliştirmek.
5. Haricî doğrulayıcılarla birlikte çalışabilirlik testi yapmak.

Çıktılar:

- [Faz 7 CAdES uzun dönem doğrulama teslim belgesi](FAZ_7_CADES_UZUN_DONEM.md) — Faz 7A tamamlandı
- CAdES B-T → B-LT doğrulama materyali gömme — tamamlandı
- CAdES B-LTA `ATSHashIndexV3` ve `archive-time-stamp-v3` — tamamlandı
- Arşiv zaman damgası yenileme ve çoklu arşiv token doğrulaması — tamamlandı
- Gömülü SİL/OCSP öncelikli tarihsel doğrulama — tamamlandı
- Yükseltme REST API'si ve DB denetim üst verisi — tamamlandı
- PAdES/XAdES, otomatik yenileme scheduler'ı ve birlikte çalışabilirlik raporu — sonraki Faz 7 alt adımları

### Faz 8 — Güvenlik, performans ve kabul testleri

Durum: **Yazılım güvenlik ve test altyapısı tamamlandı; ölçümlü yük, bağımsız sızma ve fiziksel kart kabulü bekliyor**

Yapılacaklar:

1. Tehdit modelindeki kontrolleri doğrulamak.
2. Sızma testi ve bağımlılık güvenlik incelemesi yapmak.
3. Kart, okuyucu, işletim sistemi ve sürücü uyumluluk matrisi çalıştırmak.
4. Büyük dosya, eşzamanlı istek ve TSA/OCSP kesinti testleri yapmak.
5. Bozuk, süresi geçmiş, iptal edilmiş ve algoritması kabul edilmeyen örnekleri test etmek.
6. Hukuk, güvenlik ve ürün kabulünü tamamlamak.

Çıktılar:

- Güvenlik ve performans raporları
- Kart/işletim sistemi uyumluluk matrisi
- Kabul tutanağı

### Faz 9 — Canlıya geçiş ve işletim

Durum: **Üretim dağıtım ve operasyon paketi tamamlandı; gerçek ortam kurulumu ve pilot kabulü bekliyor**

Yapılacaklar:

1. Kurulum, sürücü dağıtımı ve yükseltme mekanizmasını hazırlamak.
2. Güven deposu, mevzuat ve algoritma değişiklikleri için izleme süreci kurmak.
3. Alarm, yedekleme, olay müdahale ve iş sürekliliği planlarını hazırlamak.
4. Operasyon ve kullanıcı dokümantasyonunu tamamlamak.
5. Kontrollü pilot ve kademeli canlıya geçiş yapmak.

Çıktılar:

- Üretim sürümü
- Operasyon kitabı
- Güncelleme ve uyumluluk takip süreci

## 8. İlk API taslağı

Kesin sözleşme değildir; gereksinim keşfi için başlangıçtır:

- `GET /api/v1/readers` — Yerel aracının görebildiği okuyucular
- `GET /api/v1/cards` — Takılı kartlar ve destek durumu
- `GET /api/v1/cards/{cardId}/certificates` — Karttaki uygun sertifikalar
- `POST /api/v1/signing-sessions` — İmzalama oturumu başlatma
- `POST /api/v1/signing-sessions/{id}/complete` — Yerel imza sonucunu tamamlama
- `POST /api/v1/signatures/validate` — İmza doğrulama
- `POST /api/v1/certificates/validate` — Sertifika doğrulama
- `POST /api/v1/timestamps` — Zaman damgası alma
- `POST /api/v1/timestamps/validate` — Zaman damgası doğrulama
- `GET/POST/PUT /api/v1/admin/card-profiles` — ATR ve kart profili yönetimi
- `GET/POST/PUT /api/v1/admin/validation-policies` — Doğrulama politikası yönetimi

Kart erişim uçlarının merkezi API'de mi, yalnızca yerel aracının localhost API'sinde mi bulunacağı Faz 2'de kararlaştırılacaktır.

## 9. Güvenlik ilkeleri

- Özel anahtar karttan çıkarılmaz.
- PIN merkezi sisteme gönderilmez ve hiçbir koşulda loglanmaz.
- İmzalanan belgenin kullanıcıya gösterilen içeriği ile karta imzalatılan özet kriptografik olarak bağlanır.
- Tüm imzalama istekleri kısa ömürlü, tek kullanımlık ve oturuma bağlı olur.
- Hassas veriler loglarda maskelenir; belge içeriği varsayılan olarak loglanmaz.
- Güven kökü, ATR profili, algoritma politikası ve TSA değişiklikleri yetkili ve denetlenebilir olur.
- Haricî cevaplar (OCSP, SİL/CRL, TSA) imza ve güncellik kontrolünden geçirilmeden güvenilir sayılmaz.
- Ağ hatası, doğrulama başarısızlığı olarak yanlış sınıflandırılmaz.
- Kriptografik algoritmalar kod içine dağınık biçimde gömülmez; sürümlü politika ile yönetilir.
- Ham dosya yüklemelerinde boyut, tür ve kaynak tüketimi sınırları uygulanır.

## 10. Test stratejisi

- Birim testleri
- Format bazlı bilinen-cevap testleri
- Gerçek kart/okuyucu entegrasyon testleri
- Yazılımsal token veya test HSM ile otomatik testler
- PKCS#11 aygıt türü ayrımı: akıllı kartta ATR tabanlı otomatik slot keşfi;
  HSM'de yönetici tarafından verilen sürücü kitaplığı ve sabit slot numarası
- Fiziksel kart yerel kabul testi:
  `YEREL_FIZIKSEL_KART_TEST_REHBERI.md`
- İki modlu imzalama mimarisi:
  `IKI_MODLU_IMZALAMA_MIMARISI.md`
- ESHS/TSA test ortamı entegrasyonları
- Haricî doğrulama araçlarıyla çapraz doğrulama
- Geçersiz imza ve bozulmuş belge testleri
- Süresi geçmiş/iptal edilmiş sertifika testleri
- OCSP, SİL/CRL ve TSA kesinti senaryoları
- PIN deneme sınırı ve kart kilitlenmesi senaryoları
- Tekrar oynatma ve oturum karıştırma güvenlik testleri
- Büyük dosya ve yüksek eşzamanlılık testleri

Gerçek kartla yapılan testler, PIN deneme sayısını tüketmemek ve kartı kilitlememek için kontrollü bir test prosedürüyle yürütülecektir.

## 11. Başlıca riskler

| Risk | Etki | İlk önlem |
|---|---|---|
| Üretici PKCS#11 sürücülerinin farklı davranması | Kart uyumsuzluğu | Kart profili adaptörleri ve gerçek cihaz test matrisi |
| ATR'nin tek başına kesin kart tanımı sanılması | Yanlış profil/güven kararı | ATR'yi yalnızca aday profil seçimi için kullanmak |
| Tarayıcının karta doğrudan erişememesi | Akışın çalışmaması | İmzalı ve güvenli yerel aracı |
| Mevzuat/standart değişikliği | Uyumluluk kaybı | Sürümlü politika ve düzenli uyumluluk kontrolü |
| OCSP/SİL/TSA erişim kesintisi | Belirsiz doğrulama veya imzalama kesintisi | Önbellek, kontrollü yeniden deneme ve açık durum modeli |
| Eski/zayıf algoritmalar | Güvenlik ve uyumluluk sorunu | Merkezi algoritma politikası |
| PIN veya belge verisinin loglanması | Ciddi güvenlik/KVKK riski | Güvenli varsayılanlar, maskeleme ve log testleri |
| Kütüphane lisansı veya eksik profil desteği | Yeniden geliştirme | Faz 1-2'de PoC ve lisans incelemesi |

## 12. Açık sorular

Bir sonraki anlatımda aşağıdakiler netleştirildikçe Faz 0 güncellenecektir:

1. İlk kullanım senaryosu nedir; kim, hangi uygulama üzerinden neyi imzalayacak?
2. Öncelik PDF/PAdES, XML/XAdES veya genel dosya/CAdES seçeneklerinden hangisi?
3. Sistem yalnızca Windows'ta mı, yoksa macOS ve Linux'ta da mı çalışacak?
4. Desteklenecek kart markaları, modelleri, ATR değerleri ve okuyucular biliniyor mu?
5. Hangi ESHS'lerin sertifikaları ve hangi zaman damgası sağlayıcıları kullanılacak?
6. İmza, aynı bilgisayardaki uygulamadan mı yoksa web uygulamasından mı başlatılacak?
7. Tek imza mı, çoklu/paralel/seri imza mı gerekli?
8. Uzun dönem doğrulama ve arşiv seviyesi ilk sürümde gerekli mi?
9. Beklenen günlük işlem sayısı, eşzamanlı kullanıcı ve azami dosya boyutu nedir?
10. Belgeler sunucuda saklanacak mı, yoksa yalnızca işlem sırasında mı işlenecek?
11. Kimlik doğrulama ve yetkilendirme için mevcut bir altyapı var mı?
12. Proje bir kurum içi servis mi, ticari ürün mü, yoksa ESHS niteliğinde bir hizmet mi olacak?

## 13. Karar kaydı

| No | Tarih | Karar | Durum |
|---|---|---|---|
| K-001 | 28.07.2026 | Ana uygulama teknolojisi için Java ve Spring Boot başlangıç adayıdır. | Taslak |
| K-002 | 28.07.2026 | Kart profilleri ATR değerleriyle yapılandırılabilir olacaktır. | Kabul edilen gereksinim |
| K-003 | 28.07.2026 | İmza doğrulama, sertifika doğrulama ve zaman damgası kapsam dahilindedir. | Kabul edilen gereksinim |
| K-004 | 28.07.2026 | Web tabanlı kullanım için merkezi API yanında yerel kart aracısı öngörülmektedir. | Değerlendirilecek |
| K-005 | 28.07.2026 | BTK P1–P4 uygunluğu ile modern ETSI B-B/B-T/B-LT/B-LTA uygunluğu ayrı değerlendirilecektir. | Kabul edildi |
| K-006 | 28.07.2026 | Üretimde salt P1/BES varsayılan olmayacak; en az güvenilir zaman damgalı profil hedeflenecektir. | Faz 0 ürün onayı bekliyor |
| K-007 | 28.07.2026 | Algoritma kuralları kod sabiti değil, yürürlük tarihli ve sürümlü politika olarak yönetilecektir. | Kabul edildi |
| K-008 | 28.07.2026 | Doğrulama sonucu `VALID`, `INVALID`, `INDETERMINATE` olarak ve kriptografik/hukukî uygunluk ayrımıyla raporlanacaktır. | Kabul edildi |
| K-009 | 28.07.2026 | Türkiye e-imza güven kökleri işletim sistemi deposundan ayrı, tarihsel ve denetlenebilir bir depoda yönetilecektir. | Kabul edildi |
| K-010 | 28.07.2026 | Web kullanımında merkezi API ile cihazda çalışan kod imzalı yerel aracı ayrılacaktır. | Ürün onayı bekliyor |
| K-011 | 28.07.2026 | Sunucudan gelen çıplak özet doğrudan imzalanmayacak; imzalı manifest ve yerel belge özeti eşleştirilecektir. | Kabul edildi |
| K-012 | 28.07.2026 | PIN yalnız yerel aracının native güvenli penceresinde alınacak; HTTP API'de PIN alanı bulunmayacaktır. | Kabul edildi |
| K-013 | 28.07.2026 | Yerel aracı kullanıcı ve tenant hesabına cihaz anahtarıyla eşleştirilecektir. | Kabul edildi |
| K-014 | 28.07.2026 | 24 saatlik kesinleştirme işleri kalıcı kuyruk/worker üzerinden yürütülecektir. | Kabul edildi |
| K-015 | 28.07.2026 | İlk uygulama dağıtımı modüler monolit olarak başlayacak; worker ayrı ölçeklenebilecektir. | Önerildi |
| K-016 | 28.07.2026 | Teknik taban Java 21, Maven ve Spring Boot 4.1.0 olacaktır. | Kabul edildi |
| K-017 | 28.07.2026 | Kriptografik çekirdek Spring Framework bağımlılığı olmadan ayrı modülde tutulacaktır. | Kabul edildi |
| K-018 | 28.07.2026 | Veritabanı şeması Flyway ile yönetilecek, Hibernate yalnız doğrulayacaktır. | Kabul edildi |
| K-019 | 28.07.2026 | Yerel geliştirmede H2, üretimde PostgreSQL kullanılacaktır. | Kabul edildi |
| K-020 | 28.07.2026 | API hata sözleşmesi HTTP Problem Details ve makinece okunabilir hata kodları kullanacaktır. | Kabul edildi |
| K-021 | 28.07.2026 | İlk format MVP'si genel dosyalar için ayrık CAdES Baseline B-T olacaktır. | Teknik MVP kararı; ürün onayı bekliyor |
| K-022 | 28.07.2026 | Gerçek BTK politika değerleri olmadan imza yalnız ETSI seviyesiyle raporlanacak, nitelikli/güvenli e-imza iddiası yapılmayacaktır. | Kabul edildi |
| K-023 | 28.07.2026 | Güvenilir kök/alt kök sertifikalar DB'de immutable içerik ve tarihsel snapshot'larla yönetilecektir. | Kabul edildi ve gerçeklendi |
| K-024 | 28.07.2026 | Sertifika silme fiziksel silme yapmayacak; yeni depo sürümünden çıkarma anlamına gelecektir. | Kabul edildi ve gerçeklendi |
| K-025 | 28.07.2026 | Sertifika ve imza doğrulama, doğrulama zamanında yürürlükte olan güven deposu sürümünü kullanacaktır. | Kabul edildi ve gerçeklendi |
| K-026 | 28.07.2026 | Kriptografik geçerlilik ile Türkiye nitelikli/güvenli imza uygunluğu ayrı raporlanacaktır. | Kabul edildi ve gerçeklendi |
| K-027 | 28.07.2026 | Standart değişiklikleri sürümlü kaynak-kod-ayar-test etki haritasıyla yönetilecek; tarihsel politika ezilmeyecektir. | Kabul edildi ve dokümante edildi |
| K-028 | 28.07.2026 | Faz 7 sırası önce CAdES B-LT, sonra B-LTA ve yenileme; ardından PAdES ve XAdES olacaktır. | Kullanıcı öneriyi kabul etti |
| K-029 | 28.07.2026 | Baseline B-LT kanıtları ETSI EN 319 122-1 V1.3.1 uyarınca root `SignedData.certificates/crls` alanlarında taşınacaktır. | Kabul edildi ve gerçeklendi |
| K-030 | 28.07.2026 | B-LTA, `ATSHashIndexV3` içeren `archive-time-stamp-v3` kullanacak ve eski arşiv token'larını değiştirmeden yenilenecektir. | Kabul edildi ve gerçeklendi |

## 14. Değişiklik günlüğü

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 0.1 | 28.07.2026 | İlk proje amacı, kapsam, mimari yaklaşım, fazlar, riskler ve açık sorular oluşturuldu. |
| 0.2 | 28.07.2026 | Faz 1 uyumluluk matrisi bağlandı; profil, algoritma, doğrulama ve güven deposu kararları eklendi. |
| 0.3 | 28.07.2026 | Faz 2 mimarisi, yerel aracı protokolü, API/veri/hata modelleri, STRIDE tehdit modeli ve ADR kararları eklendi. |
| 0.4 | 28.07.2026 | Faz 3 Java/Spring Boot iskeleti, OpenAPI, Flyway, güvenlik profilleri, test ve CI çıktıları tamamlandı. |
| 0.5 | 28.07.2026 | DB tabanlı güvenilir kök/alt kök sertifika deposu, CRUD API, snapshot sürümleme ve doğrulama sağlayıcısı eklendi. |
| 0.6 | 28.07.2026 | Faz 4 akıllı kart yerel aracısı ve Faz 5 CAdES B-T/RFC 3161 yazılım MVP'leri tamamlandı. |
| 0.7 | 28.07.2026 | Faz 6 PKIX, NES/politika, OCSP/SİL, CAdES/zaman damgası doğrulama API'si, ayrıntılı rapor ve standart etki haritası tamamlandı. |
| 0.8 | 28.07.2026 | Faz 7A CAdES B-LT/B-LTA, ATSHashIndexV3, arşiv zaman damgası yenileme, gömülü kanıt doğrulama ve denetim API'si tamamlandı. |

## 15. Faz 8 karar ve ilerleme eki — 29 Temmuz 2026

| No | Karar | Durum |
|---|---|---|
| K-031 | Referans kapasite: sertifika 50/s, imza 20/s, 100 eşzamanlı kullanıcı, 25 MiB belge, normal p95 <2 s, haricî servisli p95 <10 s ve 30 dakika yük. | Kabul edildi |
| K-032 | Referans ortam Linux container, toplam 4 vCPU/8 GiB, ayrı PostgreSQL, iki API örneği, TLS ters vekil ve ayrı Windows kart ajanıdır. | Kabul edildi |
| K-033 | Daha güçlü sunucular daha yüksek kapasite verebilir; her donanım profili ölçümlü testle ayrıca ilan edilir. | Kabul edildi |
| K-034 | Aktif güvenlik saldırıları yalnız kontrollü test ortamına; canlı TSA/OCSP/CRL uçlarına yalnız pasif ve sözleşmeye uygun entegrasyon uygulanır. | Kabul edildi |
| K-035 | Fiziksel kart sonradan temin edilecek; Faz 8 yazılım kabulü tamamlanırken gerçek kart/okuyucu/sürücü kabulü zorunlu bekleyen kapıdır. | Kabul edildi |
| K-036 | Tenant başlığı JWT tenant claim'iyle bağlanır; üretim oturumu stateless olur ve ham belge/istek sınırları katmanlı uygulanır. | Gerçeklendi |
| K-037 | Server-side SMART_CARD isteğinde PIN opsiyoneldir; PIN yoksa güvenli profil credential'ı veya token oturumu denenir, cihaz giriş isterse kararlı hata döner. HSM yalnız güvenli `credentialRef` kullanır. | Gerçeklendi; otomatik test geçti, fiziksel tekrar koşusu açık |

Faz 8'in ayrıntılı adımları, kabul ölçütleri ve kalan işler
`FAZ_8_GUVENLIK_PERFORMANS_VE_KABUL.md` dosyasındadır. Kart temin edildiğinde
`FAZ_8_FIZIKSEL_KART_KABUL_PROSEDURU.md` satır satır uygulanır. Test altyapısının
tamamlanması ölçümlü performans veya fiziksel uyumluluk başarısı olarak yorumlanmaz.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 0.9 | 29.07.2026 | Faz 8 güvenlik sıkılaştırması, referans ölçek topolojisi, yük/CI güvenlik altyapısı ve fiziksel kart kabul prosedürü eklendi. |

## 16. Faz 9 karar ve teslim eki — 29 Temmuz 2026

| No | Karar | Durum |
|---|---|---|
| K-037 | Merkez API satıcıdan bağımsız Kubernetes üzerinde, OpenShift uyum overlay'iyle çalışacaktır. | Kabul edildi |
| K-038 | PostgreSQL uygulama kümesi dışında yönetilen/HA hizmet olacak; başlangıç hedefi RPO 15 dakika, RTO 4 saattir. | Öneri kabul edildi; kurum imzası bekliyor |
| K-039 | Üretim yayını SHA-256 imaj digest'i ve manuel onaylı Argo CD sync ile yapılacak; otomatik prune kapalıdır. | Gerçeklendi |
| K-040 | API en az iki pod, PDB, topology spread, HPA 2–6 ve `maxUnavailable: 0` ile dağıtılacaktır. | Gerçeklendi |
| K-041 | Ajan jpackage MSI, kurum kod imzası ve %5/%25/%100 halkalarıyla dağıtılacaktır. | Paket altyapısı hazır; gerçek imza bekliyor |
| K-042 | Aylık resmî kaynak izlemesi politika/güven deposunu otomatik değiştirmeyecek; insan onaylı yeni sürüm sürecini başlatacaktır. | Gerçeklendi |
| K-043 | `CUSTOM` ve tüm isteğe bağlı politikaları pasif yapan `AUDIT_ONLY` yalnız local/test ortamlarında kullanılacak; diğer ortamlarda tüm politikalar aktif olacak ve zorunlu kriptografik/güven kontrolleri hiçbir ortamda kapatılamayacaktır. | Gerçeklendi |
| K-044 | Politika, güvenilir sertifika, TSA ve server-side cihaz tanımları imzalama demosundan ayrı `/admin/` sayfasında yönetilecek; server-side imza agent gerektirmeyecektir. | Gerçeklendi |
| K-045 | Smart Card Agent başlatıldığında sürekli görünür Swing durum penceresi açacak; PIN yalnız agent'ın işlem anında açtığı yerel pencerede alınacaktır. | Gerçeklendi |
| K-047 | Server-side akıllı kart profillerinde slot otomatik bulunacaktır; sertifika deposunu PIN'siz açmayan sürücülerde keşif tek kullanımlık PIN ile yapılacak, HSM slotu ise yönetici tarafından zorunlu verilecektir. | Gerçeklendi ve AKİS fiziksel kartıyla doğrulandı |

Ayrıntılı teslim ve kalan üretim kapıları `FAZ_9_CANLIYA_GECIS_VE_ISLETIM.md`; günlük
işletim `OPERASYON_KITABI.md`; pilot kararı `FAZ_9_PILOT_VE_CANLI_GECIS.md` içindedir.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 29.07.2026 | Faz 9 Kubernetes/GitOps, izleme/alarmlar, ajan MSI, yedek/olay/süreklilik runbook'ları, kaynak izleme ve pilot planı tamamlandı. |
### K-046 — Hazır kart profili kataloğu ve çok formatlı demo doğrulama

- Server-side akıllı kartlar PC/SC üzerinden taranır; ATR katalog ve maske ile
  eşleşirse profil formu otomatik doldurulur.
- Katalogda doğrulanmış ilk kart AKİS'tir. Bilinmeyen ATR değerleri otomatik
  güvenilir kabul edilmez ve özel profil olarak açıkça tanımlanır.
- Demo CAdES için attached/detached, XAdES için enveloped/enveloping/detached
  ve PAdES için PDF ByteRange/CMS üzerinden doğrulama yapar.
- Doğrulama sonucu tek bir başarılı/başarısız mesaj yerine ana ETSI sonucu,
  format, seviye, sertifika yolu ve politika kontrollerini gösterir.

### K-048 — Paketleme ve algoritma seçimi

- CAdES `ATTACHED` ve `DETACHED`; XAdES `ENVELOPED`, `ENVELOPING` ve `DETACHED`;
  PAdES `ENVELOPED` üretir ve doğrular.
- RSA PKCS#1 ve ECDSA için SHA-256, SHA-384 ve SHA-512 seçenekleri vardır.
- SHA-1 seçeneği yoktur. Sertifika anahtar türü, API oturumu, manifest ve
  PKCS#11 mekanizması birbiriyle eşleşmek zorundadır.
- Eski API istekleri CAdES/XAdES `DETACHED`, PAdES `ENVELOPED` ve anahtar türüne
  göre SHA-256 varsayımıyla geriye uyumlu çalışır.

### K-049 — Süresi dolmuş sertifikayla fiziksel kart kabul testi

- İmzalama sertifikasının tarih kontrolü varsayılan olarak aktiftir ve
  production profilinde kapatılamaz.
- Local/test ortamlarında yönetim ekranındaki `CUSTOM` politika altında
  `İmzalama sertifikası tarih kontrolü` radio seçeneği geçici olarak pasif
  yapılabilir.
- Pasif mod yalnız kart, PKCS#11 özel anahtar ve kriptografik imza akışını
  sınamak içindir. Üretilen çıktı geçerli veya nitelikli e-imza sayılmaz.
- Politika sürümlü olarak DB'de saklanır; varsayılan ve eski kayıtların değeri
  Flyway `V11` migrasyonuyla aktif kabul edilir.

### K-050 — Local manifest anahtarı ve test doğrulama ayrımı

- Production manifest Ed25519 anahtarları secret/credential yönetiminden
  sağlanır. Local/test ortamında otomatik üretilen anahtar çiftinin restartlar
  arasında değişmemesi için `.eimza` altında kalıcı tutulur.
- Agent manifest public key'i ilk kurulumda merkez API'den alınır. Kalıcı local
  anahtar sayesinde her API restartında agent yeniden yapılandırılmaz.
- Sertifika tarih politikası pasifken imza matematiksel olarak açık anahtarla
  doğrulanır; tarih ve güven sonucu `INDETERMINATE` raporlanır. Bu mod geçerli
  e-imza doğrulaması değil, fiziksel kart kabul testidir.

### K-051 — Public sertifika okuma ve ayrıntılı tarih hatası

- Client-side agent sertifika listeleme sırasında hiçbir koşulda PIN istemez.
  Genel kartlarda public Cryptoki `CKO_CERTIFICATE` nesneleri kullanılır.
- AKİS'in SunPKCS11 katmanı oturum açmadan sertifikaları göstermediği için,
  makinede kurulu resmî AKİS CIF kitaplığı opsiyonel adaptör olarak yüklenir
  ve PKCS#15 sertifika dosyaları PC/SC üzerinden PIN'siz okunur. CIF kitaplığı
  agent paketine gömülmez; `EIMZA_AGENT_AKIS_CIF_LIBRARY` ile özel kurulum
  yolu da verilebilir.
- Public oturumda private-key görünürlüğü yoksa dijital imza/non-repudiation
  key-usage taşıyan kart sertifikaları imzalama adayı olarak listelenir; gerçek
  private key eşleşmesi PIN'li imza anında kesinleştirilir. `keyUsage` alanı
  bulunmayan eski AKİS sertifikaları da imzalama adayı kabul edilir.
- Sertifika tarih politikası aktifken CAdES doğrulama, genel `CADES_INVALID`
  yerine imza zamanına göre `SIGNER_CERTIFICATE_EXPIRED` veya
  `SIGNER_CERTIFICATE_NOT_YET_VALID` üretir.

### K-052 — CAdES dosya adı, ayrı doğrulama sayfası ve Swing demo

- CAdES attached ve detached çıktılarının ikisi de CMS/CAdES imza dosyası
  olarak `.p7s` uzantısıyla sunulur; demo `.p7m` üretmez.
- İmzalama demosu `/demo/`, CAdES/XAdES/PAdES doğrulama arayüzü
  `/validation/`, yönetim arayüzü `/admin/` altında ayrı sayfalardır.
- `desktop-signing-demo` modülü merkez API veya tarayıcı olmadan
  `smartcard-agent` ve `signature-cades` kütüphanelerini kullanır. Swing
  uygulaması kart/sertifika seçimi, attached/detached paketleme, RSA/ECDSA
  algoritma seçimi ve yerel tek seferlik PIN ile CAdES-B-B `.p7s` üretir.

### K-053 — Doğrulama sonucunda imzalayan bilgileri

- Web doğrulama raporu CAdES, XAdES ve PAdES için imzalayan sertifikasının ortak
  adı, subject DN, kimlik/seri alanı, kurum/birim, ülke, veren makam, sertifika
  seri numarası, geçerlilik tarihleri, açık anahtar algoritması ve SHA-256 parmak
  izini döndürür.
- Yapısal olarak okunabilen fakat geçersiz olan imzalarda da sertifika bilgileri
  doğrulama sonucundan bağımsız olarak gösterilir.
- Offline masaüstü CAdES doğrulaması aynı imzalayan bilgilerini sonuç alanında
  gösterir. Sertifikada bulunmayan nitelikler “Yok” olarak sunulur.
- Web doğrulama ekranı ve offline masaüstü uygulaması imzalayanın public X.509
  sertifikasını DER kodlu `.cer` dosyası olarak dışa aktarır. Özel anahtar ve PIN
  doğrulama raporuna veya indirilen dosyaya dahil edilmez.

## 17. Faz 11 karar ve teslim eki - 5 Ağustos 2026

### K-054 - Çoklu imza semantiği

- Mevcut tek imza istekleri `SINGLE` varsayılanıyla geriye uyumludur.
- CAdES `PARALLEL`, aynı CMS SignedData içinde bağımsız SignerInfo; `SERIAL`,
  hedef SignerInfo üzerinde CMS counterSignature üretir.
- XAdES `PARALLEL`, DETACHED/ENVELOPING bağımsız Signature öğelerini Çoklu
  kapsayıcıda taşır; `SERIAL`, hedef SignatureValue üzerinde ETSI
  CounterSignature üretir.
- PAdES yalnız `SERIAL` destekler ve imzalı PDF'e incremental revision ekler.
- XAdES `ENVELOPED + PARALLEL`, önceki imzanın belge özetini bozacağı için
  reddedilir.

### K-055 - API, demo ve doğrudan JAR kullanımı

- İmzalama oturumuna `multiSignatureType`, `existingArtifactBase64` ve
  `targetSignatureIndex` alanları eklenmiştir.
- Çoklu imza örnek ekranı `/multi-signature/` altında ilk imza demosundan ayrı
  sunulur; client-side ve server-side akışları destekler.
- `JAVA_KUTUPHANE_ENTEGRASYONU.md`, REST katmanını kullanmadan CAdES/XAdES/PAdES
  servislerini ve akıllı kart yardımcılarını kullanan Java örneklerini içerir.
- Ayrıntılı kararlar, kurallar ve testler
  `FAZ_11_COKLU_IMZA_VE_JAVA_KUTUPHANE_ENTEGRASYONU.md` dosyasındadır.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.1 | 05.08.2026 | Faz 11 CAdES/XAdES paralel-seri, PAdES seri imza, ayrı demo ve doğrudan Java kütüphane entegrasyonu eklendi. |
## 18. Faz 12 — üretim kabulü ve birlikte çalışabilirlik

Faz 1–11 yazılım teslimlerinden sonra kalan ana iş yeni bir demo özelliği değil, gerçek
cihaz/hizmet ve kurum onayıyla üretim kanıtlarını tamamlamaktır. Ayrıntılı kapsam ve çıkış
ölçütleri FAZ_12_URETIM_KABUL_VE_BIRLIKTE_CALISABILIRLIK.md dosyasındadır.

### K-056 — Yazılım teslimi ve üretim kabulü ayrımı

- Otomatik test geçmesi mevzuat, fiziksel cihaz veya canlı hizmet kabulü sayılmaz.
- Geçerli kart, gerçek HSM, canlı TSA/OCSP/SİL ve bağımsız doğrulayıcı kanıtları ayrı
  kayıtlarla tamamlanır.
- XAdES/PAdES B-LT/B-LTA yeni bir geliştirme fazı gerektirir; Faz 12 kabul kapsamına
  uygulanmış özellik gibi yazılmaz.
- Güncel gerçekleşme matrisi PROJECT_STATUS.md, AI/geliştirici devir teslimi
  AI_HANDOFF.md dosyasındadır.
