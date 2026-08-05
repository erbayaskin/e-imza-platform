# Faz 9 — Canlıya geçiş ve işletim

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Tarih: 29 Temmuz 2026  
> Durum: Üretim dağıtım ve operasyon yazılım paketi tamamlandı; gerçek ortam kurulumu,
> Faz 8 kabul kapıları ve kademeli pilot bekliyor.

## 1. Kabul edilen mimari

- Satıcıdan bağımsız Kubernetes; OpenShift için ayrı UID/GID overlay'i.
- En az iki API pod'u; HPA ile 2–6 pod.
- Yönetilen/HA PostgreSQL küme dışında.
- TLS Ingress'te; dışarı yalnız `/api`.
- Secret değerleri kaynak kod/YAML yerine kurum secret yöneticisinden.
- GitOps/Argo CD; üretimde otomatik prune ve otomatik sync kapalı.
- Container imajı tag ile değil doğrulanmış SHA-256 digest ile.
- Windows akıllı kart ajanı imzalı MSI ve %5 → %25 → %100 dağıtım halkalarıyla.

## 2. Gerçekleştirilen çıktılar

| Çıktı | Yer | Durum |
|---|---|---|
| Kubernetes Deployment/Service/Ingress | `deploy/kubernetes/base` | Tamamlandı |
| PDB, topology spread ve HPA | `deploy/kubernetes/base` | Tamamlandı |
| Restricted güvenlik ve NetworkPolicy | `deploy/kubernetes/base` | Tamamlandı |
| Üretim ve OpenShift overlay'i | `deploy/kubernetes/overlays` | Tamamlandı |
| Argo CD Application/AppProject | `deploy/gitops` | Şablon tamamlandı |
| Prometheus scrape ve alarm kuralları | `deploy/kubernetes/monitoring` | Tamamlandı |
| Windows imzalı MSI üretim betiği | `packaging/windows` | Hazır; sertifika/WiX/signtool bekliyor |
| PostgreSQL PITR/restore runbook'u | `ops/runbooks` | Tamamlandı |
| Olay müdahale ve alarm kataloğu | `ops/runbooks` | Tamamlandı |
| İş sürekliliği/felaket kurtarma | `ops/runbooks` | Tamamlandı |
| Aylık resmî kaynak izleme | `.github/workflows/compliance-monitor.yml` | Tamamlandı |
| Operasyon kitabı ve kullanıcı rehberi | kök MD dosyaları | Tamamlandı |
| Pilot/go-no-go matrisi | `FAZ_9_PILOT_VE_CANLI_GECIS.md` | Hazır; uygulama bekliyor |

## 3. Güvenli yayın akışı

1. Maven verify, CodeQL, dependency review ve container taraması geçer.
2. SBOM, imaj digest, imza ve test raporu aynı release kimliğine bağlanır.
3. Flyway değişikliği geriye uyumlu expand/migrate/contract kuralıyla incelenir.
4. Üretim overlay'indeki digest pull request ile değiştirilir.
5. `kubectl kustomize` ve politika kontrolleri geçer; iki kişi onaylar.
6. Argo CD yayın penceresinde manuel sync edilir.
7. Rollout, readiness, 5xx, p95, DB havuzu ve sentetik imza/doğrulama izlenir.
8. Eşik ihlalinde halka ilerlemez; önceki digest'i getiren Git commit'i uygulanır.

## 4. Alarm ve SLO tabanı

| Alarm | Eşik |
|---|---|
| API tamamen erişilemiyor | 2 dakika |
| İki örnekten az | 5 dakika |
| 5xx oranı | >%1, 10 dakika |
| Normal p95 | >2 s, 10 dakika |
| JVM heap | >%85, 10 dakika |
| DB havuzu | >%80, 10 dakika |
| Pod yeniden başlama | 15 dakikada >2 |

Prometheus tavsiyesine uygun olarak sayfalama kullanıcı etkisi ve eyleme dönüşebilir
belirtilere dayanır. TSA/OCSP, NTP, TLS/kod imza sertifikası ve yedek yaşı kurum izleme
sisteminden ayrıca beslenmelidir.

## 5. Yedek ve süreklilik

Başlangıç önerisi RPO ≤15 dakika, RTO ≤4 saat, 35 günlük çevrimiçi PITR, günlük tam yedek
ve üç aylık yalıtılmış restore tatbikatıdır. Gerçek sağlayıcı politikası pilot öncesi DBA,
iş sahibi, güvenlik ve hukuk tarafından imzalanır.

## 6. Standart ve güven deposu işletimi

1. GitHub zamanlanmış işi resmî BTK, ETSI, OWASP ve NIST kaynaklarının erişim, sürüm
   işareti, boyut ve SHA-256 raporunu aylık üretir.
2. Sabit ETSI PDF'si sabitlenen SHA-256 ile otomatik karşılaştırılır. Dinamik web
   sayfalarının hash'i yalnız kanıttır; aylık sürüm/mevzuat farkı insan tarafından incelenir.
   Fark PKI/uyumluluk ticket'ı açar; otomatik üretim politikası değişikliği yapılmaz.
3. Normatif fark ve yürürlük tarihi çıkarılır.
4. Yeni algoritma politika sürümü veya immutable güven snapshot'ı hazırlanır.
5. Tarihsel doğrulama regresyonu, fiziksel kart etkisi ve bağımsız birlikte çalışabilirlik
   tamamlanır.
6. Hukuk, güvenlik ve ürün onayından sonra yürürlüğe alınır.

## 7. Tamamlanmayan üretim kapıları

- Gerçek üretim registry/cluster/Ingress/secret manager değerleri.
- Kurum kod imzalama sertifikasıyla gerçek MSI.
- Fiziksel kart/okuyucu/sürücü kabulü.
- Faz 8 30 dakikalık ölçümlü performans ve bağımsız sızma testi.
- Canlı OIDC, ESHS/TSA ve HA PostgreSQL birlikte çalışabilirliği.
- Restore, felaket kurtarma ve on-call tatbikatı.
- Pilot go/no-go tablosundaki tüm imzalar.

Bu nedenle Faz 9'un tekrar üretilebilir yazılım/operasyon paketi hazırdır; “canlı üretim
sürümü” veya mevzuata tam uygunluk henüz ilan edilmez.
