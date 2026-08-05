# Faz 12 — Üretim Kabulü ve Birlikte Çalışabilirlik

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Durum: Planlandı; dış sistem, cihaz ve kurum onayları bekleniyor  
> Başlangıç belgesi: 5 Ağustos 2026  
> Ön koşul: Faz 1–11 yazılım teslimleri ve otomatik testleri

## 1. Amaç

Bu faz yeni bir demo özelliği eklemekten çok, mevcut yazılımın gerçek Türkiye e-imza
ekosisteminde kanıtlanabilir biçimde çalıştığını ve üretim işletim şartlarını karşıladığını
gösterecektir. Faz tamamlanmadan “üretime hazır” veya “tam mevzuat uyumlu” beyanı yapılmaz.

## 2. Kapsam

### 2.1 Fiziksel akıllı kart kabulü

- En az bir geçerli NES içeren AKİS kartı
- Kurumca hedeflenen diğer kart/middleware kombinasyonları
- Windows; ürün hedefliyorsa macOS ve Linux
- Birden çok okuyucu ve aynı anda birden çok kart
- Public sertifikayı PIN'siz listeleme
- Yanlış/doğru PIN, PIN kilitlenme uyarısı ve kart çıkarma
- RSA ve kart destekliyorsa EC algoritmaları
- CAdES/XAdES/PAdES tek imza; CAdES/XAdES paralel-seri ve PAdES seri imza

Mevcut geliştirme kartının sertifikası süresi dolmuş olduğundan donanım entegrasyon kanıtı
sağlayabilir fakat hukuken geçerli imza kabul vektörü yerine kullanılamaz.

### 2.2 HSM kabulü

- Gerçek üretici PKCS#11 kitaplığı ve onaylı sürüm
- Slot/partition ve sertifika seçimi
- Secret manager üzerinden `credentialRef`
- Aynı slotta concurrency, session limiti ve timeout
- Yanlış credential, token offline, failover ve yeniden bağlanma
- RSA/ECDSA mekanizma eşlemesi
- Audit logunda PIN/secret/private key bulunmadığının doğrulanması

### 2.3 Canlı güven hizmetleri

- Onaylı ESHS kök ve alt köklerinin parmak izi iki kanaldan doğrulanarak DB'ye alınması
- En az bir canlı RFC 3161 TSA ile B-T üretimi ve doğrulaması
- OCSP GOOD/REVOKED/UNKNOWN cevapları
- SİL/CRL geçerli, eski, yanlış issuer ve iptal edilmiş seri senaryoları
- Ağ kesintisinde timeout/retry ve `INDETERMINATE` davranışı
- Tarihsel trust store/policy snapshot ile eski tarihli doğrulama

### 2.4 Haricî birlikte çalışabilirlik

Her artifact için en az bir bağımsız doğrulayıcı/ürün sonucu kaydedilir:

| Vektör | Bu proje | Haricî ürün | Kanıt |
|---|---|---|---|
| CAdES ATTACHED/DETACHED B-B/B-T | Bekliyor | Bekliyor | Dosya SHA-256 + rapor |
| CAdES paralel/counter-signature | Bekliyor | Bekliyor | Tüm imzalayanlar |
| CAdES B-LT/B-LTA/yenileme | Bekliyor | Bekliyor | Kanıt ve timestamp zinciri |
| XAdES üç paketleme türü | Bekliyor | Bekliyor | XML ve doğrulama raporu |
| XAdES paralel/CounterSignature | Bekliyor | Bekliyor | Tüm `ds:Signature` sonuçları |
| PAdES B-B/B-T | Bekliyor | Bekliyor | PDF imza sözlüğü raporu |
| PAdES iki veya daha fazla seri imza | Bekliyor | Bekliyor | Her revision sonucu |

Haricî ürün imzası da bu projede doğrulanmalıdır; test tek yönlü olmamalıdır.

