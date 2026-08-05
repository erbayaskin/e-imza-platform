# E-İmza Platformu — Geliştirme Talimatları

Bu dosya depo kökünün ve bütün alt modüllerin çalışma kurallarını tanımlar. İnsan veya
yapay zekâ destekli her geliştirmede önce bu dosya, sonra `AI_HANDOFF.md` ve
`PROJECT_STATUS.md` okunmalıdır.

## 1. Kaynakların öncelik sırası

Çelişki olduğunda aşağıdaki sıra kullanılır:

1. Çalışan kod, Flyway şeması ve otomatik testler
2. `PROJECT_STATUS.md` içindeki güncel yetenek ve açıklar
3. `AI_HANDOFF.md` içindeki mimari sınırlar ve değişiklik rotası
4. `E_IMZA_PROJE_PLANI.md` içindeki karar kayıtları
5. İlgili `FAZ_*.md` kapanış belgesi
6. README ve kullanıcı/operasyon rehberleri

Faz belgeleri tarihsel kararları da saklar. Eski bir fazdaki “sonraki fazda yapılacak”
ifadesi güncel kodla çelişirse `PROJECT_STATUS.md` esas alınır; tarihsel metin silinmez,
gerekirse üstüne güncel duruma yönlendiren not eklenir.

## 2. Değiştirilemez güvenlik sınırları

- Özel anahtar akıllı karttan veya HSM'den dışarı çıkarılmaz.
- PIN loglanmaz, DB'ye yazılmaz, manifestte taşınmaz ve client-side akışta tarayıcıya
  verilmez. Client-side PIN yalnız Smart Card Agent Swing penceresinde alınır.
- Public X.509 sertifikası PIN'siz okunmaya çalışılır; middleware zorunlu kılarsa yerel
  fallback ayrı ve açık davranış olmalıdır.
- Client-side yalnız akıllı karttır. HSM yalnız server-side akışta kullanılabilir.
- SMART_CARD profili ATR ile eşleşir ve slotu otomatik keşfeder. HSM profili ATR içermez;
  PKCS#11 kitaplık yolu ve slotu yönetici tarafından tanımlanır.
- İstemci isteğinden PKCS#11 DLL/SO yolu, slot veya `credentialRef` kabul edilmez.
- Güvenilir depo yalnız kök/alt kök CA sertifikalarını içerir; son kullanıcı sertifikası
  trust anchor yapılmaz. Değişiklikler tarihsel snapshot üretir.
- Üretimde sertifika tarih, güven yolu, iptal ve politika kontrolleri sessizce
  gevşetilemez. Local/test istisnası hukuken geçerli imza iddiası oluşturmaz.
- Mevzuata veya Türkiye profiline tam uyumluluk, haricî birlikte çalışabilirlik ve hukuk/
  bilgi güvenliği kabulü olmadan iddia edilmez.

## 3. Mimari ve geriye uyumluluk kuralları

- Java sürümü 21'dir. Kök Maven reactor dokuz modül içerir.
- Format modülleri dış anahtar desenini korur: `prepare` → kart/HSM imzası → `complete`.
- Yeni algoritma veya format eklenirken server-side ve client-side aynı format hazırlama
  kodunu kullanmalıdır.
- `multiSignatureType` verilmezse davranış `SINGLE` kalmalıdır.
- CAdES PARALLEL üst seviye `SignerInfo`, SERIAL CMS `counterSignature` üretir.
- XAdES PARALLEL yalnız DETACHED/ENVELOPING; SERIAL `xades:CounterSignature` üretir.
- PAdES yalnız SERIAL çoklu imzayı incremental revision ile destekler ve hedef indeks 0'dır.
- CAdES dosya uzantısı `.p7s` olarak kullanılır.
- Mevcut Flyway migration dosyaları değiştirilmez; her şema değişikliği yeni `VNN__*.sql`
  dosyasıdır. Güncel şema sürümü V12'dir.
- API hata sözleşmesi Problem Details ve kararlı `code` alanını korur.
- Tenant izolasyonu, OAuth scope ve idempotency kontrolleri atlanmaz.

## 4. Değişiklik rotası

| Değişiklik | Önce okunacak yer | En az güncellenecekler |
|---|---|---|
| CAdES/XAdES/PAdES | İlgili faz, `AI_HANDOFF.md` | Format servisi, doğrulayıcı, API orkestrasyonu, OpenAPI, demo, test |
| Kart/PKCS#11 | Faz 4, iki modlu mimari | Agent, kart profili, fiziksel kabul testi, kullanıcı rehberi |
| HSM/server-side | İki modlu mimari, yönetim rehberi | Server key profili, secret çözümleme, API testi |
| Sertifika doğrulama | Faz 6, güven deposu | Validator, politika, trust snapshot, rapor ve negatif test |
| TSA/uzun dönem | Faz 5/7 | Timestamp client, format servisi, TSA profili, doğrulama testi |
| Standart/mevzuat | `STANDART_DEGISIKLIGI_ETKI_HARITASI.md` | Yeni politika sürümü, kod/test, faz ve kaynak kesim tarihi |
| DB şeması | Son migration ve entity | Yeni migration, entity/repository, context testi, OpenAPI |
| UI/demo | İlgili static sayfa | Controller route, JavaScript syntax kontrolü, kullanıcı rehberi |

## 5. Zorunlu doğrulama

Ana teslim komutu:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" clean verify
```

Değişiklik küçükse önce hedef modül çalıştırılabilir; teslimden önce etkilenen reactor en az
bir kez test edilmelidir. PowerShell'de nokta içeren `-D...` argümanlarını tırnak içine alın.

Paketleme:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" `
  -pl signature-api,smartcard-agent,desktop-signing-demo -am `
  "-DskipTests" package
```

Çalıştırılabilir dosyalarda `-exec.jar` kullanılır. Normal modül JAR'ında ana manifest
bulunması beklenmez.

## 6. Belge güncelleme sözleşmesi

Bir davranış değiştiğinde aynı değişiklik içinde:

1. `PROJECT_STATUS.md` yetenek/açık tablosu güncellenir.
2. İlgili `FAZ_*.md` dosyasına karar, sınır ve kabul testi eklenir.
3. API değiştiyse OpenAPI ve demo güncellenir.
4. Doğrudan JAR API'si değiştiyse `JAVA_KUTUPHANE_ENTEGRASYONU.md` güncellenir.
5. Standart etkisi varsa `STANDART_DEGISIKLIGI_ETKI_HARITASI.md` güncellenir.
6. Kullanıcının çalıştırma biçimi değiştiyse README ve ilgili rehber güncellenir.
7. `CHANGELOG.md` altındaki `Unreleased` bölümüne kısa kayıt eklenir.

Belgeye test edilmemiş sonucu “tamamlandı” diye yazmayın. Yazılım teslimi ile üretim/hukuk
kabulünü ayrı durumlar olarak gösterin.

## 7. Depoya alınmaması gerekenler

- `.m2/`, `target/`, `tmp/`, `.eimza/`
- Gerçek `.env`, PIN, token, DB/TSA/HSM secret değerleri
- Özel anahtar, gerçek PKCS#12/JKS/keystore veya üretim sertifika zinciri
- Makineye özel `agent-local.yml`; bunun yerine `agent-local.example.yml`
- Gerçek kişisel veri veya imzalanmış üretim belgeleri

Test CA sertifikası yalnız `signature-api/src/test/resources/certificates` altında ve açıkça
test amacıyla tutulabilir; özel anahtar depoya eklenemez.
