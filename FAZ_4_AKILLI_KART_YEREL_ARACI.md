# Faz 4 — Akıllı Kart ve Yerel Aracı MVP

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

Tarih: 28.07.2026  
Durum: **Yazılım MVP'si tamamlandı; gerçek kart/sürücü kabul testi bekliyor**

## 1. Amaç ve teslim sınırı

Bu fazda `smartcard-agent`, Java 21 ve Spring Boot 4.1.0 ile çalıştırılabilir bir
yerel aracıya dönüştürülmüştür. Aracı:

- PC/SC üzerinden okuyucu ve kartları keşfeder.
- ATR değerini normalize eder ve dış yapılandırmadaki maskeli profille eşleştirir.
- Yalnız yönetici tarafından yapılandırılmış mutlak PKCS#11 kitaplık yolunu yükler.
- Karttaki X.509 sertifikalarını listeler.
- PIN'i yalnız yerel pencerede alır ve kullanımdan hemen sonra karakter dizisini temizler.
- Merkezi API'nin Ed25519 imzalı, cihaz bağlı ve kısa ömürlü manifestindeki SHA-256
  özetini RSA PKCS#1 v1.5 veya ECDSA ile kart üzerinde imzalar.

Bu teslim gerçek bir kartla nitelikli elektronik imza üretildiği iddiasında değildir.
İlk üretici kartı, gerçek ATR, sürücü ve test sertifikası sağlandığında donanım kabul
matrisi ayrıca yürütülecektir.

## 2. Güvenlik sınırı

Yerel servis yalnız `127.0.0.1:18443` adresine bağlanır. Uzak istemci ve loopback
olmayan `Host` değeri reddedilir. Tarayıcı çağrılarında Origin allowlist uygulanır;
durum değiştiren çağrılar `X-EImza-Agent: 1` başlığını ister.

PIN:

- HTTP DTO'larında ve OpenAPI sözleşmesinde yoktur.
- Loglanmaz, önbelleğe yazılmaz ve merkezi API'ye gönderilmez.
- `JPasswordField` ile yerelde alınır.
- PKCS#11 çağrısı bittiğinde `char[]` sıfırlanır.
- Grafik arayüz bulunmayan ortamda güvenli biçimde hata verir.

İmzalama manifesti özgün JSON baytları üzerinden doğrulanır; yeniden serialize edilmez.
Manifestte cihaz, okuyucu, sertifika parmak izi, algoritma, özet, nonce ve en fazla
10 dakikalık geçerlilik penceresi bulunur. Yanlış imza, cihaz, süre ve tekrar kullanım
reddedilir. Merkezi açık anahtar yapılandırılmamışsa imzalama kapalıdır.

## 3. Kart profili yapılandırması

Profil HTTP üzerinden değiştirilemez. Kurulum/yönetim yapılandırmasına örnek:

```yaml
eimza:
  agent:
    device-id: cihaz-001
    manifest-public-key: ${EIMZA_AGENT_MANIFEST_PUBLIC_KEY}
    allowed-origins:
      - https://imza.example.gov.tr
    card-profiles:
      - id: ornek-kart
        display-name: Örnek Akıllı Kart
        device-type: SMART_CARD
        atr: 3B950040
        atr-mask: FFFF00FF
        pkcs11-library: C:\Program Files\Vendor\pkcs11.dll
        auto-discover-slot: true
        slot-list-index: 0
        allowed-mechanisms:
          - RSA_PKCS1_SHA256
          - ECDSA_SHA256
```

ATR yalnız sürücü/profil adayı seçer; karta veya imza sahibine güven sağlamaz.
Hiç eşleşme yoksa `UNSUPPORTED_CARD`, birden fazla eşleşme varsa
`AMBIGUOUS_CARD_PROFILE` üretilir.

