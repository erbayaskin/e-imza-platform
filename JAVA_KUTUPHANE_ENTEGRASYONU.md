# Java Uygulamalarında Doğrudan JAR/Kütüphane Entegrasyonu

Bu rehber REST API veya demo web sayfası kullanmadan `signature-*` JAR
modüllerini doğrudan bir Java 21 uygulamasına eklemeyi gösterir. Temel desen
her formatta aynıdır:

1. `prepare(...)` ile imzalanacak yapı ve özet hazırlanır.
2. Uygulama kendi `PrivateKey`, PKCS#11 kart veya HSM adaptörüyle imza üretir.
3. `completeBaseline(...)` veya `completeWithTimestamp(...)` artifact'i üretir.

## Maven bağımlılıkları

```xml
<dependency>
  <groupId>io.github.erbayaskin</groupId>
  <artifactId>signature-cades</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.erbayaskin</groupId>
  <artifactId>signature-xades</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.erbayaskin</groupId>
  <artifactId>signature-pades</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Akıllı kart profil/ATR/PKCS#11 yardımcılarını da kullanacak uygulama ayrıca:

```xml
<dependency>
  <groupId>io.github.erbayaskin</groupId>
  <artifactId>smartcard-agent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Geliştirme sürümünü yerel Maven deposuna kurmak için:

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" install
```

## Yazılım anahtarıyla örnek imzalayıcı

Bu yardımcı test ve sunucu `PrivateKey` örnekleri içindir. Akıllı kart/HSM
entegrasyonunda özel anahtar yerine cihazın `sign` çağrısı kullanılmalıdır.

```java
static byte[] signBytes(
        byte[] bytes, PrivateKey privateKey, String jcaAlgorithm) throws Exception {
    Signature signer = Signature.getInstance(jcaAlgorithm);
    signer.initSign(privateKey);
    signer.update(bytes);
    return signer.sign();
}
```

## CAdES-B-B doğrudan kullanım

```java
byte[] document = Files.readAllBytes(Path.of("sozlesme.xml"));
X509Certificate certificate = loadCertificate();
PrivateKey privateKey = loadPrivateKeyForTest();

CadesSignatureService service = new CadesSignatureService();
CadesSigningPreparation preparation = service.prepare(
        document,
        certificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null,                 // Kurumsal SignaturePolicy gerekirse verilir
        Instant.now(),
        false,                // false=DETACHED, true=ATTACHED
        true);                // sertifika tarih kontrolü

byte[] rawSignature = signBytes(
        preparation.signedAttributes(),
        privateKey,
        preparation.signatureAlgorithm().jcaName());

CadesSignatureResult result =
        service.completeBaseline(preparation, rawSignature);
Files.write(Path.of("sozlesme.p7s"), result.encodedSignature());
```

Doğrulama:

```java
CadesVerificationResult verification =
        new CadesSignatureVerifier().verifyDetached(
                document, result.encodedSignature(), null, Set.of());
if (!verification.cryptographicValidity()) {
    throw new IllegalStateException("CAdES geçersiz");
}
```

## XAdES-B-B doğrudan kullanım

```java
byte[] document = Files.readAllBytes(Path.of("belge.xml"));
byte[] digest = MessageDigest.getInstance("SHA-256").digest(document);
XadesSignatureService service = new XadesSignatureService();

XadesSigningPreparation preparation = service.prepare(
        SignaturePackaging.DETACHED,
        "urn:uuid:" + UUID.randomUUID(),
        document,
        "application/xml",
        digest,
        certificate,
        "RSA_PKCS1_SHA256",
        Instant.now(),
        true);

byte[] rawSignature =
        signBytes(preparation.signedInfo(), privateKey, "SHA256withRSA");
XadesSignatureResult result =
        service.completeBaseline(preparation, rawSignature);
Files.write(Path.of("belge.xades.xml"), result.encodedSignature());
```

## PAdES-B-B doğrudan kullanım

```java
byte[] pdf = Files.readAllBytes(Path.of("sozlesme.pdf"));
PadesSignatureService service = new PadesSignatureService();

PadesSigningPreparation preparation = service.prepare(
        pdf,
        certificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null,
        Instant.now(),
        certificate.getSubjectX500Principal().getName(),
        "Sözleşme onayı",
        true);

byte[] rawSignature = signBytes(
        preparation.cmsPreparation().signedAttributes(),
        privateKey,
        preparation.cmsPreparation().signatureAlgorithm().jcaName());
