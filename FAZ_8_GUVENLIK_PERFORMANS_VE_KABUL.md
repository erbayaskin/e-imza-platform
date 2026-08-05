# Faz 8 — Güvenlik, performans ve kabul

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Tarih: 29 Temmuz 2026  
> Durum: Yazılım güvenlik ve performans test altyapısı tamamlandı; ölçümlü yük testi,
> kontrollü sızma testi ve fiziksel kart kabulü bekliyor.

## 1. Amaç ve kabul sınırı

Bu fazın amacı API'yi güvenli varsayılanlarla sertleştirmek, saldırı yüzeyini otomatik
negatif testlerle küçültmek, yatay ölçeklenebilir referans ortamı kurmak ve ölçülebilir
kapasite kabulünü tanımlamaktır. Test altyapısının var olması performans hedefinin geçtiği
anlamına gelmez. Sonuç ancak tarihli rapor ve ham ölçüm çıktılarıyla “geçti” yapılır.

Fiziksel kart henüz temin edilmediği için yazılım PKCS#11 testleri bu fazdadır; kart,
okuyucu ve üretici sürücüsüyle canlı kabul `FAZ_8_FIZIKSEL_KART_KABUL_PROSEDURU.md`
uyarınca sonradan yapılacaktır.

## 2. Kabul edilen performans tabanı

| Ölçüt | Referans hedef |
|---|---:|
| Sertifika doğrulama | 50 işlem/saniye |
| İmza doğrulama | 20 işlem/saniye |
| Eşzamanlı kullanıcı | 100 |
| Azami ham belge | 25 MiB |
| Normal uygulama akışı p95 | 2 saniyeden az |
| Kontrollü TSA/OCSP akışı p95 | 10 saniyeden az |
| Kesintisiz yük süresi | 30 dakika |
| Hatalı HTTP istek oranı | %1'den az |

Referans düğüm Linux container, toplam 4 vCPU/8 GiB RAM, ayrı PostgreSQL, en az iki API
örneği ve TLS sonlandıran ters vekildir. Daha güçlü sunucularda kapasite daha yüksek olabilir;
her donanım sınıfı ayrı 30 dakikalık test sonucu olmadan ilan edilmez.

## 3. Gerçekleştirilen güvenlik kontrolleri

1. Üretim OAuth2 kaynak sunucusu stateless çalışır; yönetim, doğrulama ve imzalama
   kapsamları uç bazında ayrıdır. Issuer yanında `eimza-api` audience ve izinli JWS
   algoritma listesi doğrulanır.
2. `X-Tenant-Id`, JWT içindeki yapılandırılabilir `tenant_id` talebiyle eşleşmeden
   imzalama oturumuna erişemez. Böylece nesne düzeyi yatay yetki aşımı kapatılır.
3. Belge ve imza 25 MiB, sertifika 1 MiB, tek iptal kanıtı 5 MiB; ara sertifika ve
   kanıt listeleri ayrıca adet sınırına tabidir.
4. 35 MiB üzerindeki bildirilmiş HTTP gövdeleri uygulamaya ulaşmadan 413 döner.
   Ters vekilde de `client_max_body_size 35m` uygulanır.
