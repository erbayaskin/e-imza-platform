# Git/GitHub Depo Hazırlık Durumu

> Denetim tarihi: 5 Ağustos 2026  
> Hedef: `https://github.com/erbayaskin/e-imza-platform`  
> Sonuç: Public kaynak yayını için lisans ve yönetişim dosyaları hazır; GitHub repository'sinin
> oluşturulması, güvenlik/branch ayarları ve ilk CI sonucu bekleniyor.

## 1. Onaylanan kararlar

| Konu | Karar |
|---|---|
| Lisans | Apache License 2.0 |
| Telif sahibi | Copyright 2026 Erbay AŞKIN |
| GitHub sahibi | `@erbayaskin` |
| Repository adı | `e-imza-platform` |
| Maven groupId | `io.github.erbayaskin` |
| Java paket kökü | `io.github.erbayaskin.eimza` |
| Dış katkı | Pull request ile kabul |
| Katkı beyanı | DCO 1.1, her committe `Signed-off-by` |
| CLA | Yok |
| Davranış kuralları | Contributor Covenant 2.1 |
| Davranış özel iletişimi | `erbayaskin@gmail.com` |
| Güvenlik bildirimi | GitHub Private Vulnerability Reporting |
| İlk güvenlik yanıt hedefi | Kritik: 1 iş günü; diğer: 3 iş günü |
| Varsayılan dal/merge | Korumalı `main`, yalnız squash merge |
| Review | En az 1 onay ve code owner review |
| Release | Semantic Versioning, imzalı annotated tag |

Bu kararların kaynak içindeki karşılıkları `LICENSE`, `NOTICE`, `DCO`, `GOVERNANCE.md`,
`CODE_OF_CONDUCT.md`, `SECURITY.md`, `CONTRIBUTING.md`, `SUPPORT.md` ve `.github` altındaki
şablonlardır.

## 2. Teknik denetim sonucu

- `D:\ErbayProject` henüz Git çalışma ağacı değildir; `.git` dizini ve `origin` yoktur.
- `.gitignore` build çıktıları, yerel Maven deposu, runtime anahtarları, geçici dosyalar ve
  makineye özel `agent-local.yml` dosyasını hariç tutar.
- `.gitattributes` satır sonlarını ve binary türlerini tanımlar.
- Kamu kurumu izlenimi oluşturabilecek eski namespace kaldırılmış; Maven koordinatları
  `io.github.erbayaskin`, Java paketleri `io.github.erbayaskin.eimza` yapılmıştır.
- Sohbette paylaşılmış test PIN'i kaynak, yapılandırma ve belgelerde tutulmamaktadır.
- Özel anahtar PEM işareti veya literal bearer token bulunmamıştır.
- Örnek deployment secret dosyaları yalnız açık placeholder değerler içerir.
- Test CA dosyası public sertifikadır; özel anahtar değildir.
- 5 Ağustos 2026 tarihinde kök reaktörde `mvn verify` başarılıdır: dokuz modülde 85 test,
  sıfır hata/başarısızlık.
- CI, CodeQL, dependency review, DCO kontrolü, SBOM ve Dependabot yapılandırmaları kaynakta
  hazırdır; gerçek GitHub repository'sinde henüz çalışmamıştır.
- Aggregate SBOM'daki 121 bileşenin tamamında lisans metadata'sı vardır; alternatif/copy-left
  lisans bildiren sekiz bileşenin teknik değerlendirmesi `THIRD_PARTY_LICENSES.md` içindedir.

## 3. Public yayın öncesi kalan kapılar

### 3.1 Yerel Git ve GitHub

- Repository public olarak oluşturulmalı ve ilk DCO sign-off'lu commit push edilmelidir.
- İlk CI, CodeQL, DCO ve dependency review sonucu görülmelidir.
- `main` ruleset gerçek check adlarıyla etkinleştirilmelidir.
- Private Vulnerability Reporting, security notifications, Dependabot alerts ve mümkünse
  secret scanning/push protection etkinleştirilmelidir.
- Issues, Discussions, yalnız squash merge ve merge sonrası branch silme ayarlanmalıdır.

Adım adım ve kanıt alanları için
[GITHUB_PUBLICATION_CHECKLIST.md](GITHUB_PUBLICATION_CHECKLIST.md) kullanılır.

### 3.2 Hukuki ve içerik kontrolü

Apache-2.0 lisans metni ve telif bildirimi sahibi tarafından onaylanmıştır. Public butonuna
basılmadan önce yine de şu somut kontroller yapılır:

- Projenin tamamı üzerinde lisans verme yetkisinin Erbay AŞKIN'a ait olduğu doğrulanır.
- Daha önce başka kişi/kurum adına üretilmiş kod varsa gerekli izin veya attribution eklenir.
- Üçüncü taraf bağımlılık ve varlık lisansları SBOM ile birlikte incelenir.
- Marka, kurum adı, gerçek kişisel veri, üretim endpoint'i ve hukuken doğrulanmamış uyumluluk
  iddiaları için son içerik kontrolü yapılır.

Bu belge teknik yönetişim kaydıdır; bağımsız hukuk görüşünün yerine geçmez.

## 4. Önerilen ilk Git adımları

Bu komutlar henüz çalıştırılmamıştır:

```powershell
cd D:\ErbayProject
git init
git branch -M main
git add .
git status --short
git commit -s -m "Initial public release"
git remote add origin https://github.com/erbayaskin/e-imza-platform.git
git push -u origin main
```

`git add .` sonrasında `.m2/`, `.eimza/`, `tmp/`, bütün `target/` dizinleri,
`agent-local.yml`, gerçek `.env`, keystore ve cihaz secret dosyaları görünmemelidir.


## 5. Binary ve release politikası

`output/pdf` altındaki küçük PDF/DOCX entegrasyon kılavuzu kaynakla birlikte tutulabilir.
Çalıştırılabilir JAR, MSI, container image, test sonucu ve büyük fixture dosyaları Git'e commit
edilmez. Bunlar GitHub Release veya güvenilir artifact repository üzerinden checksum, SBOM,
`LICENSE` ve `NOTICE` ile yayımlanır.

Kararlı ilk release için ayrıca:

- `0.1.0-SNAPSHOT` release sürümüne çevrilir.
- `CHANGELOG.md` sürüm ve tarihle kapatılır.
- Kaynak/Javadoc JAR ve artifact repository hedefi belirlenir.
- Tag imzalanır ve `git tag -v` ile doğrulanır.

## 6. Hazır olma beyanı

Kaynak ağacında public lisans ve yönetişim kararları tamamlanmıştır. Proje, repository
oluşturulup [GITHUB_PUBLICATION_CHECKLIST.md](GITHUB_PUBLICATION_CHECKLIST.md) içindeki GitHub
ayarları ve ilk workflow sonuçları doğrulanmadan **public yayını tamamlanmış** sayılmaz.

Public kaynak yayın hazırlığı, Faz 12'deki fiziksel kart/HSM/canlı TSA-ESHS/yük/sızma/DR ve
hukuki ürün kabulünün tamamlandığı anlamına gelmez.
