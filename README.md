# E-İmza Platform

[![CI](https://github.com/erbayaskin/e-imza-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/erbayaskin/e-imza-platform/actions/workflows/ci.yml)
[![CodeQL](https://github.com/erbayaskin/e-imza-platform/actions/workflows/codeql.yml/badge.svg)](https://github.com/erbayaskin/e-imza-platform/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Türkiye elektronik imza profillerine göre akıllı kart/HSM ile imza oluşturma,
sertifika/imza doğrulama, zaman damgası ve çoklu imza işlemleri için Java 21 + Spring Boot
tabanlı platform.

Faz 1–11 yazılım teslimleri gerçekleştirilmiştir. Gerçek HSM, geçerli kart matrisi, canlı
ESHS/TSA, bağımsız birlikte çalışabilirlik, performans/sızma/DR ve hukuk/bilgi güvenliği
kabulü tamamlanmadan mevzuata tam uyumluluk veya üretime hazır olma iddiası yapılmamalıdır.

## İlk kez açıyorsanız

- Güncel yetenek ve açıklar: [PROJECT_STATUS.md](PROJECT_STATUS.md)
- Geliştirici/yapay zekâ devir teslimi: [AI_HANDOFF.md](AI_HANDOFF.md)
- Depo çalışma kuralları: [AGENTS.md](AGENTS.md)
- Git/GitHub hazırlık durumu: [REPOSITORY_READINESS.md](REPOSITORY_READINESS.md)
- Katkı akışı: [CONTRIBUTING.md](CONTRIBUTING.md)
- Proje yönetişimi: [GOVERNANCE.md](GOVERNANCE.md)
- Güvenlik bildirimi: [SECURITY.md](SECURITY.md)
- Destek kanalları: [SUPPORT.md](SUPPORT.md)
- Public yayın kontrolü: [GITHUB_PUBLICATION_CHECKLIST.md](GITHUB_PUBLICATION_CHECKLIST.md)
- Üçüncü taraf lisans denetimi: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)

## Modüller

| Modül | Sorumluluk |
|---|---|
| `signature-core` | Format bağımsız modeller, politika ve imzalama durum makinesi |
| `certificate-validation` | Tarihsel güven deposuyla PKIX yolu, NES/politika, SİL/CRL ve OCSP doğrulaması |
| `timestamp-client` | RFC 3161 zaman damgası istemcisi ve token doğrulaması |
| `signature-cades` | CAdES B-B/B-T, B-LT/B-LTA, paralel/seri imza, yenileme ve doğrulama |
| `signature-xades` | Enveloped/enveloping/detached XAdES B-B/B-T, paralel/seri imza ve doğrulama |
| `signature-pades` | PAdES B-B/B-T PDF byte-range, incremental seri imza ve doğrulama |
| `smartcard-agent` | Çalıştırılabilir loopback yerel aracı; PC/SC, ATR, PKCS#11, yerel PIN ve ham özet imzalama |
| `desktop-signing-demo` | API/agent gerektirmeyen offline Swing CAdES imzalama ve doğrulama örneği |
| `signature-api` | REST API, persistence/Flyway, yönetim, demo ve gözlemlenebilirlik |

## Gereksinimler

- Java 21
- Maven 3.8.8 veya üzeri
- Üretim için PostgreSQL

## Derleme

```shell
mvn clean verify
```

PowerShell ve proje içi Maven deposu kullanımı:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" clean verify
```

## Yerel çalıştırma

`local` profil bellek içi H2 veritabanını kullanır:

```shell
mvn -pl signature-api -am package "-DskipTests"
java -jar signature-api/target/signature-api-0.1.0-SNAPSHOT-exec.jar --spring.profiles.active=local
```

Çok modüllü kök projede `-am spring-boot:run` kullanmayın; hedef üst `pom`
modülünde de çalışarak `Unable to find a suitable main class` hatası verir.

Sağlık kontrolü: `http://localhost:8080/actuator/health`

Uçtan uca kart test arayüzü: `http://localhost:8080/demo/`

Paralel / seri imza test arayüzü: `http://localhost:8080/multi-signature/`

İmza/sertifika doğrulama arayüzü: `http://localhost:8080/validation/`

Yönetim ve tanımlama ekranı: `http://localhost:8080/admin/`

- Yönetim ekranı PC/SC okuyucularını tarar, ATR değerini hazır kart kataloğuyla
  eşleştirir ve AKİS gibi kartların server-side profilini otomatik oluşturabilir.
- Demo ekranı CAdES attached/detached, XAdES enveloped/enveloping/detached ve
  PAdES imzalarını doğrular; paketleme türü ile politika kontrollerini raporlar.

Agent, yönetim ekranı ve agent gerektirmeyen server-side test akışı:
[YONETIM_EKRANI_VE_AGENT_REHBERI.md](YONETIM_EKRANI_VE_AGENT_REHBERI.md)

Faz 10 teknik akışı: [FAZ_10_UCTAN_UCA_CADES_XADES_PADES.md](FAZ_10_UCTAN_UCA_CADES_XADES_PADES.md)

Faz 11 Çoklu imza akışı:
[FAZ_11_COKLU_IMZA_VE_JAVA_KUTUPHANE_ENTEGRASYONU.md](FAZ_11_COKLU_IMZA_VE_JAVA_KUTUPHANE_ENTEGRASYONU.md)

Doğrudan Java/JAR entegrasyonu:
[JAVA_KUTUPHANE_ENTEGRASYONU.md](JAVA_KUTUPHANE_ENTEGRASYONU.md)

OpenAPI dosyası: `signature-api/src/main/resources/static/openapi/e-signature-api-v1.yaml`

Client-side agent için önce paylaşılabilir örneği makineye özel dosyaya kopyalayın:

```powershell
Copy-Item .\agent-local.example.yml .\agent-local.yml
```

`agent-local.yml` Git'e alınmaz. ATR ve PKCS#11 yolu kuruluma göre bu yerel dosyada
değiştirilir; PIN hiçbir yapılandırma dosyasına yazılmaz.

Güvenilir kök/alt kök sertifika deposu:
[GUVENILIR_SERTIFIKA_DEPOSU.md](GUVENILIR_SERTIFIKA_DEPOSU.md)

## Üretim profili

```shell
java -jar signature-api/target/signature-api-0.1.0-SNAPSHOT-exec.jar --spring.profiles.active=prod
```

Güvenli varsayılan `prod` profilidir ve gerekli ortam değişkenleri yoksa uygulama
başlamaz.

Gerekli ortam değişkenleri:

- `EIMZA_DB_URL`
- `EIMZA_DB_USERNAME`
- `EIMZA_DB_PASSWORD`
- `EIMZA_OIDC_ISSUER_URI`
- `EIMZA_OIDC_AUDIENCE` (varsayılan `eimza-api`)
- `EIMZA_OIDC_JWS_ALGORITHMS` (varsayılan yalnız `RS256`)
- `EIMZA_TENANT_CLAIM` (varsayılan `tenant_id`)

## Dokümantasyon

- [Proje planı](E_IMZA_PROJE_PLANI.md)
- [Faz 1 uyumluluk matrisi](FAZ_1_UYUMLULUK_MATRISI.md)
- [Faz 2 mimari ve tehdit modeli](FAZ_2_MIMARI_VE_TEHDIT_MODELI.md)
- [Faz 3 teknik iskelet](FAZ_3_TEKNIK_ISKELET.md)
- [Faz 4 akıllı kart ve yerel aracı](FAZ_4_AKILLI_KART_YEREL_ARACI.md)
- [Faz 5 CAdES ve zaman damgası](FAZ_5_CADES_VE_ZAMAN_DAMGASI.md)
- [Faz 6 sertifika ve imza doğrulama motoru](FAZ_6_DOGRULAMA_MOTORU.md)
- [Faz 7 CAdES uzun dönem doğrulama](FAZ_7_CADES_UZUN_DONEM.md)
- [Faz 8 güvenlik, performans ve kabul](FAZ_8_GUVENLIK_PERFORMANS_VE_KABUL.md)
- [Faz 9 canlıya geçiş ve işletim](FAZ_9_CANLIYA_GECIS_VE_ISLETIM.md)
- [Faz 10 uçtan uca CAdES/XAdES/PAdES](FAZ_10_UCTAN_UCA_CADES_XADES_PADES.md)
- [Faz 11 Çoklu imza ve Java kütüphane entegrasyonu](FAZ_11_COKLU_IMZA_VE_JAVA_KUTUPHANE_ENTEGRASYONU.md)
- [Faz 12 üretim kabulü ve birlikte çalışabilirlik](FAZ_12_URETIM_KABUL_VE_BIRLIKTE_CALISABILIRLIK.md)
- [Doğrudan Java/JAR entegrasyon örnekleri](JAVA_KUTUPHANE_ENTEGRASYONU.md)
- [Standart değişikliği etki haritası](STANDART_DEGISIKLIGI_ETKI_HARITASI.md)
- [Güvenilir sertifika deposu](GUVENILIR_SERTIFIKA_DEPOSU.md)

## Faz 8 güvenlik ve performans

Faz 8 yazılım güvenlik sıkılaştırması, iki örnekli referans container topolojisi, k6 yük
senaryoları ve CI güvenlik kapıları hazırlanmıştır. Henüz tarihli 30 dakikalık kapasite ölçümü,
bağımsız kontrollü sızma testi ve fiziksel kart kabulü yapılmadığından üretim kabulü açık
durumdadır.

- [Faz 8 güvenlik, performans ve kabul planı](FAZ_8_GUVENLIK_PERFORMANS_VE_KABUL.md)
- [Fiziksel kart kabul prosedürü](FAZ_8_FIZIKSEL_KART_KABUL_PROSEDURU.md)
- [Yük testi çalıştırma rehberi](performance/README.md)
- [Güvenlik bildirim politikası](SECURITY.md)

## Faz 9 canlıya geçiş ve işletim

Kubernetes/OpenShift uyumlu üretim manifestleri, Argo CD şablonları, Prometheus alarmları,
Windows ajan MSI üretimi, yedek/olay/felaket runbook'ları ve kademeli pilot planı hazırlandı.
Gerçek cluster/registry/secret değerleri, imzalı MSI, Faz 8 kabul kapıları ve pilot
tamamlanmadan üretim canlı kabulü yapılmış sayılmaz.

- [Faz 9 teslim ve kabul durumu](FAZ_9_CANLIYA_GECIS_VE_ISLETIM.md)
- [Operasyon kitabı](OPERASYON_KITABI.md)
- [Pilot ve canlı geçiş](FAZ_9_PILOT_VE_CANLI_GECIS.md)
- [Kullanıcı rehberi](KULLANICI_REHBERI.md)
- [Kubernetes dağıtımı](deploy/kubernetes/README.md)
- [Windows ajan dağıtımı](packaging/windows/AJAN_DAGITIM_VE_YUKSELTME.md)

## Katkı ve lisans

Proje [Apache License 2.0](LICENSE) ile lisanslanır; telif bildirimi [NOTICE](NOTICE)
dosyasındadır. Dış katkılar pull request ve [DCO 1.1](DCO) sign-off ile kabul edilir.
Katkıdan önce [CONTRIBUTING.md](CONTRIBUTING.md), [GOVERNANCE.md](GOVERNANCE.md) ve
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) okunmalıdır.
