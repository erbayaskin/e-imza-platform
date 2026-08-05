# GitHub Public Yayın Kontrol Listesi

> Hedef repository: `https://github.com/erbayaskin/e-imza-platform`  
> Varsayılan dal: `main`  
> Lisans: Apache-2.0  
> Sahip/CODEOWNER: `@erbayaskin`

Bu dosya, kaynak içindeki yönetişim kararları ile GitHub arayüzünde yapılması gereken ayarları
birbirinden ayırır. Kutular yalnız gerçekten uygulandıktan ve mümkünse bağlantı/ekran kanıtı
kaydedildikten sonra işaretlenir.

## 1. Repository oluşturma ve ilk push

- [ ] GitHub'da `erbayaskin/e-imza-platform` public repository oluşturuldu.
- [ ] GitHub'ın otomatik README/LICENSE/gitignore üretmesi kapalı bırakıldı; yerel dosyalar esas
  alındı.
- [ ] Yerelde `git init`, `main`, ilk DCO sign-off'lu commit oluşturuldu.
- [ ] `origin` doğru repository adresine bağlandı ve `main` push edildi.
- [ ] GitHub Apache-2.0 lisansını algıladı.
- [ ] Maven `io.github.erbayaskin` ve Java `io.github.erbayaskin.eimza` namespace'leri build
  artifact'lerinde doğrulandı; önceki kamu namespace'i kalmadı.
- [ ] README, LICENSE, NOTICE, SECURITY ve Code of Conduct repository ana sayfasında erişilebilir.

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

- [ ] Description, topics (`java`, `spring-boot`, `electronic-signature`, `cades`, `xades`,
  `pades`, `pkcs11`, `turkey`) ve website alanı kontrol edildi.
- [ ] Features altında Issues ve Discussions etkinleştirildi.
- [ ] Yalnız **Allow squash merging** açık; merge commit ve rebase merge kapalı.
- [ ] Squash commit başlığı PR başlığı olacak şekilde ayarlandı.
- [ ] Branch'lerin merge sonrası otomatik silinmesi etkinleştirildi.

## 3. `main` ruleset/branch protection

- [ ] Pull request zorunlu.
- [ ] En az 1 approving review zorunlu.
- [ ] Code owner review zorunlu.
- [ ] Yeni commit geldiğinde eski approval düşürülüyor.
- [ ] Son review konuşmalarının çözülmesi zorunlu.
- [ ] Dalın merge öncesi güncel olması zorunlu.
- [ ] Force-push ve deletion kapalı.
- [ ] Bypass listesi boş; kurallar yöneticilere de uygulanıyor.
- [ ] İlk workflow çalıştıktan sonra CI build, dependency review, DCO ve CodeQL analyze kontrolleri
  GitHub'da görünen **gerçek adlarıyla** required status check yapıldı.

## 4. Güvenlik ayarları

- [ ] Settings → Security/Advanced Security → Private vulnerability reporting etkinleştirildi.
- [ ] Security alerts bildirimleri `@erbayaskin` için etkinleştirildi.
- [ ] Dependabot alerts ve security updates etkinleştirildi.
- [ ] Secret scanning ve push protection hesap planında mevcutsa etkinleştirildi.
- [ ] CodeQL ilk taraması başarılı; kritik/yüksek açık bırakılmadı.
- [ ] `SECURITY.md` içindeki **Report a vulnerability** bağlantısı çalışıyor.

## 5. Issue, katkı ve topluluk

- [ ] Bug ve feature issue formları repository üzerinde açılıyor.
- [ ] Blank issue kapalı ve güvenlik bağlantısı özel rapora gidiyor.
- [ ] Pull request şablonu görüntüleniyor.
- [ ] CODEOWNERS otomatik review talebi oluşturuyor.
- [ ] DCO kontrolü imzasız test commit'ini reddediyor ve sign-off'lu commit'i kabul ediyor.
- [ ] Davranış bildirimi e-postası erişilebilir; public issue açılmıyor.

## 6. İlk CI ve yayın kanıtı

- [ ] `CI`, `CodeQL` ve resmî kaynak monitor workflow'u en az bir kez başarılı çalıştı.
- [ ] Test raporu, JaCoCo ve CycloneDX SBOM artifact'leri indirilebildi.
- [ ] `CHANGELOG.md` ilk release için güncellendi.
- [ ] `0.1.0-SNAPSHOT` kararlı release sürümüne çevrildi.
- [ ] İmzalı annotated tag yerelde `git tag -v <tag>` ve GitHub üzerinde doğrulandı.
- [ ] Release JAR/MSI/container, checksum, SBOM, LICENSE ve NOTICE ile yayımlandı.

## 7. Public veri kontrolü

- [ ] Git geçmişi dahil secret taraması temiz.
- [ ] PIN, özel anahtar, token, gerçek NES/kişisel veri ve üretim endpoint'i yok.
- [ ] `agent-local.yml`, `.env`, keystore, runtime sertifikaları ve build çıktıları Git'e girmedi.
- [ ] Public belgelerde kurum içi adres, kişi verisi ve hukuken doğrulanmamış uyumluluk iddiası yok.
- [ ] Üçüncü taraf bağımlılık lisansları/SBOM release öncesi gözden geçirildi.

## 8. Tamamlanma kaydı

| Alan | Değer |
|---|---|
| Yayın tarihi | |
| İlk commit SHA | |
| İlk başarılı CI bağlantısı | |
| CodeQL bağlantısı | |
| Ruleset bağlantısı/kimliği | |
| İlk release/tag | |
| Onaylayan | Erbay AŞKIN |
