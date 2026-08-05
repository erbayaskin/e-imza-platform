# Proje Yönetişimi

## Sahiplik

- Proje sahibi ve baş bakımcısı: [Erbay AŞKIN](https://github.com/erbayaskin)
- GitHub sahibi: [`@erbayaskin`](https://github.com/erbayaskin)
- Repository: [`erbayaskin/e-imza-platform`](https://github.com/erbayaskin/e-imza-platform)
- Lisans: Apache License 2.0
- Maven groupId: `io.github.erbayaskin`
- Java paket kökü: `io.github.erbayaskin.eimza`

Baş bakımcı; yol haritası, güvenlik sürümleri, bakımcı atama/çıkarma, geriye uyumsuz değişiklik
ve release kararlarında son sorumludur. Teknik kararlar mümkün olduğunca issue veya pull
request üzerinde gerekçesiyle kaydedilir.

## Katkı modeli

- Dış katkılar pull request ile kabul edilir.
- Katılımcılar [DCO 1.1](DCO) beyanını her committe `Signed-off-by` satırıyla verir.
- Ayrı bir Contributor License Agreement (CLA) istenmez.
- Katılımcılar [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) kurallarına uyar.
- Güvenlik açığı düzeltmeleri public PR yerine [SECURITY.md](SECURITY.md) sürecinde ele alınır.

Bir katkının gönderilmiş olması birleştirme garantisi vermez. Bakımcı; kapsam, güvenlik,
standart uyumu, test kanıtı, bakım maliyeti ve geriye uyumluluğa göre karar verir.

## Değişiklik kararları

Küçük hata düzeltmeleri normal pull request incelemesiyle ilerler. Aşağıdakiler için önce
tasarım issue'su ve açık bakımcı onayı gerekir:

- Public Java veya REST sözleşmesinde geriye uyumsuz değişiklik
- İmza formatı, kriptografik algoritma veya doğrulama politikasında davranış değişikliği
- Veritabanı şeması ve tenant izolasyonu değişikliği
- Yeni PKCS#11/HSM/PIN veya secret işleme yolu
- Lisans, yönetişim, güven modeli veya destek kapsamı değişikliği
- Yeni dış ağ erişimi ya da güven kaynağı

Karar kaydı ilgili faz belgesine ve güncel durum için `PROJECT_STATUS.md` dosyasına işlenir.

## `main` dalı politikası

GitHub üzerinde aşağıdaki kurallar etkinleştirilir:

- Doğrudan push yerine pull request zorunluluğu
- En az bir onaylı review ve code owner review
- Yeni commit gelince eski onayın düşürülmesi
- CI `build`, CI `dependency-review`, CI `dco` ve CodeQL `analyze` kontrolleri
- Açık review konuşmalarının çözülmesi
- Force-push ve branch silmenin kapatılması
- Yöneticilerin de kuralları atlayamaması
- Squash merge; merge commit ve rebase merge seçeneklerinin kapatılması

İlk GitHub çalışması gerçek check adlarını üretmeden required status check seçimi yapılmaz.
Ayarların uygulanması [GITHUB_PUBLICATION_CHECKLIST.md](GITHUB_PUBLICATION_CHECKLIST.md)
üzerinden kaydedilir.

## Sürüm ve yayın politikası

- Sürümler [Semantic Versioning 2.0.0](https://semver.org/) izler.
- `0.x` dönemi public sözleşmenin olgunlaştırıldığı geliştirme dönemidir.
- Release yalnız temiz `main`, başarılı CI/CodeQL, güncel changelog ve SBOM ile hazırlanır.
- Release tag'i bakımcı tarafından imzalı annotated tag olarak oluşturulur ve doğrulanır.
- JAR/MSI/container gibi binary çıktılar kaynak deposuna commit edilmez; GitHub Release veya
  güvenilir artifact repository üzerinden checksum ve SBOM ile yayımlanır.
- Güvenlik release'leri gerekirse normal takvim dışında önceliklendirilir.

## Yapay zekâ ile üretilen katkılar

Yapay zekâ kullanımı katkı sahibinin sorumluluğunu kaldırmaz. Katkıyı gönderen kişi lisans,
kaynak, test, güvenlik ve doğruluk kontrollerini yapar; commit için DCO beyanını kendi adına
verir. Yapay zekâya PIN, özel anahtar, gerçek kişisel veri veya üretim secret'ı verilmez.
