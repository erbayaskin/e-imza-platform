# Güvenlik Politikası

## Desteklenen sürümler

Henüz kararlı bir release yayımlanmamıştır. İlk release sonrasında en güncel minor sürümün son
patch'i güvenlik güncellemesi alır. `main` dalı gelecek sürüm için en iyi gayretle korunur;
release yerine kullanılmamalıdır. Eski sürümlerin desteği GitHub Releases notlarında ayrıca
ilan edilir.

## Güvenlik açığı bildirme

Güvenlik açığını public issue, discussion, pull request, e-posta zinciri veya log ekiyle
paylaşmayın. Şu özel kanalı kullanın:

1. Repository içindeki **Security** sekmesini açın.
2. **Advisories** bölümüne girin.
3. **Report a vulnerability** ile özel rapor oluşturun:
   [Private vulnerability report](https://github.com/erbayaskin/e-imza-platform/security/advisories/new)

Bu özellik repository yöneticisi tarafından public yayından hemen sonra etkinleştirilmelidir.
Buton görünmüyorsa ayrıntıyı public alana yazmayın; yalnızca
[`@erbayaskin`](https://github.com/erbayaskin) ile güvenli bir özel kanal kurulmasını isteyin.

## Yanıt hedefleri

- Kritik etki şüphesinde ilk alındı bildirimi: 1 iş günü
- Diğer güvenlik raporlarında ilk alındı bildirimi: 3 iş günü
- İlk sınıflandırma ve sonraki adım hedefi: 7 takvim günü
- Düzeltme/yayın tarihi: etki, karmaşıklık ve koordineli açıklama planına göre raporlayana
  özel kanalda bildirilir

Bu süreler hizmet seviyesi garantisi değildir; bakımcının koordinasyon hedefleridir.

## Raporda bulunması gerekenler

- Etkilenen sürüm, modül ve uç
- Tekrar üretme adımları
- Beklenen güvenlik etkisi ve saldırı önkoşulları
- Mümkünse zararsız, sentetik veriyle hazırlanmış kanıt
- Biliniyorsa geçici azaltım ve önerilen düzeltme

Gerçek kişisel veri, PIN, özel anahtar, erişim tokenı, üretim sertifikası, HSM kimlik bilgisi
veya kurum içi endpoint eklenmemelidir.

## Kapsam ve güvenli araştırma

Özellikle imza doğrulamasını atlatma, sertifika yolu/güven deposu hatası, tenant izolasyonu,
SSRF, PIN/secret sızıntısı, PKCS#11 kötüye kullanımı, replay, yetki yükseltme ve kriptografik
bütünlük sorunları güvenlik kapsamındadır.

Aktif test yalnız araştırmacının sahibi olduğu ya da açıkça test izni aldığı sistem ve veride
yapılmalıdır. Hizmet kesintisi, sosyal mühendislik, fiziksel saldırı, üçüncü taraf TSA/ESHS/HSM
servislerine izinsiz trafik ve gerçek kullanıcı verisine erişim denemesi yapılmamalıdır.

## Koordineli açıklama

Bakımcı ve raporlayan; etki doğrulaması, düzeltme, test, CVE/GHSA gereksinimi ve yayın tarihi
üzerinde özel kanalda koordinasyon kurar. Düzeltme sonrası aynı senaryo ve benzer kod noktaları
yeniden test edilir. Kritik/yüksek doğrulanmış bulgular release ve üretim kabulünü engeller.

Davranış veya topluluk ihlalleri güvenlik açığı değildir; bunlar
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) içindeki özel iletişim kanalına bildirilmelidir.
