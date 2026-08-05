# Windows akıllı kart ajanı dağıtım ve yükseltme

## Paket üretimi

Üretim paketi Java 21 `jpackage`, WiX Toolset, Windows SDK `signtool`, kurum EV/kurumsal
kod imzalama sertifikası ve RFC 3161 kod imza zaman damgası gerektirir.

```powershell
./packaging/windows/build-agent-msi.ps1 `
  -ArtifactVersion 0.1.0-SNAPSHOT `
  -PackageVersion 0.1.0 `
  -SigningThumbprint SERTIFIKA_SHA1_PARMAK_IZI
```

Betik var olan sürüm dizininin üzerine yazmaz; MSI'yı imzalayıp doğrular ve SHA-256 içeren
`release-manifest.json` üretir. Sertifika özel anahtarı kaynak kodda, CI değişkeninde veya
dosyada tutulmaz; HSM/kurumsal imzalama hizmeti tercih edilir.

## Kurumsal dağıtım

1. MSI, manifest, SBOM, test sonucu ve sürüm notunu değişmez release alanına alın.
2. Intune/SCCM/GPO ürün kodu ve sabit upgrade UUID ile paketi tanımlayın.
3. İlk halka: yalnız test ekibi ve yazılım PKCS#11 makineleri.
4. İkinci halka: kullanıcıların %5'i, en az iki iş günü gözlem.
5. Üçüncü halka: %25, en az iki iş günü gözlem.
6. Son halka: kalan kullanıcılar.
7. Her halkada kurulum başarısı, ajan başlangıcı, kart keşfi, imza başarı oranı ve çökme
   sayısını izleyin.

`C:\ProgramData\EImza\agent\application.yml` yönetici tarafından yazılır. Kullanıcı bu
dosyada Origin, manifest anahtarı, ATR veya PKCS#11 yolunu değiştirememelidir. PIN ve
özel anahtar hiçbir kurulum/telemetri kaydına girmez.

## Geri alma

Önceki imzalı MSI ve manifest en az iki sürüm saklanır. Kritik sorun halinde dağıtım
durdurulur, etkilenen halka önceki MSI'ya geri alınır ve merkez API ilgili minimum/maksimum
ajan sürümüyle uyumluluğu korur. Şema/protokol kırıcı değişiklik iki sürümlük geçiş dönemi
olmadan yayınlanmaz.

## Fiziksel kart bağı

Kart temin edilene kadar paket “üretim kabulü geçmiş” sayılmaz. Her ajan/sürücü güncellemesi
`FAZ_8_FIZIKSEL_KART_KABUL_PROSEDURU.md` matrisindeki en az bir gerçek kart koşusunu
yeniden gerektirir.
