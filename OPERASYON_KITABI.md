# E-İmza Platformu operasyon kitabı

> Sürüm: 1.0 — 29 Temmuz 2026  
> Kapsam: Merkez API, PostgreSQL bağımlılığı, güven deposu, TSA/OCSP/SİL, OIDC ve Windows ajanı

## 1. Hizmet hedefleri

| Ölçüt | Başlangıç hedefi |
|---|---:|
| Aylık erişilebilirlik | %99,9 |
| Normal API p95 | < 2 saniye |
| Kontrollü haricî servis p95 | < 10 saniye |
| HTTP 5xx oranı | <%1 |
| RPO | ≤15 dakika |
| RTO | ≤4 saat |

Bu hedefler kontrollü pilot öncesi iş sahibi tarafından imzalanır. Faz 8 yük koşusu olmadan
kapasite hedefi “geçti” olarak işaretlenmez.

## 2. Roller

- Hizmet sahibi: SLO, bakım ve canlıya geçiş kararı.
- On-call/operasyon: alarm, ilk tanı, runbook ve eskalasyon.
- Geliştirme: uygulama düzeltmesi ve regresyon testi.
- Bilgi güvenliği: olay, zafiyet, secret ve imza zinciri.
- DBA: HA, PITR, yedek ve geri yükleme.
- PKI/uyumluluk: güven deposu, algoritma, BTK/ETSI ve ESHS/TSA değişikliği.
- Masaüstü yönetimi: imzalı MSI ve ajan dağıtım halkaları.
- Hukuk/KVKK: saklama, bildirim ve hukukî uygunluk kararı.

## 3. Günlük kontrol

1. Erişilebilirlik, 5xx, p95, pod ve DB alarm durumunu kontrol edin.
2. Son GitOps senkronizasyonu ile küme sapmasını karşılaştırın.
3. Son tam yedek ve WAL arşiv zamanını kontrol edin.
4. OIDC/TSA/OCSP/SİL hata ve `INDETERMINATE` eğilimini inceleyin.
5. Yetkisiz/başarısız güven deposu yönetim çağrılarını inceleyin.
6. NTP/clock skew, TLS ve kod imza sertifikası sürelerini kontrol edin.
7. Sonucu vardiya kaydına tarih, kişi ve ticket bağlantısıyla yazın.

## 4. Haftalık/aylık kontrol

- Haftalık: kapasite eğilimi, HPA, DB havuzu, JVM/GC, açık zafiyet ve başarısız ajan kurulumu.
- Aylık: BTK/ETSI/IETF/OWASP kaynak raporu, dinamik sayfaların insan incelemesi ve
  ESHS/TSA politika/sertifika değişiklikleri.
- Aylık: en az bir pod tahliye/geri alma smoke testi.
- Üç aylık: PostgreSQL geri yükleme ve algoritma/güven deposu gözden geçirmesi.
- Her sürüm: SBOM, imaj tarama/imza, fiziksel kart matrisi etkisi ve rollback testi.

## 5. Yayın kapısı

1. Maven verify, CodeQL, bağımlılık inceleme ve container taraması geçer.
2. CycloneDX SBOM, imaj digest ve imza release kaydına bağlanır.
3. Flyway ileri/geri uyumluluk incelemesi tamamlanır.
4. Kubernetes diff ve güvenlik politikası sonucu iki kişi tarafından onaylanır.
5. Test → %5 → %25 → %100 trafiği; her üretim halkası en az 30 dakika gözlenir.
6. 5xx, p95, doğrulama sonucu dağılımı veya güvenlik alarmı aşılırsa otomatik ilerleme durur.
7. Geri alma commit'i ve önceki digest hazır olmadan yayın başlamaz.

## 6. Runbook dizini

- [Alarm müdahale](ops/runbooks/ALARM_MUDAHALE.md)
- [Güvenlik olayı](ops/runbooks/OLAY_MUDAHALE.md)
- [PostgreSQL yedek/geri yükleme](ops/runbooks/POSTGRES_BACKUP_GERI_YUKLEME.md)
- [İş sürekliliği/felaket kurtarma](ops/runbooks/IS_SUREKLILIGI_VE_FELAKET_KURTARMA.md)
- [Kubernetes kurulum/yükseltme](deploy/kubernetes/README.md)
- [Windows ajan dağıtımı](packaging/windows/AJAN_DAGITIM_VE_YUKSELTME.md)

## 7. Değişiklik ve denetim

Doğrudan üretim değişikliği yalnız SEV-1/2 sınırlaması için ve olay komutanı onayıyla yapılır.
Sonrasında GitOps'a yansıtılır. Güven deposu kayıtları fiziksel silinmez; yeni immutable
snapshot üretilir. Tüm zamanlar UTC, kullanıcı arayüzü Europe/Istanbul olarak raporlanır.
