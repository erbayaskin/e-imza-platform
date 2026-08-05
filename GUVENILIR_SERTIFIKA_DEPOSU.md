# Güvenilir Kök ve Alt Kök Sertifika Deposu

> Durum: İlk çalışan sürüm gerçeklendi  
> Tarih: 28 Temmuz 2026  
> Kapsam: Sertifika/imza doğrulamasında kullanılan güvenilir CA sertifikalarının DB yönetimi

## 1. Amaç

Sertifika ve elektronik imza doğrulaması, yalnız işletim sisteminin genel sertifika deposuna dayanmayacaktır. Türkiye e-imza politikası için güvenilir kabul edilen kök ve alt kök CA sertifikaları uygulamanın PostgreSQL veritabanında, sürümlü ve tarihsel olarak yönetilecektir.

Doğrulama motoru:

1. Doğrulama zamanını belirler.
2. O zamanda yürürlükte olan güven deposu sürümünü seçer.
3. O sürümde etkin olan kök ve alt kök sertifikaları yükler.
4. İmzalayanın NES sertifikasından güvenilir sertifikaya kadar X.509 yolunu kurar.
5. Zincir, politika, kullanım ve iptal kontrollerini ayrı ayrı yürütür.

## 2. Neden sürümlü depo?

Güvenilir sertifikayı fiziksel olarak silmek, geçmişte geçerli olan bir imzanın daha sonra doğrulanmasını bozabilir. Bu nedenle her yönetim işlemi yeni ve immutable bir snapshot üretir.

```text
Sürüm 1: Kök A + Alt Kök A1
Sürüm 2: Kök A + Alt Kök A1 + Kök B
Sürüm 3: Kök A + Kök B
```

Sürüm 3'te Alt Kök A1 çıkarılmış olsa bile Sürüm 1 ve 2 saklanır. Sürüm 2 yürürlükteyken oluşturulmuş bir imza, gerekiyorsa Sürüm 2 ile tarihsel olarak doğrulanabilir.

## 3. Yönetim davranışları

### Ekleme

- PEM veya Base64 DER biçiminde X.509 sertifikası alınır.
- Sertifikanın ayrıştırılabildiği doğrulanır.
- `BasicConstraints` CA şartı aranır.
- Key Usage eklentisi varsa `keyCertSign` şartı aranır.
- SHA-256 parmak izi hesaplanır.
- Aynı sertifikanın güncel sürümde tekrar eklenmesi engellenir.
- Yeni güven deposu sürümü oluşturulur.

### Güncelleme

Güncellenebilen üyelik alanları:

- Görünen ad
- `ROOT` veya `INTERMEDIATE` güven tipi
- Etkin/pasif durumu

Sertifikanın DER içeriği ve parmak izi immutable'dır. Bir CA sertifikası yenilendiğinde:

1. Yeni sertifika eklenir.
2. Eski sertifika yeni snapshot'tan çıkarılır veya pasif yapılır.
3. Eski snapshot'lar tarihsel doğrulama için korunur.

### Silme

API'deki `DELETE`, fiziksel SQL silmesi yapmaz. Sertifikayı yeni aktif snapshot'tan çıkarır ve önceki snapshot'ı `SUPERSEDED` durumuna geçirir.

Bu işlem geri alınmak istenirse sertifika tekrar eklenerek yeni bir sürüm yayımlanır.

## 4. Veri modeli

### `trusted_certificate`

Sertifikanın immutable kriptografik kimliği:

- DER içeriğinin Base64 gösterimi
- SHA-256 parmak izi
- Subject DN ve Issuer DN
- Seri numarası
- Geçerlilik başlangıç/bitiş zamanı

### `trust_store_version`

- Sürüm kimliği
- Snapshot içerik özeti
- `ACTIVE` veya `SUPERSEDED` durumu
- Yürürlük başlangıcı/bitişi

### `trust_store_entry`

Bir sertifikanın belirli snapshot içindeki üyeliği:

- Kök/alt kök tipi
- Görünen ad
- Etkinlik durumu

### `trust_store_lock`

Eşzamanlı iki yönetim işleminin birbirini ezmesini önleyen veritabanı kilit satırı.

## 5. REST API

| Metot | Uç | İşlem |
|---|---|---|
| `GET` | `/api/v1/admin/trusted-certificates` | Güncel snapshot |
| `POST` | `/api/v1/admin/trusted-certificates` | Sertifika ekleme ve yeni snapshot |
| `PUT` | `/api/v1/admin/trusted-certificates/{certificateId}` | Üyelik bilgisini güncelleme |
| `DELETE` | `/api/v1/admin/trusted-certificates/{certificateId}` | Güncel snapshot'tan çıkarma |
| `GET` | `/api/v1/admin/trusted-certificates/versions/{versionId}` | Tarihsel snapshot |

