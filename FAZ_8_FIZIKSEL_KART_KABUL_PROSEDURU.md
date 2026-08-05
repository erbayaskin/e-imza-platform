# Faz 8 — Fiziksel kart, okuyucu ve sürücü kabul prosedürü

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

> Kart temin durumu: Bekleniyor  
> Amaç: Kart geldiğinde aynı adımları tekrarlayarak kanıtlanabilir uyumluluk sonucu üretmek.

## 1. Test öncesi kayıt

Her kombinasyon için aşağıdaki alanlar doldurulur:

| Koşu | Kart/ESHS/model | ATR | Okuyucu/firmware | İşletim sistemi | PKCS#11 kitaplığı ve sürüm | Ajan sürümü | Sonuç |
|---|---|---|---|---|---|---|---|
| HW-001 | Bekliyor | Bekliyor | Bekliyor | Windows / sürüm | Bekliyor | 0.1.0 | Bekliyor |

Kart seri numarası, TCKN, PIN ve özel anahtar hiçbir rapora/loga yazılmaz. Sertifika
gerekiyorsa konu alanları maskelenir; yalnız SHA-256 parmak izi ve politika OID'leri tutulur.

## 2. Güvenli hazırlık

1. Üretici sürücüsünü resmî kaynaktan indirin; sürüm, URL, yayıncı imzası ve dosya SHA-256
   değerini kaydedin.
2. Test bilgisayarını ağ ve kullanıcı yetkileri bakımından kontrollü hale getirin.
3. Kartın kalan PIN deneme sayısını üretici aracından doğrulayın. Varsayılan/emin olunmayan
   PIN ile deneme yapmayın; art arda otomatik PIN testi yasaktır.
4. Ajanı kod imzalı paketle kurun ve `server.address=127.0.0.1` olduğunu doğrulayın.
5. PKCS#11 dosya yolunu HTTP isteğinde değil yerel yönetici kart profilinde tanımlayın.
6. Test belgesini oluşturun ve kullanıcıya gösterilen SHA-256 ile manifestteki özetin aynı
   olduğunu bağımsız araçla kontrol edin.

## 3. ATR ve kart profili kabulü

1. Kart yokken okuyucu listelenmeli, kart işlemi başlamamalıdır.
2. Kartı takın; PC/SC'den okunan ATR'yi büyük harfli hex biçiminde kaydedin.
3. Tam ATR veya açık maske kuralını karta ait profile ekleyin.
4. Aynı ATR'ye iki profil eşleşiyorsa ajan imzalamayı reddetmelidir.
5. Tanımsız ATR desteklenmiyor olarak görünmeli; keyfi PKCS#11 yolu seçilmemelidir.
6. Doğru profil; üretici, model ve beklenen PKCS#11 sağlayıcısını seçmelidir.

## 4. Sertifika kabulü

1. Token oturumu açılmadan herkese açık sertifika nesnelerini listeleyin.
2. Yalnız imzalama amaçlı, süresi geçerli ve özel anahtarı bulunan uygun sertifikayı gösterin.
3. Sertifika zincirini DB'deki test kök/alt köküyle doğrulayın.
4. QCStatements, key usage, sertifika politika OID'si, RSA/EC boyu ve geçerlilik zamanını
   Faz 6 raporunda ayrı ayrı kontrol edin.
5. Yanlış kök snapshot'ı ile sonucun `VALID` olmamasını doğrulayın.
6. Sertifika parmak izini kart/ESHS'nin resmî bilgisiyle ikinci kanaldan karşılaştırın.

## 5. İmzalama kabulü

1. Tek kullanımlık, kısa ömürlü imzalama oturumu oluşturun.
2. İmzalı manifesti ajana iletin; ajan imza ve süreyi doğrulamalıdır.
3. Belgeyi yerelde yeniden özetleyin; manifest özeti farklıysa PIN penceresi açılmadan reddedin.
4. PIN yalnız native pencerede girilmeli; HTTP, merkezi API, log ve crash dump'ta görünmemelidir.
5. Doğru PIN ile CAdES B-T üretin; özel anahtarın export edilemediğini doğrulayın.
6. Aynı manifesti ikinci kez kullanmayı deneyin; reddedilmelidir.
7. Manifest süresi dolduktan sonra denemeyi tekrarlayın; reddedilmelidir.
8. İmzalanan içeriği değiştirin; doğrulama `INVALID` vermelidir.
9. İmzayı B-LT/B-LTA'ya yükseltin ve gömülü kanıt/arşiv zaman damgasını doğrulayın.

## 6. Hata ve dayanıklılık kabulü

- Kart işlem ortasında çıkarılır: işlem açık hata koduyla sonlanır, oturum/PIN bellekte kalmaz.
- Okuyucu çıkarılır: ajan çökmez; yeniden takıldığında kontrollü keşif yapar.
- Yanlış PIN: yalnız bir manuel deneme yapılır; kalan hak belirsizse test durdurulur.
- Kilitli kart: otomatik tekrar denenmez.
- PKCS#11 kitaplığı eksik/yanlış mimari: profil devre dışı kalır, ajan genel olarak çalışır.
- TSA/OCSP kesintisi: `INVALID` ile ağ kaynaklı `INDETERMINATE` karıştırılmaz.
- Ajan yeniden başlatılır: önceki tek kullanımlık manifest tekrar kullanılamaz.

## 7. Çapraz doğrulama ve çıkış

Üretilen imza en az bir bağımsız CAdES doğrulayıcıyla ve mümkünse ilgili ESHS test aracıyla
karşılaştırılır. Aşağıdakilerin tümü geçmeden kombinasyon desteklenenler listesine alınmaz:

- ATR/profile tekil eşleşme;
- sertifika zinciri ve Türkiye profil kontrolleri;
- doğru içerik/imza doğrulaması;
- bozuk içerik, replay ve süre dolumu reddi;
- PIN/özel anahtar sızıntısı olmaması;
- B-T ve hedefleniyorsa B-LT/B-LTA birlikte çalışabilirliği;
- kart çıkarma/okuyucu kesintisinde kontrollü davranış.

Son rapora koşu kimliği, tarih, yazılım/sürücü sürümleri, maskelenmiş kanıt, geçen/kalan
senaryolar ve onaylayan kişi eklenir.
