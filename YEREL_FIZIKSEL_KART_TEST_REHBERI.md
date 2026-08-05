# Yerel Fiziksel Akıllı Kart İmza ve Doğrulama Test Rehberi

Tarih: 29.07.2026

Bu rehber AKİS kart, `C:/Windows/System32/akisp11.dll` sürücüsü ve
`agent-local.yml` profiliyle yerel uçtan uca test içindir.

## 1. Test kapsamı

İlk testte `B_B` seviyesi kullanılmalıdır. Bu seviye TSA gerektirmez.
Başarılı imza tamamlama işlemi şu kontrollerin geçtiğini kanıtlar:

1. ATR ile AKİS profili bulunmuştur.
2. PKCS#11 slotu otomatik seçilmiştir.
3. Kart sertifikası ve özel anahtarı bulunmuştur.
4. PIN ile kart üzerinde imza üretilmiştir.
5. Merkez API ham kart imzasını sertifikanın açık anahtarıyla doğrulamıştır.
6. Seçilen CAdES, XAdES veya PAdES çıktısı oluşturulmuştur.

Bağımsız imza doğrulama endpoint'i şu anda yalnız zaman damgalı ayrık CAdES
`B_T`, `B_LT` ve `B_LTA` imzalarını doğrular. `B_B` CAdES ile XAdES/PAdES
için bağımsız doğrulama API'si henüz yoktur.

## 2. Uygulamaları paketleme

Çalışan eski ajan varsa terminalinde `Ctrl+C` ile durdurulur. Proje kökünde:

```powershell
cd D:\ErbayProject

mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" `
  -pl signature-api,smartcard-agent -am package -DskipTests
```

## 3. Merkez API'yi başlatma

Birinci PowerShell penceresinde:

```powershell
cd D:\ErbayProject

java -jar .\signature-api\target\signature-api-0.1.0-SNAPSHOT-exec.jar `
  --spring.profiles.active=local
```

Kontrol:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health |
  ConvertTo-Json -Depth 5
```

`status` alanı `UP` olmalıdır.

## 4. Akıllı kart ajanını başlatma

İkinci PowerShell penceresinde, API başladıktan sonra:

```powershell
cd D:\ErbayProject

$env:EIMZA_AGENT_DEVICE_ID = "a8a0dc09-54ab-40b7-b404-bebd55ff1756"
$env:EIMZA_AGENT_MANIFEST_PUBLIC_KEY = (
  Invoke-RestMethod http://localhost:8080/api/v1/signing-configuration/manifest-key
).publicKey
$env:EIMZA_AGENT_ALLOWED_ORIGIN = "http://localhost:8080"
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "file:D:/ErbayProject/agent-local.yml"

java -jar .\smartcard-agent\target\smartcard-agent-0.1.0-SNAPSHOT-exec.jar
```

Local/test API manifest Ed25519 anahtarını ilk çalıştırmada
`.eimza/manifest-ed25519.pk8` ve `.spki` dosyalarında üretir ve sonraki
restartlarda aynı anahtarı kullanır. Bu özelliğin eklendiği sürüme ilk geçişte
agent public key değeri yukarıdaki komutla bir kez yenilenmelidir. Sonraki API
restartları agent manifest doğrulamasını bozmaz.

Kontrol:

```powershell
Invoke-RestMethod http://127.0.0.1:18443/actuator/health |
  ConvertTo-Json -Depth 5

Invoke-RestMethod http://127.0.0.1:18443/agent/v1/cards |
  ConvertTo-Json -Depth 6
```

AKİS kart kaydının `status` alanı `READY` olmalıdır. Ajan logunda otomatik
bulunan gerçek `slotListIndex` değeri görülür.

Slot keşfi, PIN istemeyen kartlarda anonim oturumla; sertifika erişimi için
oturum isteyen AKİS kartlarda ise kullanıcının yerel pencerede verdiği tek
kullanımlık PIN ile yapılır. PIN bellekte kullanım sonrasında sıfırlanır ve
loglanmaz. `agent-local.yml` içindeki `slot-list-index` yalnız keşif tamamen
başarısız olursa geriye dönük uyumluluk için kullanılan fallback değeridir.

## 5. Kart sertifikasını okuma

Bu çağrı sertifikaları önce public PKCS#11 oturumunda PIN'siz okumayı dener.
Kart üreticisi sertifika listesini oturum açmadan sunmuyorsa agent güvenli
fallback olarak yerel PIN penceresini açar:

```powershell
$cards = @(
  Invoke-RestMethod http://127.0.0.1:18443/agent/v1/cards
)
$card = $cards | Where-Object status -eq "READY" | Select-Object -First 1