### 2.5 Performans, güvenlik ve işletim

- `performance/` senaryolarıyla tarihli 30 dakikalık yük testi
- Küçük/orta/azami belge ve tek/çoklu imza profilleri
- 70 MiB varsayılan istek sınırının kaynak tüketimi ölçümü
- Bağımsız kontrollü sızma testi
- SBOM, dependency review ve CodeQL sonuçlarının kapatılması
- PostgreSQL backup/restore ve RPO/RTO tatbikatı
- Kubernetes probe, PDB, rollout, rollback ve secret rotation
- Agent/MSI kod imzası ve %5/%25/%100 pilot halkaları

## 3. Kabul kaydı şablonu

Her test için aşağıdaki alanlar doldurulur:

| Alan | Değer |
|---|---|
| Test kimliği | `F12-...` |
| Tarih/saat ve saat dilimi | |
| Ortam ve uygulama sürümü | |
| İşletim sistemi/JDK | |
| Kart/HSM/okuyucu | |
| Middleware ve PKCS#11 SHA-256 | |
| ATR/slot veya HSM partition | |
| Sertifika issuer/seri/geçerlilik | |
| Format/paketleme/seviye/algoritma | |
| Politika ve trust store sürümü | |
| TSA/OCSP/SİL sağlayıcısı | |
| Artifact SHA-256 | |
| Proje doğrulama sonucu | |
| Haricî ürün sonucu | |
| Log correlation ID | |
| Beklenen/gerçek sonuç | |
| İnceleyen ve onaylayan | |

PIN, özel anahtar, bearer token, HSM secret ve kişisel belge içeriği kanıt paketine
konulmaz.

## 4. Zorunlu negatif testler

1. Yanlış PIN ve kilitlenmeye yaklaşan PIN.
2. Kartın imza sırasında çıkarılması.
3. ATR eşleşen fakat PKCS#11 kitaplığı yanlış profil.
4. Yanlış HSM slotu/credential ve offline cihaz.
5. Bozuk Base64, belge digest uyuşmazlığı ve azami boyut aşımı.
6. Süresi dolmuş/henüz geçerli olmayan/iptal edilmiş sertifika.
7. Güvenilmeyen kök ve eksik ara sertifika.
8. Bozuk CAdES/XAdES/PAdES imza baytı ve yanlış detached içerik.
9. Yanlış TSA imprint/nonce/policy ve güvenilmeyen TSA zinciri.
10. Çoklu imzada bir imzalayanın veya counter-signature'ın bozulması.
11. Tenant/scope/idempotency ihlali.
12. Agent Origin/Host/manifest/device signature ihlali.

## 5. Faz çıkış ölçütleri

Faz 12 yalnız aşağıdakilerin tamamı sağlandığında kapanır:

- En az bir geçerli kart ve bir gerçek HSM kabul matrisi geçti.
- Canlı TSA ve ESHS iptal servisleriyle pozitif/negatif kanıt toplandı.
- CAdES/XAdES/PAdES tek ve çoklu imzaları bağımsız ürünlerle iki yönlü doğrulandı.
- Yük testi eşikleri kaynak profiliyle birlikte onaylandı.
- Bağımsız sızma testinde açık kritik/yüksek bulgu kalmadı.
- DR ve rollback tatbikatları RPO/RTO hedeflerini karşıladı.
- Hukuk, KVKK, bilgi güvenliği, operasyon ve ürün sahibi imzalı go/no-go verdi.
- `PROJECT_STATUS.md`, faz belgesi ve release notu gerçek kanıt bağlantılarıyla güncellendi.

## 6. Faz sonrasında olası geliştirme

XAdES/PAdES B-LT/B-LTA, kurumsal Java SDK/BOM yayını veya yeni algoritma/PQC desteği
istenirse ayrı bir geliştirme fazı açılır; Faz 12 kabul çalışmasına sessizce eklenmez.