5. Üretimde CSP, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` ve HSTS
   başlıkları etkinleştirilmiştir.
6. Yerel akıllı kart aracısı yalnız `127.0.0.1` üzerinde dinler; loopback adresi,
   Host, Origin allowlist ve POST ajan başlığı kontrol edilir. PIN HTTP modeline girmez.
7. OCSP/SİL istemcisi yönlendirme izlemez, süre aşımı ve özel/loopback hedef denetimleri
   uygular. Canlı üçüncü taraflara aktif saldırı yükü gönderilmez.
8. Container root olmayan UID ile, salt okunur kök dosya sistemi, düşürülmüş Linux
   yetenekleri ve `no-new-privileges` ile çalıştırılır.

## 4. Güvenlik test planı

Aktif testler yalnız yerel veya kurumun açıkça yetkilendirdiği kontrollü test ortamında yapılır.

| Alan | Zorunlu senaryo | Kabul |
|---|---|---|
| Kimlik doğrulama | tokensız, süresi geçmiş, yanlış issuer/audience/alg | 401; işlem yok |
| Fonksiyon yetkisi | validate token ile admin API, sign token ile admin API | 403 |
| Nesne yetkisi | JWT tenant A + başlık tenant B | 403 `TENANT_SCOPE_VIOLATION` |
| Tekrar oynatma | aynı idempotency anahtarı, iptal/terminal oturum | tek sonuç veya açık 409 |
| Kaynak tüketimi | 25 MiB üstü alan, 35 MiB üstü gövde, uzun liste | 400/413; OOM yok |
| ASN.1/CMS/X.509 | kesik, rastgele, derin/bozuk DER | kontrollü 4xx/INVALID; stack trace yok |
| SSRF | localhost, özel IP, DNS yeniden bağlama deneyi, redirect | ağ isteği yok |
| Haricî servis | yavaş, imzasız, eski, yanlış nonce/policy OCSP/TSA/SİL | timeout veya açık INDETERMINATE/INVALID |
| Ajan | uzak adres, sahte Host/Origin, eksik ajan başlığı | 403 |
| Yapılandırma | local profil, açık actuator, varsayılan parola, TLS zayıflığı | üretimde reddedilir |

Bağımsız sızma testi raporu; kapsam, araç sürümü, test zamanı, bulgu/şiddet, kanıt,
düzeltme sürümü ve yeniden test sonucunu içermelidir. Kritik/yüksek açık açıkken canlıya
çıkılmaz.

## 5. Adım adım referans ortam ve yük testi

1. `deploy/phase8/.env.example` dosyasını gizli değer yöneticisinden gelen test değerleriyle
   `.env` olarak doldurun; dosyayı sürüm kontrolüne eklemeyin.
2. Test TLS sertifikasını `deploy/phase8/certs/server.crt` ve `server.key` olarak koyun.
3. OIDC issuer'ın container ağından erişilebilir ve token audience/issuer kontrolünün etkin
   olduğunu doğrulayın.
4. `docker compose --env-file .env -f deploy/phase8/docker-compose.yml up --build` çalıştırın.
5. Test kök/alt kök sertifikalarını yalnız test DB'sine yükleyin ve snapshot kimliğini kaydedin.
6. `performance/README.md` uyarınca önce sertifika, sonra imza senaryosunu 30 dakika çalıştırın.
7. Tek API ile sonucu kaydedin; ardından iki API örneğiyle aynı vektör ve oranı tekrarlayın.
8. k6 özeti, Prometheus metriği, CPU/RAM/GC, DB havuzu/sorgu ve haricî servis gecikmesini
   aynı zaman aralığıyla arşivleyin.
9. Eşiklerden biri geçmezse kapasiteyi “başarısız” işaretleyin; darboğazı düzeltip testi
   yeni koşu kimliğiyle baştan çalıştırın.
10. Güçlü sunucu testi için işlem oranını kademeli artırın. Geçen son oranı o donanım
    profilinin kapasitesi olarak kaydedin.

## 6. CI ve yazılım tedarik zinciri

- Maven `clean verify`, Enforcer dependency convergence ve birim/entegrasyon testleri zorunludur.
- CycloneDX aggregate JSON/XML SBOM üretilip CI çıktısı olarak saklanır.
- Pull request bağımlılık incelemesi orta ve üzeri yeni bilinen açıkları engeller.
- CodeQL Java/Kotlin `security-extended` analizi ayrı işte çalışır.
- Üretim sürümü öncesi kullanılan base image digest ile sabitlenmeli, SBOM imzalanmalı,
  container zafiyet taraması kurumun onaylı tarayıcısıyla yapılmalıdır.
- Kritik/yüksek açık için istisna; risk sahibi, son tarih ve telafi kontrolü olmadan verilemez.

## 7. Güncel başvuru tabanı

- OWASP ASVS 5.0.0 (Mayıs 2025)
- OWASP API Security Top 10 — 2023
- NIST SP 800-218 SSDF 1.1
- IETF RFC 8725 JWT Best Current Practices
- ETSI TS 119 312 V2.1.1 (Haziran 2026)

ETSI V2.1.1, post-kuantum geçişi ve hibrit şemaları da kapsar. Mevcut MVP RSA/ECDSA
tabanındadır; PQC/hibrit üretim desteği ayrıca tasarlanıp politika, sağlayıcı ve birlikte
çalışabilirlik onayından geçmeden “V2.1.1'in tamamı uygulanmıştır” denmez.

## 8. Faz çıkış matrisi

| Çıktı | Durum | Kanıt |
|---|---|---|
| Tenant/JWT ve kaynak sınırı sıkılaştırması | Tamamlandı | otomatik testler ve `signature-api/security` |
| İki API + PostgreSQL + TLS gateway referansı | Tamamlandı | `deploy/phase8` |
| 30 dakikalık k6 senaryoları | Hazır, ölçüm bekliyor | `performance` |
| SBOM/CodeQL/bağımlılık CI kapıları | Tamamlandı | `.github/workflows` |
| Kontrollü bağımsız sızma testi | Bekliyor | tarihli dış test raporu |
| Gerçek kart/okuyucu/sürücü kabulü | Bekliyor | fiziksel kart matrisi |
| Canlı ESHS/TSA birlikte çalışabilirliği | Bekliyor | sağlayıcı test raporu |

Faz 8 yazılım teslimatı tamamlanmış sayılır; üretim kabulü son üç “bekliyor” satırı
geçmeden tamamlanmış sayılmaz.
