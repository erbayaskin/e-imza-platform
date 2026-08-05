# GitHub Public Yayın Kontrol Listesi

> Hedef repository: `https://github.com/erbayaskin/e-imza-platform`  
> Varsayılan dal: `main`  
> Lisans: Apache-2.0  
> Sahip/CODEOWNER: `@erbayaskin`

Bu dosya, kaynak içindeki yönetişim kararları ile GitHub arayüzünde yapılması gereken ayarları
birbirinden ayırır. Kutular yalnız gerçekten uygulandıktan ve mümkünse bağlantı/ekran kanıtı
kaydedildikten sonra işaretlenir.

## 1. Repository oluşturma ve ilk push

- [x] GitHub'da `erbayaskin/e-imza-platform` public repository oluşturuldu.
- [x] GitHub'ın otomatik README/LICENSE/gitignore üretmesi kapalı bırakıldı; yerel dosyalar esas
  alındı.
- [x] Yerelde `git init`, `main`, ilk DCO sign-off'lu commit oluşturuldu.
- [x] `origin` doğru repository adresine bağlandı ve `main` push edildi.
- [x] GitHub Apache-2.0 lisansını algıladı.
- [x] Maven `io.github.erbayaskin` ve Java `io.github.erbayaskin.eimza` namespace'leri build
  artifact'lerinde doğrulandı; önceki kamu namespace'i kalmadı.
- [x] README, LICENSE, NOTICE, SECURITY ve Code of Conduct repository ana sayfasında erişilebilir.

Örnek ilk yayın komutları:

```powershell
cd D:\ErbayProject
git init
git branch -M main
git add .
git status --short
git commit -s -m "Initial public release"
git remote add origin https://github.com/erbayaskin/e-imza-platform.git
git push -u origin main
```


## 2. Genel repository ayarları

- [x] Description, topics (`java`, `spring-boot`, `electronic-signature`, `cades`, `xades`,
  `pades`, `pkcs11`, `turkey`) ve website alanı kontrol edildi.
- [x] Features altında Issues ve Discussions etkinleştirildi.
- [x] Yalnız **Allow squash merging** açık; merge commit ve rebase merge kapalı.
- [x] Squash commit başlığı PR başlığı olacak şekilde ayarlandı.
- [x] Branch'lerin merge sonrası otomatik silinmesi etkinleştirildi.

## 3. `main` ruleset/branch protection

- [x] Pull request zorunlu.
- [x] En az 1 approving review zorunlu.
- [x] Code owner review zorunlu.
- [x] Yeni commit geldiğinde eski approval düşürülüyor.
- [x] Son review konuşmalarının çözülmesi zorunlu.
- [x] Dalın merge öncesi güncel olması zorunlu.
- [x] Force-push ve deletion kapalı.
- [x] Bypass listesi boş; kurallar yöneticilere de uygulanıyor.
- [x] İlk workflow çalıştıktan sonra CI build, dependency review, DCO ve CodeQL analyze kontrolleri
  GitHub'da görünen **gerçek adlarıyla** required status check yapıldı.

## 4. Güvenlik ayarları

- [x] Settings → Security/Advanced Security → Private vulnerability reporting etkinleştirildi.
- [ ] Security alerts bildirimleri `@erbayaskin` için etkinleştirildi.
- [x] Dependabot alerts ve security updates etkinleştirildi.
- [x] Secret scanning ve push protection etkinleştirildi.
- [x] CodeQL taraması başarılı; açık code-scanning uyarısı kalmadı.
- [x] SECURITY.md içindeki Report a vulnerability bağlantısı doğru özel rapor uç noktasına gidiyor.

> Hesap düzeyindeki security notification tercihi GitHub kullanıcı ayarıdır; @erbayaskin
> hesabında arayüzden ayrıca doğrulanmalıdır.

## 5. Issue, katkı ve topluluk

- [x] Bug ve feature issue formları repository üzerinde mevcut.
- [x] Blank issue kapalı ve güvenlik bağlantısı özel rapora gidiyor.
- [x] Pull request şablonu repository üzerinde mevcut.
- [ ] CODEOWNERS otomatik review talebi oluşturuyor.
- [ ] DCO kontrolü imzasız test commit'ini reddediyor ve sign-off'lu commit'i kabul ediyor.
- [ ] Davranış bildirimi e-postası erişilebilir; public issue açılmıyor.

## 6. İlk CI ve yayın kanıtı

- [x] `CI`, `CodeQL` ve resmî kaynak monitor workflow'u en az bir kez başarılı çalıştı.
- [ ] Test raporu, JaCoCo ve CycloneDX SBOM artifact'leri indirilebildi.
- [ ] `CHANGELOG.md` ilk release için güncellendi.
- [ ] `0.1.0-SNAPSHOT` kararlı release sürümüne çevrildi.
- [ ] İmzalı annotated tag yerelde `git tag -v <tag>` ve GitHub üzerinde doğrulandı.
- [ ] Release JAR/MSI/container, checksum, SBOM, LICENSE ve NOTICE ile yayımlandı.

## 7. Public veri kontrolü

- [x] Git geçmişi dahil secret taraması temiz.
- [x] PIN, özel anahtar, token, gerçek NES/kişisel veri ve üretim endpoint'i yok.
- [x] `agent-local.yml`, `.env`, keystore, runtime sertifikaları ve build çıktıları Git'e girmedi.
- [x] Public belgelerde kurum içi adres, kişi verisi ve hukuken doğrulanmamış uyumluluk iddiası yok.
- [x] Üçüncü taraf bağımlılık lisansları/SBOM public kaynak yayını öncesi gözden geçirildi.

## 8. Tamamlanma kaydı

| Alan | Değer |
|---|---|
| Yayın tarihi | 5 Ağustos 2026 |
| İlk commit SHA | [f1f0721](https://github.com/erbayaskin/e-imza-platform/commit/f1f072102bfd45db108ce57e04e503ba2602db62) |
| İlk başarılı CI bağlantısı | [CI #31010281397](https://github.com/erbayaskin/e-imza-platform/actions/runs/31010281397) |
| Son doğrulanan CI bağlantısı | [CI #31011044426](https://github.com/erbayaskin/e-imza-platform/actions/runs/31011044426) |
| CodeQL bağlantısı | [CodeQL #31011044421](https://github.com/erbayaskin/e-imza-platform/actions/runs/31011044421) |
| Resmî kaynak monitor | [Official source monitor #31011126661](https://github.com/erbayaskin/e-imza-platform/actions/runs/31011126661) |
| Dal koruması | [Classic main branch protection](https://github.com/erbayaskin/e-imza-platform/settings/branches) |
| İlk release/tag | Yayımlanmadı; 0.1.0-SNAPSHOT devam ediyor |
| Onaylayan | Erbay AŞKIN |