Örnek ekleme isteği:

```json
{
  "certificate": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----",
  "trustType": "ROOT",
  "displayName": "ESHS Kök Sertifikası",
  "enabled": true
}
```

Örnek güncelleme:

```json
{
  "trustType": "INTERMEDIATE",
  "displayName": "ESHS Alt Kök Sertifikası",
  "enabled": true
}
```

Üretim profilinde uçlar `eimza.admin` OAuth scope'u gerektirir.

## 6. Doğrulama motoru bağlantısı

`certificate-validation` modülündeki `TrustedCertificateProvider` portu şu girdiyi alır:

- `validationTime`

ve şu snapshot'ı döndürür:

- Depo sürümü
- Yürürlük aralığı
- Etkin X.509 kök/alt kök sertifikaları
- Her sertifikanın SHA-256 parmak izi ve tipi

`signature-api` içindeki `DatabaseTrustedCertificateProvider` bu portun JPA/PostgreSQL adaptörüdür.

Faz 6 doğrulama motoru bu portu doğrudan kullanmaktadır. PKIX trust anchor'ları yalnız
snapshot'taki etkin `ROOT` üyelerinden, yol kurma adayları ise etkin `INTERMEDIATE`
üyelerinden alınır. Seçilen snapshot kimliği her doğrulama raporunda
`trustStoreVersion` olarak döndürülür. Ayrıntılı akış:
[Faz 6 doğrulama motoru](FAZ_6_DOGRULAMA_MOTORU.md).

Bir doğrulama zamanı için snapshot bulunamazsa sistem sessizce işletim sistemi deposuna düşmez. Sonuç güven deposu kanıtı bulunamadığı için `INDETERMINATE/TRUST_STORE_NOT_FOUND` olarak ele alınmalıdır.

## 7. Güvenlik kuralları

- İşletim sistemi genel kök deposu Türkiye e-imza için otomatik güven kaynağı değildir.
- Son kullanıcı sertifikası CA deposuna eklenemez.
- Sertifika parmak izi SHA-256 ile hesaplanır.
- Aynı snapshot'ta aynı sertifika yalnız bir kez bulunabilir.
- Yönetim işlemleri pessimistic DB lock ile sıralanır.
- Her snapshot içerik özeti taşır.
- Eski snapshot ve sertifika içerikleri fiziksel olarak silinmez.
- API cevabında sertifikanın özel anahtarı gibi bir veri bulunmaz; depoda yalnız açık sertifika vardır.
- Sertifika geçerlilik tarihi dolmuş olsa bile tarihsel doğrulama amacıyla saklanabilir.
- Bir sertifikanın depoda olması tek başına NES uygunluğu anlamına gelmez; politika, kullanım, zaman ve iptal kontrolleri ayrıca yapılır.

## 8. Üretim öncesi kalan kontroller

- Ekleme/güncelleme/çıkarma için dört göz onay iş akışı
- Yönetim işlemlerinin `audit_event` hash zincirine yazılması
- Sertifika kaynağı ve BTK/ESHS yayın adresinin kaydedilmesi
- Kaynaktan indirilen sertifikanın out-of-band parmak izi doğrulaması
- PostgreSQL Testcontainers entegrasyon testi
- Güven deposu snapshot'larının uygulama anahtarıyla imzalanması
- Toplu BTK/ESHS sertifika içe aktarma ve değişiklik karşılaştırması
- Yanlışlıkla boş depo yayımlanmasını engelleyen üretim politikası

İlk çalışan sürüm CRUD, X.509 CA kontrolü, snapshot sürümleme, tarihsel seçim ve doğrulama portu bağlantısını kapsamaktadır. Dört göz onayı tamamlanmadan yönetim API'si üretimde açılmamalıdır.

## 9. Testler

Entegrasyon testi:

- Geçerli CA sertifikası ekleme
- Yeni snapshot oluşması
- Sertifika üyelik bilgisini güncelleme
- Güncellemede yeni snapshot oluşması
- Eski doğrulama zamanında tarihsel snapshot'ın seçilmesi
- Sertifikayı güncel snapshot'tan çıkarma
- Eski snapshot'ların korunması

Migration V3, H2 PostgreSQL uyumluluk modunda Flyway ve Hibernate şema doğrulamasıyla çalıştırılmaktadır.

Paketlenmiş Spring Boot JAR üzerinde yapılan HTTP duman testinde:

- Başlangıç deposu boş okundu.
- Kök sertifika eklendi ve ilk snapshot üretildi.
- Görünen ad güncellendi ve sürümün değiştiği doğrulandı.
- Sertifika çıkarıldı, güncel snapshot boşaldı ve sürüm yeniden değişti.
