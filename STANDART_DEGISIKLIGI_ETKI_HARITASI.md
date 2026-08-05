# E-İmza Standart Değişikliği Etki Haritası

> Amaç: BTK, ETSI, IETF veya algoritma kuralları değiştiğinde hangi belge, ayar, kod ve testin inceleneceğini adım adım göstermek  
> Son kontrol: 28 Temmuz 2026  
> Kural: Eski politika ve güven deposu sürümleri değiştirilmez; yeni yürürlük dönemi için yeni sürüm yayımlanır.

## 1. Kaynak envanteri

| Kimlik | Kaynak ve sabitlenen sürüm | Resmî adres | Başlıca etki alanı |
|---|---|---|---|
| STD-TR-001 | 5070 sayılı Elektronik İmza Kanunu | https://www.mevzuat.gov.tr/mevzuat?MevzuatNo=5070&MevzuatTertip=5&MevzuatTur=1 | Hukukî sınıflandırma, güvenli elektronik imza |
| STD-TR-002 | BTK e-imza mevzuat envanteri | https://www.btk.gov.tr/elektronik-imza-mevzuati | Yönetmelik, tebliğ, kurul kararı değişiklikleri |
| STD-TR-003 | BTK Elektronik İmza Kullanım Profilleri Rehberi, Sürüm 1.0, Haziran 2012 | https://www.btk.gov.tr/uploads/pages/elektronik-imza-kullanim-profilleri-rehberi-5a33ff5b59f93.pdf | P1–P4, politika ve doğrulama iş akışı |
| STD-TR-004 | BTK 2012/DK-15/299, 02.07.2012 | https://www.btk.gov.tr/uploads/pages/2012-07-02-tarih-2012-dk-15-299-sayili-guvenli-eimza-kurul-karari-1-5a33ff476c9e9.pdf | Güvenli imza format/profil dayanağı |
| STD-ETSI-001 | ETSI EN 319 102-1 V1.4.1, 2024-06 | https://www.etsi.org/deliver/etsi_en/319100_319199/31910201/01.04.01_60/en_31910201v010401p.pdf | İmza doğrulama süreci ve sonuç modeli |
| STD-ETSI-002 | ETSI EN 319 122-1 V1.3.1, 2023-06 | https://www.etsi.org/deliver/etsi_en/319100_319199/31912201/01.03.01_60/en_31912201v010301p.pdf | CAdES B-B/B-T/B-LT/B-LTA ve ATSHashIndexV3 |
| STD-ETSI-003 | ETSI TS 119 102-2 V1.4.1, 2023-06 | https://www.etsi.org/deliver/etsi_ts/119100_119199/11910202/01.04.01_60/ts_11910202v010401p.pdf | Doğrulama raporu |
| STD-IETF-001 | RFC 5280, Mayıs 2008 | https://www.rfc-editor.org/rfc/rfc5280 | X.509 yol ve SİL profili |
| STD-IETF-002 | RFC 6960, Haziran 2013 | https://www.rfc-editor.org/rfc/rfc6960 | OCSP |
| STD-IETF-003 | RFC 3161 ve RFC 5816 | https://www.rfc-editor.org/rfc/rfc3161 | Zaman damgası ve algoritma güncellemesi |
| STD-ALG-001 | Kurumca onaylanacak ETSI TS 119 312 baskısı | https://www.etsi.org/standards-search | Kabul edilen algoritmalar, anahtar boyları ve geçiş tarihleri |

`STD-ALG-001` için kesin baskı hukuk/bilgi güvenliği onayında doldurulmadan üretime çıkılmaz.

## 2. Gereksinimden koda izlenebilirlik