$certificates = @(
  Invoke-RestMethod -Method Post `
    -Uri "http://127.0.0.1:18443/agent/v1/cards/$($card.readerId)/certificates/refresh" `
    -Headers @{"X-EImza-Agent" = "1"}
)
$leaf = $certificates | Where-Object hasPrivateKey | Select-Object -First 1
$leaf | ConvertTo-Json -Depth 5
```

`$leaf` boş olmamalı ve `hasPrivateKey` değeri `true` olmalıdır.

Sertifikaların listelenmesi, sertifikanın süresi dolmuş olsa da kart ve sürücü
erişimini sınamak için mümkündür. Normalde manifest/imzalanacak veri
hazırlığında güncel zaman için X.509 geçerlilik kontrolü zorunludur.

Yalnız local/test fiziksel kart kabul testinde `/admin/` sayfasındaki
`Özel politika seçimi` açılıp `İmzalama sertifikası tarih kontrolü` radio
seçeneği `Pasif` yapılabilir. Bu durumda kartın özel anahtarıyla kriptografik
imza üretimi sınanır; ortaya çıkan imza geçerli veya nitelikli e-imza olarak
kabul edilmez. Production profilinde kontrol zorunlu olarak aktiftir.

Doğrulamada tarih politikası pasifse CMS/XML/PDF imzası sertifikanın açık
anahtarıyla kriptografik olarak doğrulanır; sonuçta
`SIGNING_CERTIFICATE_VALIDITY_PASSIVE` kontrolü ve `INDETERMINATE` ana sonucu
gösterilir. Böylece bozuk imza ile süresi dolmuş sertifika birbirine
karıştırılmaz.

## 6. Güvenilir kök ve alt kökleri DB'ye ekleme

İmza tamamlanmadan önce kart sertifikasının zincirindeki CA sertifikaları
güven deposunda bulunmalıdır. Kök ve alt kök sertifikaları e-imza hizmet
sağlayıcısından DER `.cer` veya PEM biçiminde temin edilir. Son kullanıcı
sertifikası güven deposuna eklenmez.

Örnek dosya yolları ortama göre değiştirilir:

```powershell
function Get-CertificateBase64([string]$Path) {
  [Convert]::ToBase64String(
    [IO.File]::ReadAllBytes((Resolve-Path $Path))
  )
}

$rootBase64 = Get-CertificateBase64 "D:\sertifikalar\kok.cer"
$intermediateBase64 = Get-CertificateBase64 "D:\sertifikalar\alt-kok.cer"

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/trusted-certificates" `
  -ContentType "application/json" `
  -Body (@{
    certificate = $rootBase64
    trustType = "ROOT"
    displayName = "E-imza güvenilir kök"
    enabled = $true
  } | ConvertTo-Json)

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/trusted-certificates" `
  -ContentType "application/json" `
  -Body (@{
    certificate = $intermediateBase64
    trustType = "INTERMEDIATE"
    displayName = "E-imza güvenilir alt kök"
    enabled = $true
  } | ConvertTo-Json)
```

Depoyu kontrol etme:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/admin/trusted-certificates |
  ConvertTo-Json -Depth 8
```

## 7. Kart sertifikasını bağımsız doğrulama

```powershell
$certificateReport = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/validations/certificates" `
  -ContentType "application/json" `
  -Body (@{
    certificate = $leaf.certificateBase64
    intermediateCertificates = @()
  } | ConvertTo-Json)

$certificateReport | ConvertTo-Json -Depth 10
```

