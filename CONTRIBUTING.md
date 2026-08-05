# Katkı Rehberi

Bu proje elektronik imza, özel anahtar, kişisel veri ve güven kararları işlediği için küçük
değişikliklerde bile izlenebilirlik bekler. Katkıdan önce `AGENTS.md`, `AI_HANDOFF.md` ve
`PROJECT_STATUS.md` okunmalıdır. Katılım [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) kurallarına
tabidir.
## Lisans ve DCO

Katkılar Apache License 2.0 altında projeye sunulur. Her commit
[Developer Certificate of Origin 1.1](DCO) için katkı sahibinin gerçek kimliğiyle
`Signed-off-by` satırı taşımalıdır:

```shell
git commit -s -m "Değişikliği kısa açıklayın"
```

Mevcut son commit'e sign-off eklemek için `git commit --amend --signoff` kullanılabilir.
Başkasının adına sign-off eklemeyin. Ayrı bir CLA istenmez; DCO kontrolü pull request CI'ında
zorunludur.

## Geliştirme akışı

1. Değişikliğin bağlı olduğu fazı ve standart etkisini belirleyin.
2. Davranışı önce test veya açık kabul senaryosuyla tanımlayın.
3. Değişikliği en dar modülde uygulayın; mimari sınırları aşmayın.
4. API/DB/UI/doğrudan-JAR tüketicilerinin etkisini birlikte değerlendirin.
5. İlgili otomatik testleri ve ardından `clean verify` çalıştırın.
6. İlgili Markdown/OpenAPI belgelerini aynı PR içinde güncelleyin.

## Derleme ve test

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" clean verify
```

Hedef modül örneği:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" `
  -pl signature-xades -am test
```

PowerShell'de nokta içeren Maven `-D...` seçenekleri tırnak içinde verilmelidir.

## Pull request beklentileri

- Amaç ve kullanıcı etkisi
- Değişen public API/DB/format davranışı
- Güvenlik ve geriye uyumluluk değerlendirmesi
- Çalıştırılan test komutları ve sonuçları
- Fiziksel kart/HSM/TSA/ESHS gerektiren açık kabul maddeleri
- Güncellenen belgeler
- Bütün commitlerde geçerli DCO `Signed-off-by` satırı

Artifact, PIN, token, özel anahtar, üretim sertifikası veya gerçek kişisel veri PR'a
eklenmez.

## Kod ve şema kuralları

- Java 21 ve UTF-8 kullanılır.
- Mevcut Flyway migration değiştirilmez; yeni migration eklenir.
- Format kütüphaneleri dış anahtar `prepare/sign/complete` desenini korur.
- API hata kodları kararlı ve test edilebilir olmalıdır.
- `SINGLE` imza geriye uyumluluğu korunur.
- Client-side HSM veya tarayıcıdan PIN kabul eden değişiklik yapılmaz.
- Yeni ağ erişimi timeout, allowlist/SSRF, tazelik ve kanıt doğrulaması içermelidir.

## Belge kuralları

- Güncel durum `PROJECT_STATUS.md` dosyasına yazılır.
- Tarihsel faz kararı silinmez; yeni kararla superseded olduğu belirtilir.
- Standart değişikliği `STANDART_DEGISIKLIGI_ETKI_HARITASI.md` sürecini izler.
- Doğrudan Java public metodu değişirse `JAVA_KUTUPHANE_ENTEGRASYONU.md` güncellenir.
- REST sözleşmesi değişirse OpenAPI ve demo aynı PR'da güncellenir.
- Kullanıcı/operasyon komutu değişirse README ve ilgili runbook güncellenir.

## Commit kapsamı

Tek commit/PR mümkün olduğunca tek bir davranış değişikliğini taşımalıdır. Biçimlendirme,
geniş yeniden adlandırma ve işlevsel değişikliği gerekmedikçe aynı committe karıştırmayın.

## Güvenlik bildirimi

Bir güvenlik açığı katkı PR'ı veya public issue içinde paylaşılmaz. `SECURITY.md` içindeki
özel bildirim süreci kullanılır.