| Gereksinim | Kaynak | Ayar/kod | Temel test |
|---|---|---|---|
| Üç durumlu sonuç ve alt kontroller | STD-ETSI-001 | `ValidationIndication`, `ValidationCheck`, `ValidationReport`, `ValidationService` | Geçerli, kesin geçersiz ve kanıt yok senaryoları |
| Doğrulama zamanında güven yolu | STD-ETSI-001, STD-IETF-001 | `DefaultCertificateValidator`, `DatabaseTrustedCertificateProvider` | Tarihsel snapshot ile yol kurma |
| X.509 süre, CA, kullanım ve kısıtlar | STD-IETF-001 | `DefaultCertificateValidator`, `TrustStoreService` | CA olmayan kökün reddi, key usage ve süre |
| OCSP cevap ve responder doğrulaması | STD-IETF-002 | `NetworkRevocationDataProvider` | GOOD/REVOKED/UNKNOWN/eski/sahte cevap |
| SİL imza, issuer ve tazelik | STD-IETF-001 | `NetworkRevocationDataProvider` | İptal seri, eski ve yanlış imzalı SİL |
| CAdES Baseline B-T | STD-ETSI-002, STD-TR-003 | `CadesSignatureService`, `CadesSignatureVerifier`, `CadesSignatureInspector` | Pozitif ve bozuk içerik/imza |
| CAdES Baseline B-LT/B-LTA | STD-ETSI-001/002 | `CadesLongTermService`, `CadesLongTermVerifier`, `EmbeddedFirstRevocationDataProvider` | B-LT, ATSv3, yenileme ve gömülü kanıt |
| RFC 3161 zaman damgası | STD-IETF-003 | `Rfc3161TimestampClient`, `Rfc3161TimestampVerifier` | imprint/nonce/policy/imza/zincir |
| Türkiye profili ve NES ayrımı | STD-TR-001/003/004 | `CertificateValidationPolicy`, `ValidationProperties`, `DefaultCertificateValidator` | QCStatement/politika OID var-yok |
| Algoritma ve anahtar eşikleri | STD-ALG-001 | `eimza.validation`, `DefaultCertificateValidator` | SHA-1, kısa RSA/EC reddi |
| Güven kökü tarihçesi | STD-ETSI-001, STD-IETF-001 | V3 migration, `TrustStoreService`, `DatabaseTrustedCertificateProvider` | Ekle/güncelle/çıkar ve eski snapshot |

## 3. Bir standart değiştiğinde uygulanacak süreç

1. **Değişikliği kaydet:** Kaynak kimliği, eski/yeni sürüm, yayın ve yürürlük tarihi, resmî URL, indirilen belgenin SHA-256 özeti ve inceleyen kişi değişiklik kaydına eklenir.
2. **Normatif farkı çıkar:** “shall/must”, “shall not/must not”, geçiş tarihi, kaldırılan algoritma/OID ve yeni profil alanları ayrı liste halinde yazılır.
3. **Hukukî ve teknik etkiyi ayır:** Kanun/BTK sınıflandırması hukuk onayına; kriptografik/protokol değişikliği bilgi güvenliği ve geliştirme onayına gider.
4. **Bu matrisi güncelle:** Etkilenen gereksinim satırlarına yeni kaynak sürümü, kod noktası ve kabul testi yazılır.
5. **Yeni politika sürümü oluştur:** Anahtar boyu, algoritma, OID, tazelik veya profil kuralı değiştiyse mevcut sürüm ezilmez; yeni `policyVersion` ve yürürlük başlangıcı oluşturulur.
6. **Güven deposunu sürümle:** Kök/alt kök değiştiyse sertifika doğrulanmış resmî kaynaktan alınır, parmak izi ikinci kanaldan doğrulanır ve yeni immutable snapshot yayımlanır.
7. **Kodu değiştir:** Yalnız ayarla çözülemeyen format/protokol/sonuç semantiği değişiklikleri ilgili sınıflara uygulanır.
8. **Test vektörlerini güncelle:** En az geçerli, kesin geçersiz, belirsiz, eski politika ile tarihsel ve yeni politika ile güncel örnek eklenir.
9. **Geriye dönük doğrula:** Eski tarihli rapor/imza eski politika ve güven snapshot'ıyla aynı sonucu üretmelidir.
10. **Birlikte çalışabilirlik yap:** Yeni sürüm örnekleri en az bir bağımsız ürünle karşılaştırılır; Türkiye profili ayrıca proje testleriyle kontrol edilir.
11. **Onay ve yayın:** Hukuk, bilgi güvenliği ve ürün sahibi onayı; sürüm notu; geçiş/geri alma planı tamamlanır.
12. **İzlemeyi başlat:** Yeni hata kodları, `INDETERMINATE` oranı, OCSP/SİL gecikmesi ve politika uyuşmazlıkları için alarm eşikleri izlenir.

## 4. Değişiklik türüne göre dosya rotası