İmzalama için `mainIndication` değeri `VALID` olmalıdır. `INDETERMINATE`
veya `INVALID` ise `checks` alanındaki hata çözülmeden imza tamamlama işlemi
başarılı olmaz. Yerel makinenin sertifikadaki CRL/OCSP adreslerine ağ erişimi
olmalıdır.

## 8. Demo ekranıyla B-B imza oluşturma

Tarayıcıda aşağıdaki adres açılır:

```text
http://localhost:8080/demo/index.html
```

Alanlar:

- Tenant UUID: `11111111-1111-1111-1111-111111111111`
- İmzalama modu: `CLIENT_SIDE`
- Cihaz UUID: `a8a0dc09-54ab-40b7-b404-bebd55ff1756`
- Dosya: CAdES/XAdES için küçük bir metin dosyası; PAdES için gerçek PDF
- Format: İlk testte `CADES`, sonra `XADES` ve `PADES`
- Seviye: TSA yapılandırılana kadar `B_B`

Sıra:

1. İlk ajan başlangıcında `Yerel ajan cihaz anahtarını kaydet` seçilir.
   Ajanın UUID ve Ed25519 açık anahtarı tenant'a kaydedilir; özel anahtar
   ajandan çıkmaz.
2. `Kartı tara ve public sertifikaları yükle` seçilir. Sertifikalar PIN
   istenmeden listelenir.
3. Demo ekranındaki `İmza sertifikası` listesinden özel anahtarı bulunan
   sertifika seçilir.
4. `Seçili sertifika ile imzala` seçilir ve yerel ajan penceresinde PIN bir
   kez girilir.
5. Log alanında `artifactId` ve `sha256` görülür.
6. Tarayıcı CAdES için `.p7s`, XAdES için `.xades.xml`, PAdES için
   `.signed.pdf` dosyasını indirir.

`complete` başarılıysa kartın ürettiği ham imza merkez API tarafından
kriptografik olarak doğrulanmıştır.

PIN alanı bilinçli olarak HTML sayfasında bulunmaz. PIN yalnız
`127.0.0.1:18443` üzerinde çalışan yerel ajanın masaüstü penceresine girilir;
JavaScript'e, tarayıcıya ve merkez API'ye aktarılmaz.

İmza doğrulama, imzalama formundan ayrı
`http://localhost:8080/validation/` sayfasındadır.

## 9. Bağımsız CAdES imza doğrulama

Bu endpoint CAdES `B_T` veya daha üst seviye ister. Bunun için önce güvenilir
bir TSA endpoint'i ve TSA sertifika zinciri yapılandırılmalı, ardından demo
ekranında `CADES` ve `B_T` seçilmelidir.

Özgün belge ile indirilen `.p7s` dosyasının yolları verilerek:

```powershell
$contentBase64 = [Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("D:\test\belge.txt")
)
$signatureBase64 = [Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("D:\test\belge.txt.p7s")
)

$signatureReport = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/validations/signatures" `
  -ContentType "application/json" `
  -Body (@{
    content = $contentBase64
    signature = $signatureBase64
  } | ConvertTo-Json)

$signatureReport | ConvertTo-Json -Depth 12
```

Beklenen temel alanlar:

```text
targetType       : SIGNATURE
mainIndication   : VALID
detectedFormat   : CAdES
detectedLevel    : B-T
```

İçeriğin tek baytı veya `.p7s` dosyası değiştirilip çağrı tekrarlandığında
sonuç `INVALID` olmalıdır. Bu negatif test içerik bağının gerçekten
doğrulandığını gösterir.

## 10. Mevcut doğrulama kapsamı

- CAdES attached/detached, XAdES detached/enveloped/enveloping ve PAdES
  imzaları aynı doğrulama API'sinde desteklenir.
- Kriptografik geçerlilik; sertifika tarihi, güven zinciri, iptal bilgisi,
  nitelikli sertifika ve imza politikası sonuçlarından ayrı raporlanır.
- CAdES B-LT/B-LTA kanıtları ve arşiv zaman damgası kontrolleri ayrıca
  raporlanır.
- Yerel fiziksel kart kabul testinde kapatılan politika kontrolleri sonucu
  hukuken geçerli imza anlamına gelmez; raporda pasif politika olarak görünür.