PadesSignatureResult result =
        service.completeBaseline(preparation, rawSignature);
Files.write(Path.of("sozlesme.signed.pdf"), result.encodedPdf());
```

## Akıllı kart ile doğrudan imza

`Pkcs11TokenService` Spring uygulamasına constructor injection ile alınabilir.
Public sertifikalar PIN'siz listelenir; PIN yalnız `sign` çağrısında kullanılır.

```java
@Service
final class CardCadesSigner {
    private final Pkcs11TokenService token;
    private final CadesSignatureService cades = new CadesSignatureService();

    CardCadesSigner(Pkcs11TokenService token) {
        this.token = token;
    }

    byte[] sign(
            byte[] document,
            CardProfile profile,
            CardCertificate selected,
            X509Certificate certificate,
            char[] pin) {
        CadesSignatureAlgorithm algorithm =
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256;
        CadesSigningPreparation preparation = cades.prepare(
                document, certificate, algorithm, null,
                Instant.now(), false, true);
        byte[] raw = null;
        try {
            raw = token.sign(
                    profile,
                    selected.fingerprintSha256(),
                    preparation.digestToSign(),
                    algorithm.name(),
                    pin);
            return cades.completeBaseline(preparation, raw).encodedSignature();
        } finally {
            Arrays.fill(pin, '\0');
            if (raw != null) Arrays.fill(raw, (byte) 0);
        }
    }
}
```

## CAdES paralel ve seri imza

```java
// Önceki .p7s içine bağımsız ikinci SignerInfo ekle
CadesSigningPreparation coSign = cades.prepareParallel(
        previousP7s, originalDocument, secondCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), false, true);
byte[] coRaw = signBytes(
        coSign.signedAttributes(), secondPrivateKey, "SHA256withRSA");
byte[] parallelP7s =
        cades.completeBaseline(coSign, coRaw).encodedSignature();

// Üst seviye 0 numaralı imzanın CMS counter-signature'ını üret
CadesSigningPreparation counter = cades.prepareCounterSignature(
        parallelP7s, 0, approverCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null, Instant.now(), true);
byte[] counterRaw = signBytes(
        counter.signedAttributes(), approverPrivateKey, "SHA256withRSA");
byte[] serialP7s =
        cades.completeBaseline(counter, counterRaw).encodedSignature();
```

## XAdES paralel ve seri imza

```java
XadesSigningPreparation coSign = xades.prepareParallel(
        previousXades,
        SignaturePackaging.DETACHED,
        documentUri,
        originalDocument,
        "application/xml",
        documentDigest,
        secondCertificate,
        "RSA_PKCS1_SHA256",
        Instant.now(),
        true);
byte[] coRaw = signBytes(
        coSign.signedInfo(), secondPrivateKey, "SHA256withRSA");
byte[] parallelXades =
        xades.completeBaseline(coSign, coRaw).encodedSignature();

XadesSigningPreparation counter = xades.prepareCounterSignature(
        parallelXades, 0, approverCertificate,
        "RSA_PKCS1_SHA256", Instant.now(), true);
byte[] counterRaw = signBytes(
        counter.signedInfo(), approverPrivateKey, "SHA256withRSA");
byte[] serialXades =
        xades.completeBaseline(counter, counterRaw).encodedSignature();
```

Paralel XAdES için `DETACHED` veya `ENVELOPING` kullanılır.
`ENVELOPED + PARALLEL` bilinçli olarak reddedilir.

## PAdES seri imza

```java
PadesSigningPreparation secondApproval = pades.prepareSequential(
        previouslySignedPdf,
        secondCertificate,
        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
        null,
        Instant.now(),
        "İkinci Onaycı",
        "İkinci aşama onayı",
        true);
byte[] raw = signBytes(
        secondApproval.cmsPreparation().signedAttributes(),
        secondPrivateKey,
        "SHA256withRSA");
byte[] signedTwice =
        pades.completeBaseline(secondApproval, raw).encodedPdf();
```

## Üretim notları

- Test dışındaki private key'leri dosyadan yüklemeyin; kart/HSM/KMS adaptörü kullanın.
- PIN, private key, raw imza girdisi ve belge Base64 değerini loglamayın.
- B-T için `TimestampClient` vererek `completeWithTimestamp` çağırın.
- Sertifika zinciri ve politika doğrulamasını artifact üretiminden ayrı uygulayın.
- Aynı iş isteğini uygulama katmanında idempotent yönetin.