| Değişiklik | Önce bakılacak yer | Muhtemel uygulama noktası |
|---|---|---|
| RSA/EC boyu veya algoritma kaldırılması | `FAZ_1_UYUMLULUK_MATRISI.md` | `application.yml`, `ValidationProperties`, `DefaultCertificateValidator` |
| İmza özeti veya PKCS#1/ECDSA algoritması değişikliği | ETSI TS 119 312 ve format profili | `CadesSignatureAlgorithm`, `RawDigestSigningSupport`, `ServerPkcs11SigningService`, XAdES URI eşlemeleri, OpenAPI ve demo |
| Attached/detached/enveloped/enveloping kuralı değişikliği | ETSI CAdES/XAdES/PAdES profil ailesi | `SignaturePackaging`, `SigningSessionService`, format üretici/doğrulayıcıları ve V10 şeması |
| Yeni/çıkarılan sertifika politika OID'si | Bu belge ve ESHS politika kataloğu | `EIMZA_VALIDATION_REQUIRED_CERTIFICATE_POLICY_OIDS` |
| NES/QCStatements kuralı | BTK profil ve sertifika profili | `CertificateValidationPolicy`, `DefaultCertificateValidator` |
| OCSP protokol/tazelik değişikliği | RFC 6960 ardılı veya BTK OCSP profili | `NetworkRevocationDataProvider` |
| SİL/delta/indirect CRL değişikliği | RFC 5280 ve BTK SİL profili | `NetworkRevocationDataProvider` |
| CAdES signed/unsigned attribute değişikliği | ETSI EN 319 122 ailesi | `signature-cades` modülü |
| Zaman damgası algoritma/politika değişikliği | RFC 3161/5816 ve TSA politikası | `timestamp-client`, `ValidationProperties` |
| Sonuç kodu/karar ağacı değişikliği | ETSI EN 319 102-1 | `ValidationIndication`, `ValidationService`, API şeması |
| Kök/alt kök değişikliği | BTK/ESHS resmî yayınları | Güven deposu yönetim API'si; kod değişikliği gerekmez |
| Yeni format/seviye | BTK kararı ve ETSI XAdES/PAdES/CAdES | Yeni format modülü ve Faz 7 |

## 5. Değişiklik kayıt şablonu

Her incelemede aşağıdaki tabloya satır eklenir:

| Kayıt | Kaynak | Eski → yeni | Yayın/yürürlük | Normatif fark | Politika/kod/test etkisi | Onay | Durum |
|---|---|---|---|---|---|---|---|
| CHG-000 | Örnek | V1 → V2 | YYYY-AA-GG | Örnek gereksinim | `policy-v2`, sınıf, test | Hukuk/Güvenlik | Taslak |

## 6. Periyodik kontrol

- BTK mevzuat envanteri ve Kurul kararları: en az aylık ve canlı sürüm öncesi.
- ETSI/IETF kaynakları ile algoritma politikası: en az üç aylık ve kütüphane yükseltmesi öncesi.
- ESHS/TSA politika, kök, ara sertifika, OCSP/SİL adresleri: sağlayıcı bildirimi üzerine ve en az aylık.
- Kullanılan Java, Spring Boot ve Bouncy Castle güvenlik duyuruları: sürekli bağımlılık taramasıyla.

Kontrol “değişiklik yok” sonucuyla bitse bile tarih, kontrol eden ve kaynak URL kaydedilmelidir.

## 7. Değişiklik günlüğü

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 28.07.2026 | Faz 6 için kaynak envanteri, gereksinim-kod-test izlenebilirliği ve 12 adımlı standart güncelleme süreci oluşturuldu. |

## 8. Faz 8 güvenlik ve algoritma güncellemesi — 29 Temmuz 2026

| Kimlik | Sabitlenen kaynak | Resmî adres | Etki |
|---|---|---|---|
| STD-ALG-002 | ETSI TS 119 312 V2.1.1, 2026-06 | https://www.etsi.org/deliver/etsi_ts/119300_119399/119312/02.01.01_60/ts_119312v020101p.pdf | Algoritma bitiş tarihleri, PQC geçişi, hibrit şemalar, CAdES/PAdES/XAdES ve TSA algoritmaları |
| STD-SEC-001 | OWASP ASVS 5.0.0, 2025-05 | https://github.com/OWASP/ASVS/tree/v5.0.0_release | Uygulama güvenlik kabul kontrolleri |
| STD-SEC-002 | OWASP API Security Top 10, 2023 | https://owasp.org/API-Security/editions/2023/en/0x03-introduction/ | Yetki, kaynak tüketimi, SSRF, yapılandırma ve haricî API riskleri |
| STD-SEC-003 | NIST SP 800-218 SSDF 1.1 | https://csrc.nist.gov/pubs/sp/800/218/final | Güvenli geliştirme ve tedarik zinciri |
| STD-IETF-004 | RFC 8725 JWT BCP | https://www.rfc-editor.org/info/rfc8725/ | JWT algoritma, issuer, audience ve claim doğrulaması |

