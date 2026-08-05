# Client-side ve Server-side E-İmzalama Mimarisi

Tarih: 29.07.2026

## 1. Karar

Platform iki imzalama modu destekler:

| Mod | Donanım | PIN | Cihaz seçimi |
|---|---|---|---|
| `CLIENT_SIDE` | Yalnız kullanıcının akıllı kartı | Yalnız yerel Smart Card Agent penceresi | `deviceId` UUID + cihaza özel Ed25519 kanıtı |
| `SERVER_SIDE` | Sunucu HSM'i veya sunucu akıllı kartı | HSM: güvenli `credentialRef`; kart: tek kullanımlık API PIN'i | Yönetici tanımlı `serverKeyId` |

İki mod da aynı CAdES, XAdES, PAdES, zaman damgası, sertifika doğrulama ve
güvenilir kök/alt kök deposunu kullanır.

## 2. Client-side akış

Client-side API isteği DLL, slot veya ATR içermez. Bunlar ajan kurulumundaki
yönetici kontrollü kart profilindedir.

1. Ajan akıllı kartı ATR ile eşleştirir.
2. PKCS#11 slotunu otomatik keşfeder.
3. Merkez `deviceId`, oturum, nonce, okuyucu ve belge özetine bağlı kısa
   ömürlü manifest üretir.
4. PIN yalnız ajan masaüstü penceresinde girilir.
5. Kart ham imzayı üretir.
6. Ajan sonucu cihaza özel Ed25519 anahtarıyla ayrıca imzalar.
7. Merkez kayıtlı cihaz açık anahtarıyla cihaz kanıtını doğrular.
8. Merkez kart imzasını doğrular ve seçilen imza formatını tamamlar.

UUID tek başına kimlik bilgisi değildir. `deviceId` cihazı adresler; Ed25519
imzası cihaz sahipliği kanıtıdır.

Yerel geliştirmede ajan anahtar yapılandırılmamışsa süreç ömrü boyunca geçici
bir Ed25519 anahtarı üretir. `/agent/v1/device` yalnız cihaz UUID'si ve açık
anahtarı döndürür. Demo ekranındaki `Yerel ajan cihaz anahtarını kaydet`
düğmesi bu açık anahtarı yerel DB'ye kaydeder.

Üretimde aşağıdaki iki değer secret/işletim sistemi güvenli deposundan ajana
verilmelidir:

```text
EIMZA_AGENT_DEVICE_PRIVATE_KEY=<Base64 PKCS#8 Ed25519>
EIMZA_AGENT_DEVICE_PUBLIC_KEY=<Base64 X.509 Ed25519>
```

Özel anahtar HTTP ile dışarı verilmez. İşletim sistemi anahtar deposuna
otomatik kalıcı kayıt, dağıtım/kurulum fazında tamamlanacak güvenlik işidir.

## 3. Server-side akış

İstemci yalnız `serverKeyId` gönderir. PKCS#11 kütüphanesi, slot, ATR,
sertifika parmak izi ve tenant yetkisi yönetici yapılandırmasındadır.

### HSM

- `pkcs11-library`, `slot-list-index`, `credential-ref` zorunludur.
- Slot taranmaz.
- PIN/parola istemci isteğinde kabul edilmez.
- `credential-ref` başlangıç uygulamasında environment secret adıdır.
- Credential sağlayıcısından alınan işlem kopyası `char[]` olarak tutulur ve
  temizlenir. Environment değişkeninin yaşam döngüsü işletim sistemi/süreç
  yöneticisinin sorumluluğundadır; üretimde Vault/secret-manager tercih edilir.

### Sunucu akıllı kartı

- `pkcs11-library`, `atr` ve `atr-mask` zorunludur.
- ATR eşleşmeden imza başlatılmaz.
- Slot akıllı kart için otomatik keşfedilir.
- PIN `/server-sign` isteğinde tek kullanımlık alınır.
- PIN DB'ye, hazırlık kaydına, denetim kaydına veya loga yazılmaz ve işlem
  sonunda temizlenir.

Örnek profiller `server-signing.example.yml` dosyasındadır.

## 4. Oturum API'si

Client-side oturum:

```json
{
  "signingMode": "CLIENT_SIDE",
  "deviceId": "a8a0dc09-54ab-40b7-b404-bebd55ff1756",
  "serverKeyId": null
}
```

Bu mod mevcut `manifest`, `agent-connected`, `approve`, ajan
`signing-requests` ve `complete` akışını kullanır. `complete` gövdesi kart
imzasıyla birlikte `deviceSignature` içerir.

Server-side oturum:

```json
{
  "signingMode": "SERVER_SIDE",
  "deviceId": null,
  "serverKeyId": "kurumsal-hsm-imza-1"
}
```

Oturum oluşturulduktan sonra:

```http
POST /api/v1/signing-sessions/{sessionId}/server-sign
X-Tenant-Id: ...
Content-Type: application/json

{"pin": null}
```

HSM için `pin` boş olur. Sunucu akıllı kartı için tek kullanımlık PIN verilir.

## 5. Kalıcılık ve güvenlik kontrolleri

- `signing_session.signing_mode`, `device_id` ve `server_key_id` DB constraint
  ile tutarlı tutulur.
- Client cihaz açık anahtarları tenant bağlı `client_device` tablosundadır.
- Server key profilleri tenant allowlist uygular.
- Aynı `serverKeyId` işlemleri süreç içinde kilitlenerek token oturumlarının
  birbirine karışması engellenir.
- Client-side HSM reddedilir; ajan profilleri yalnız `SMART_CARD` olabilir.
- İstemciden PKCS#11 DLL yolu, slot, ATR veya HSM credential kabul edilmez.

## 6. Kalan üretim işleri

- Ajan Ed25519 özel anahtarını Windows/macOS/Linux işletim sistemi güvenli
  anahtar deposunda otomatik oluşturup kalıcılaştırmak.
- Environment credential sağlayıcısına ek olarak Vault/KMS/secret-manager
  adaptörleri eklemek.
- Gerçek HSM ve sunucu akıllı kartıyla donanım kabul testlerini çalıştırmak.
- Cihaz anahtarı rotasyonu, iptali ve yeniden kayıt yönetim uçlarını eklemek.
