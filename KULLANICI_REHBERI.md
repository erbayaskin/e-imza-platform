# E-İmza kullanıcı rehberi

## İlk kullanım

1. Kurum yazılım merkezinden yayıncısı ve dijital imzası doğrulanmış E-İmza Ajanı'nı kurun.
2. Kart üreticisinin kurumca onaylı sürücüsünü kurun; internetten rastgele PKCS#11 indirmeyin.
3. Okuyucuyu bağlayıp kartı takın; portal yalnız tanımlı ATR/kart profilini destekler.
4. İmzalanacak belge adını, türünü, boyutunu, amacını ve özetini ekranda kontrol edin.
5. PIN'i yalnız yerel güvenli ajan penceresine girin. Web sayfası veya destek görevlisi PIN
   isterse işlemi durdurup güvenliğe bildirin.
6. Sonuçtaki imza seviyesi, zaman damgası ve doğrulama raporunu kontrol edin.

## Güvenlik

- PIN'i paylaşmayın, ekran kaydına almayın ve art arda denemeyin.
- Kartı işlem bitince çıkarın.
- Tarayıcı “ajan bulunamadı” diyorsa güvenlik duvarını kapatmayın; destek kaydı açın.
- Belge özeti/amacı beklediğinizden farklıysa PIN girmeden iptal edin.
- Kart kayıp/çalıntıysa derhal kurum güvenliği ve ilgili ESHS üzerinden iptal sürecini başlatın.

## Sık hata durumları

- **Kart tanınmıyor:** okuyucu bağlantısı, kurumsal sürücü ve desteklenen kart listesini kontrol edin.
- **PIN yanlış/kart kilitli:** yeni deneme yapmayın; ESHS/kurum prosedürünü izleyin.
- **Zaman damgası alınamıyor:** işlemi tekrar tekrar göndermeyin; hata koduyla destek kaydı açın.
- **Doğrulama INDETERMINATE:** imza kesin geçersiz demek değildir; gerekli kanıt veya hizmet
  o anda bulunamamıştır.
- **Doğrulama INVALID:** belgeyi güvenilir kaynaktan yeniden alın; imzayı geçerli kabul etmeyin.

Destek kaydında correlation ID, zaman ve genel hata kodunu paylaşın; belge, PIN, token veya
sertifika özel bilgilerini eklemeyin.
