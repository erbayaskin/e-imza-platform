# Güvenlik olayı müdahale runbook'u

Bu süreç NIST SP 800-61 Rev.3 ve kurum CSF 2.0 süreciyle birlikte uygulanır.

## Şiddet

| Seviye | Örnek | İlk yanıt |
|---|---|---:|
| SEV-1 | Özel anahtar/PIN şüphesi, güven deposu yetkisiz değişikliği, toplu yanlış `VALID`, tam kesinti | 15 dk |
| SEV-2 | Tenant ihlali, aktif istismar, TSA/OCSP uzun kesinti, ciddi veri bütünlüğü riski | 30 dk |
| SEV-3 | Kısmi kapasite, tek pod/zone kaybı, başarısız ajan halkası | 4 saat |
| SEV-4 | Etkisiz anomali, iyileştirme talebi | 1 iş günü |

## Adımlar

1. **Tespit ve kayıt:** alarm zamanı, correlation ID, sürüm/digest, tenant, etkilenen
   akış ve ilk kanıtı kaydedin; belge, PIN, token veya kişisel veriyi ticket'a kopyalamayın.
2. **Sınıflandırma:** olay komutanı, teknik lider, güvenlik, DBA ve hukuk/KVKK sorumlusunu
   şiddete göre çağırın.
3. **Kanıt koruma:** pod loglarını, audit kayıtlarını, DB erişim/audit loglarını, GitOps
   commit'ini, imaj/SBOM/imza ve zaman senkronizasyon kanıtını salt okunur alana aktarın.
4. **Sınırlama:** gerekli scope/istemci/tenant'ı kapatın; şüpheli ajan sürümünü durdurun;
   güven deposu yönetim erişimini dondurun. Kanıt yok edilmeden pod silmeyin.
5. **Giderme:** ana nedeni düzeltin; token/secret/sertifika döndürün; kötü digest'i engelleyin;
   bilinen güvenli sürümü GitOps ile yayınlayın.
6. **Kurtarma:** sentetik imza ve sertifika doğrulaması, tarihsel snapshot, TSA/OCSP ve
   performans smoke testini geçin; trafiği halkalarla açın.
7. **Bildirim:** hukuk/KVKK/BTK/ESHS bildirim gereğini kurum yetkilisi değerlendirir;
   geliştirme ekibi kendiliğinden dış bildirim yapmaz.
8. **Ders çıkarma:** SEV-1/2 için beş iş günü içinde kök neden, zaman çizelgesi, etki,
   düzeltici işler, sahip ve termin yayınlanır; aynı saldırı için regresyon testi eklenir.

## Özel senaryolar

- **Güven deposu değişikliği:** yönetim API'sini durdurun, son onaylı snapshot'ı belirleyin,
  audit zincirini koruyun; fiziksel silme yapmayın.
- **Yanlış doğrulama sonucu:** etkilenen politika ve snapshot sürümünü dondurun; geçmiş
  raporları aynı zaman/politika ile yeniden üretin.
- **Ajan/PKCS#11 olayı:** dağıtım halkasını durdurun, imzalı MSI hash'ini karşılaştırın,
  kart kilitlenme riski varsa kullanıcıya yeni PIN denemesi yaptırmayın.
- **TSA/OCSP/SİL kesintisi:** sonucu `INVALID` yapmayın; `INDETERMINATE` oranını ve
  önbellek yaşını izleyin, yalnız onaylı sağlayıcı failover'ı kullanın.
