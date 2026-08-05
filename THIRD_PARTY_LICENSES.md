# Üçüncü Taraf Lisans Denetimi

> Denetim tarihi: 5 Ağustos 2026  
> Kaynak: CycloneDX Maven Plugin 2.9.2 aggregate BOM  
> Proje sürümü: `0.1.0-SNAPSHOT`

Bu belge public kaynak ve binary release öncesindeki teknik lisans envanteridir; bağımsız hukuk
görüşü değildir. Her release'te SBOM yeniden üretilmeli ve farklar incelenmelidir.

## Sonuç

- SBOM bileşeni: 121
- Lisans metadata'sı eksik bileşen: 0
- Görülen lisans aileleri: Apache-2.0, Bouncy Castle Licence, BSD-2-Clause, BSD-3-Clause,
  CC0-1.0, EPL-1.0/EPL-2.0, GPL-2.0-with-classpath-exception, LGPL-2.1-only,
  LGPL-2.1-or-later, MIT ve MPL-2.0
- AGPL veya GPL-3.0 bileşeni: görülmedi

## Elle incelenen bileşenler

| Bileşen | Bildirilen seçenek | Teknik kullanım kararı |
|---|---|---|
| Logback classic/core | EPL-2.0 veya LGPL-2.1-only | Değiştirilmemiş bağımlılık; EPL-2.0 seçeneği esas alınır |
| JNA | Apache-2.0 veya LGPL-2.1-or-later | Apache-2.0 seçeneği esas alınır |
| Jakarta Annotation/Transaction | EPL-2.0 veya GPL-2.0 + Classpath Exception | EPL-2.0 seçeneği esas alınır |
| Jakarta Persistence | EPL-2.0 veya BSD-3-Clause | BSD-3-Clause/EPL-2.0 seçenekleri uygundur |
| AspectJ Weaver | EPL-2.0 | Değiştirilmemiş bağımlılık; EPL notice/dağıtım şartları korunur |
| H2 | MPL-2.0 veya EPL-1.0 | Yerel/test veritabanı bağımlılığı; ilgili notice/dağıtım şartları korunur |
| Bouncy Castle | Bouncy Castle Licence | Özel fakat izin verici lisans; üç Maven paketi CI istisnasında açıkça listelenir |

Apache-2.0 proje lisansı üçüncü taraf bileşenlerin lisansını değiştirmez. Binary dağıtımda
her bileşenin kendi lisans/notice şartı korunmalı; release paketi aggregate SBOM, `LICENSE`,
`NOTICE` ve gerekli üçüncü taraf notice metinlerini içermelidir.

## CI politikası

Dependency Review yeni bağımlılıkları hem güvenlik hem lisans açısından kontrol eder. Mevcut
SPDX lisans aileleri allowlist'tedir. SPDX kimliği bulunmayan aşağıdaki Bouncy Castle PURL'ları
yalnız lisans kontrolü için açık istisnadır:

- `pkg:maven/org.bouncycastle/bcprov-jdk18on`
- `pkg:maven/org.bouncycastle/bcpkix-jdk18on`
- `pkg:maven/org.bouncycastle/bcutil-jdk18on`

İstisna yeni grup/paketlere genişletilmez. Sürüm yükseltmesinde Bouncy Castle lisans metni
yeniden kontrol edilir.

## Release komutu

```powershell
mvn "-Dmaven.repo.local=D:\ErbayProject\.m2\repository" `
  org.cyclonedx:cyclonedx-maven-plugin:2.9.2:makeAggregateBom `
  -DskipTests
```

Üretilen `target/bom.json` ve `target/bom.xml` Git'e commit edilmez; release artifact'i olarak
yayımlanır.
