# Offline e-imza masaüstü uygulaması

Bu modül, diğer demo uygulamalarından ayrı çalışan örnek bir Swing masaüstü
uygulamasıdır. Merkez API'ye bağlanmaz, HTTP sunucusu veya Smart Card Agent süreci
başlatmaz. Kart erişimi için `smartcard-agent` JAR'ındaki düşük seviyeli
bileşenleri, CAdES üretme ve doğrulama için `signature-cades` JAR'ını doğrudan
kullanır.

## Derleme ve çalıştırma

Proje kökünde:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" -pl desktop-signing-demo -am package
java -jar .\desktop-signing-demo\target\desktop-signing-demo-0.1.0-SNAPSHOT-exec.jar
```

İstenirse profil veri dizini belirtilebilir:

```powershell
java -jar .\desktop-signing-demo\target\desktop-signing-demo-0.1.0-SNAPSHOT-exec.jar `
  --data-dir=D:\eimza-offline-data
```

Varsayılan profil dosyası, uygulamanın çalıştırıldığı dizindeki
`.eimza-desktop\card-profiles.properties` dosyasıdır. Bu dosyada PIN tutulmaz.

## İlk AKİS kart tanımı

1. **Kart profilleri** sekmesine geçin.
2. **Takılı karttan al** ile ATR'yi okuyun veya
   `3B9F978131FE4580655443D3228231C073F621808105D3` değerini girin.
3. Kart adı olarak `AKİS` girin.
4. **Dosya seç** ile kurulu PKCS#11 kütüphanesini seçin. Windows kurulumunda
   tipik ad `akisp11.dll` olmakla birlikte gerçek kurulum yolunu seçin.
5. **Yeni profil ekle** düğmesine basın. Kart slotu ATR üzerinden otomatik bulunur.

Her farklı kart türü için kart adı, tam ATR ve üreticinin PKCS#11 kütüphane
dosyasıyla ayrı profil eklenebilir. Public sertifikanın PIN'siz okunabilmesi,
kartın/middleware'in public sertifika nesnelerine oturumsuz erişim vermesine
bağlıdır. AKİS CIF bulunursa AKİS için özel PIN'siz okuma yolu da otomatik
kullanılır.

## İmzalama

1. **İmzala** sekmesinde kartları tarayın.
2. Public imza sertifikasını, attached/detached biçimini ve algoritmayı seçin.
3. Belgeyi ve `.p7s` çıktı yolunu seçin.
4. İmzala düğmesine basın; PIN yalnızca yerel pencerede alınır ve saklanmaz.

Süresi dolmuş kartla yalnızca fiziksel entegrasyon testi yapılacaksa sertifika
tarih kontrolü geçici olarak kapatılabilir. Böyle bir çıktı hukuken geçerli veya
nitelikli elektronik imza kabul edilmemelidir.

## İmza doğrulama

1. **İmzayı doğrula** sekmesinde paketleme türünü seçin.
2. `.p7s`/`.p7m` imza dosyasını seçin.
3. Detached imzada orijinal belgeyi de seçin.
4. Doğrula düğmesine basın.

Doğrulama çevrimdışı olarak CAdES biçimini, gömülü imzalayan sertifikasını,
kriptografik imzayı, imzalama-sertifikası özetini, sertifika tarihini ve mevcutsa
gömülü zaman damgasını kontrol eder. Sonuç ekranında ayrıca imzalayanın sertifika
konusu, ortak adı, kimlik/seri alanı, kurum/birim bilgileri, sertifikayı veren,
sertifika seri numarası, geçerlilik tarihleri, anahtar algoritması ve SHA-256
parmak izi gösterilir. Bu bağımsız örnek şu aşamada merkez
uygulamadaki DB güven deposu, OCSP/CRL ve politika yönetimini kullanmaz; dolayısıyla
sonuç bir çevrimdışı kriptografik/biçimsel kontroldür, tam nitelikli imza güven
kararı değildir.

İmzalayan sertifikası yapısal olarak okunabildiğinde **İmzacı sertifikasını
kaydet (.cer)** düğmesi etkinleşir. Kaydedilen dosya DER kodlu public X.509
sertifikasıdır; özel anahtar veya PIN içermez.
