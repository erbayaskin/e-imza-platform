# İş sürekliliği ve felaket kurtarma

## Hedef mimari

- API en az iki pod ve iki worker node üzerinde çalışır.
- Uygun cluster varsa pod'lar bölgelere yayılır.
- PostgreSQL ayrı, çok bölgeli HA hizmetidir.
- Container registry, GitOps deposu, secret yöneticisi ve OIDC bağımlılıkları ayrı hata
  alanları olarak izlenir.
- Önerilen hizmet SLO'su aylık %99,9; önerilen RPO 15 dakika, RTO 4 saattir.

## Bağımlılık kaybında davranış

| Bağımlılık | Davranış |
|---|---|
| OIDC | Yeni token doğrulaması yapılamıyorsa korumalı uçlar fail-closed; health açık kalır |
| PostgreSQL | Yazma/DB gerektiren işlem durur; yanlış başarılı sonuç üretilmez |
| TSA | Yeni B-T/B-LTA işlemi açık hata/yeniden denenebilir durum; zaman damgasız nitelikli iddia yok |
| OCSP/SİL | Kanıt yokluğu `INVALID` yapılmaz; politika gereğine göre `INDETERMINATE` |
| Tek node/zone | PDB/topology spread ile kalan pod hizmet verir |
| Registry/GitOps | Çalışan imaj etkilenmez; yeni yayın durur |
| Yerel ajan dağıtımı | Mevcut imzalı sürüm çalışır; yeni halka durdurulur |

## Tatbikat takvimi

- Aylık: tek pod ve node tahliye testi.
- Üç aylık: PostgreSQL yalıtılmış PITR geri yükleme.
- Altı aylık: OIDC/TSA/OCSP kesinti masası ve teknik tatbikatı.
- Yıllık: bölge/küme kaybı, yeni kümeye GitOps + secret + DB failover tatbikatı.
- Her büyük sürüm öncesi: geri alma ve eski ajan uyumluluk testi.

Her tatbikat planlanan/ölçülen RPO-RTO, veri bütünlüğü, karar sahipleri, eksikler ve terminli
iyileştirmelerle kapatılır.

## Yeni kümeye kurtarma sırası

1. Olay komutanı felaket ilanını ve hedef kurtarma zamanını kaydeder.
2. Registry digest, imza ve SBOM doğrulanır.
3. Namespace/politikalar GitOps'tan kurulur; henüz trafik açılmaz.
4. Secret yöneticisi bağlantısı ve TLS sertifikaları doğrulanır.
5. PostgreSQL failover/PITR tamamlanır ve güven snapshot zinciri kontrol edilir.
6. API iki pod ile açılır; Flyway yalnız doğrulanmış şema planıyla çalışır.
7. Sentetik sertifika, imza, tenant ayrımı ve yönetim yetkisi testleri geçilir.
8. Trafik %5, %25, %100 halkalarıyla açılır; her halkada en az 30 dakika gözlenir.