`SMART_CARD` profillerinde ATR ve ATR maskesi zorunludur. Ajan PKCS#11 slotlarını
PIN kullanmadan tarar, sertifikanın bulunduğu dolu token slotunu seçer ve PIN'i
yalnız bu slota uygular. Sağlayıcı sertifikayı oturum açmadan göstermiyorsa
`slot-list-index` fallback değeridir. Akıllı kartlarda otomatik keşif kapatılamaz.

Client-side Smart Card Agent yalnız `SMART_CARD` kabul eder. HSM profili ajan
yapılandırmasında başlangıç hatası üretir. HSM yalnız server-side imzalama
profili olarak, yönetici kontrollü sürücü ve sabit slotla yapılandırılır;
ayrıntılar `IKI_MODLU_IMZALAMA_MIMARISI.md` dosyasındadır.

## 4. Yerel API

Sözleşme:
`smartcard-agent/src/main/resources/static/openapi/smartcard-agent-v1.yaml`

| Uç | İşlev |
|---|---|
| `GET /agent/v1/cards` | Okuyucu, ATR ve profil durumunu keşfeder |
| `GET /agent/v1/cards/{readerId}/certificates` | Bellekteki son sertifika listesini döndürür |
| `POST /agent/v1/cards/{readerId}/certificates/refresh` | Yerel PIN ile kart sertifikalarını yeniler |
| `POST /agent/v1/signing-requests` | İmzalı manifesti doğrular ve yerel PIN ile özeti imzalar |

Okuyucu adı dışarı verilmez; SHA-256 tabanlı kısaltılmış okuyucu kimliği kullanılır.
Sertifika cevabında SHA-256 parmak izi, konu, düzenleyen, seri, geçerlilik ve anahtar
algoritması bulunur.

## 5. Hata modeli

Ele alınan başlıca kodlar:

- `PCSC_ERROR`, `READER_NOT_FOUND`, `CARD_REMOVED`
- `UNSUPPORTED_CARD`, `AMBIGUOUS_CARD_PROFILE`
- `PKCS11_LIBRARY_NOT_FOUND`, `PKCS11_CONFIGURATION_ERROR`, `PKCS11_ERROR`
- `PIN_CANCELLED`, `PIN_INCORRECT`, `PIN_LOCKED`, `PIN_UI_UNAVAILABLE`
- `INVALID_MANIFEST_SIGNATURE`, `WRONG_DEVICE`, `MANIFEST_EXPIRED`,
  `MANIFEST_REPLAYED`
- `CERTIFICATE_NOT_FOUND`, `PRIVATE_KEY_NOT_FOUND`, `MECHANISM_NOT_ALLOWED`

İç dosya yolu, sürücü ayrıntısı ve stack trace API cevabına taşınmaz.

## 6. Test sonucu

Donanımsız otomatik testler şunları doğrular:

- ATR normalizasyonu, maske eşleştirmesi ve belirsiz profil reddi.
- Ed25519 manifest imzası, süre ve nonce tekrar kullanım kontrolü.
- RSA ve ECDSA için önceden hesaplanmış SHA-256 özetinin standart
  `SHA256withRSA` / `SHA256withECDSA` doğrulayıcılarıyla uyumu.
- Tüm çok modüllü Maven derlemesi ve mevcut önceki faz testleri.

## 7. Gerçek donanım kabul kapısı

Faz 4'ün saha kabulü için her desteklenen kombinasyonda şu bilgiler sağlanmalıdır:

1. Kart marka/modeli, ATR ve gerekiyorsa kontrollü maske.
2. İşletim sistemi/mimari ve imzalı üretici PKCS#11 sürücü sürümü.
3. Slot/token davranışı ve izin verilen mekanizmalar.
4. Test sertifikasıyla doğru PIN, yanlış PIN, kilitli PIN ve kart çıkarma senaryoları.
5. Üretilen imzanın bağımsız araçla doğrulama sonucu.

Bu test tamamlanmadan kart profili üretim allowlist'ine alınmamalıdır.
