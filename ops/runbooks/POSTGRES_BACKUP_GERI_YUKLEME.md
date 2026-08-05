# PostgreSQL yedekleme ve geri yükleme runbook'u

## Hizmet hedefi

- Önerilen RPO: en fazla 15 dakika.
- Önerilen RTO: en fazla 4 saat.
- Topoloji: çok bölgeli HA PostgreSQL; API kümesinden ayrı yönetilir.
- Yedek: sürekli WAL/PITR + günlük tam yedek.
- Saklama: çevrimiçi PITR 35 gün; daha uzun saklama kurumun hukuk/KVKK politikasına göre.
- Kopya: ayrı hesap/bölge, şifreli ve immutable saklama.

Bu değerler pilot öncesi iş sahibi, DBA, bilgi güvenliği ve hukuk tarafından imzalanır.

## Günlük kontrol

1. Son başarılı tam yedeğin 24 saatten eski olmadığını doğrulayın.
2. Son arşivlenen WAL zamanının 15 dakikadan eski olmadığını doğrulayın.
3. Yedek deposu erişimi, immutable kilit ve anahtar kullanılabilirliğini kontrol edin.
4. Başarısız/yavaş yedek alarmını olay kaydına bağlayın.
5. Kontrol sonucu, zaman ve sorumlu kişiyi değişmez operasyon kaydına yazın.

## Geri yükleme tatbikatı

En az üç ayda bir, üretimden yalıtılmış hesaba geri yükleme yapılır:

1. Olaydan önceki hedef zamanı ve yedek kimliğini kaydedin.
2. Yeni PostgreSQL örneğine PITR yapın; üretim endpoint'ini değiştirmeyin.
3. Flyway şema sürümünü ve tablo sayımlarını doğrulayın.
4. Güvenilir sertifika snapshot'larının sürüm/zaman zincirini örnekleyin.
5. İmzalama oturumu ve uzun dönem yükseltme denetim kayıtlarını örnekleyin.
6. Uygulamayı salt okunur doğrulama modunda bağlayıp sentetik sertifika/imza doğrulaması yapın.
7. Ölçülen veri kaybını RPO, toplam süreyi RTO ile karşılaştırın.
8. Tatbikat DB'sini onaylı veri imha prosedürüyle kaldırın.

## Gerçek felaket geçişi

Olay komutanı ve DBA birlikte karar verir. Yazma trafiği durdurulur, son güvenli hedef zaman
seçilir, yeni DB doğrulanır, secret yöneticisindeki DB endpoint'i değiştirilir ve API pod'ları
kademeli yeniden başlatılır. Güven deposu snapshot tutarlılığı kanıtlanmadan imza doğrulama
trafiği açılmaz. Eski DB adli inceleme/geri dönüş süresi boyunca salt okunur korunur.