`STD-ALG-001` geçici satırı yerine yeni incelemelerde `STD-ALG-002` esas alınır. V2.1.1'in
PQC ve hibrit şema hükümleri mevcut RSA/ECDSA MVP'sinde tam uygulanmış değildir. Yeni
algoritma/OID, kripto sağlayıcı, kart/HSM desteği, API formatı ve birlikte çalışabilirlik
vektörleri için ayrı politika sürümü oluşturulacaktır; eski tarihli doğrulama politikası
değiştirilmeyecektir.

Faz 8 güvenlik değişikliği rotası:

1. ASVS/API Security sürümü değişirse `FAZ_8_GUVENLIK_PERFORMANS_VE_KABUL.md`,
   güvenlik filtreleri, OAuth kapsam/tenant testleri ve CI kapıları incelenir.
2. İstek boyu veya kapasite değişirse `ApiLimits`, `application.yml`, nginx
   `client_max_body_size`, k6 vektörleri ve OpenAPI birlikte güncellenir.
3. JWT profili değişirse issuer, audience, izinli algoritma ve tenant claim sözleşmesi
   kimlik sağlayıcıyla birlikte sürümlenir.
4. Container/Java/Spring/Bouncy Castle değişirse SBOM, CodeQL, bağımlılık taraması,
   negatif ASN.1 testleri ve 30 dakikalık yük testi yeniden çalıştırılır.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.1 | 29.07.2026 | ETSI TS 119 312 V2.1.1 ve Faz 8 güvenlik kaynakları, PQC boşluğu ve değişiklik rotası eklendi. |

## 9. Faz 9 işletim kaynakları

| Kimlik | Kaynak | Resmî adres | İşletim etkisi |
|---|---|---|---|
| OPS-K8S-001 | Kubernetes Deployment, probe, PDB ve topology spread | https://kubernetes.io/docs/ | Yayın, sağlık, yedeklilik ve bakım |
| OPS-GITOPS-001 | Argo CD automated sync ve sync windows | https://argo-cd.readthedocs.io/en/stable/ | Onaylı yayın, sapma ve bakım penceresi |
| OPS-MON-001 | Prometheus alerting practices/rules | https://prometheus.io/docs/practices/alerting/ | Kullanıcı etkili ve eyleme dönüşebilir alarm |
| OPS-IR-001 | NIST SP 800-61 Rev.3, 2025 | https://csrc.nist.gov/pubs/sp/800/61/r3/final | Olay hazırlık, tespit, yanıt ve kurtarma |

Bu kaynakların sürüm/değişiklik kontrolü `ops/scripts/check-official-sources.ps1` ve aylık
CI raporuyla desteklenir. Kaynak farkı tek başına üretim ayarını değiştirmez; etki haritası,
runbook, manifest, test ve onay zinciri birlikte güncellenir.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.2 | 29.07.2026 | Faz 9 Kubernetes/GitOps, izleme ve olay müdahale kaynakları ile aylık kontrol rotası eklendi. |


## 10. Faz 11 çoklu imza etki rotası

Paralel ve seri imza davranışı değiştiğinde aşağıdaki noktalar birlikte incelenir:

| Değişiklik | Kod/şema | Zorunlu kabul testi |
|---|---|---|
| CAdES ortak imza veya counter-signature kuralı | `signature-cades`, `MultiSignatureType`, V12 | Tüm üst seviye ve iç içe `SignerInfo` değerlerinin doğrulanması |
| XAdES paralel veya CounterSignature kuralı | `signature-xades`, API paketleme doğrulaması | Her `ds:Signature` ve iç içe karşı-imzanın doğrulanması |
| PAdES ardışık/incremental update kuralı | `signature-pades` | Eski ve yeni PDF imza sözlüklerinin ayrı ayrı doğrulanması |
| Oturum alanı veya boyut sınırı | OpenAPI, `CreateSigningSessionRequest`, `SigningSessionEntity`, V12, `application.yml` | Eski SINGLE istemciler ve PARALLEL/SERIAL istekleri |

`targetSignatureIndex` sıfır tabanlıdır. CAdES/XAdES seri imzada hedef imzayı,
PAdES'te ise yeni incremental revision eklendiği için yalnız `0` değerini temsil eder.
XAdES `ENVELOPED + PARALLEL` önceki belgenin digest'ini değiştireceğinden desteklenmez;
paralel XAdES için `DETACHED` veya `ENVELOPING` kullanılır.

| Sürüm | Tarih | Değişiklik |
|---|---|---|
| 1.3 | 05.08.2026 | Faz 11 CAdES/XAdES paralel-seri ve PAdES seri imza etki/test rotası eklendi. |
