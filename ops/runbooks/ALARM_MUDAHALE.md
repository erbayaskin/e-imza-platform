# Alarm müdahale kataloğu

Her alarmda önce kullanıcı etkisi, son yayın, pod/zone dağılımı, PostgreSQL ve haricî
OIDC/TSA/OCSP durumunu aynı zaman aralığında karşılaştırın.

## EImzaApiUnavailable

SEV-1. Ingress sentetik kontrolü, endpoint/pod, son rollout ve DB erişimini doğrulayın.
Yeni yayınla ilişkiliyse GitOps commit'ini geri alın. DB felaketiyse yedek runbook'una geçin.

## EImzaApiRedundancyLost

SEV-3. PDB, HPA, node taint/pressure, image pull ve readiness nedenini inceleyin. Tek pod
sağlıklıyken planlı bakım yapmayın.

## EImzaApiHighErrorRate

SEV-2. Hata kodlarını endpoint/tenant/sürüm bazında ayırın. Stack trace veya belge içeriğini
loglamayın. OIDC/DB/haricî servis hatasını uygulama hatasından ayırın.

## EImzaApiHighP95Latency

SEV-3; imzalama/doğrulama tamamen duruyorsa SEV-2. DB havuzu, GC, CPU throttling ve
TSA/OCSP gecikmesini ayırın. Normal akış 2 s, kontrollü haricî servis akışı 10 s hedefidir.

## EImzaApiJvmHeapPressure

Heap dump kişisel veri/sertifika/belge içerebilir; yalnız güvenlik onaylı şifreli alana alın.
Önce istek boyutu dağılımı, GC ve pod limitini kontrol edin; körlemesine limiti yükseltmeyin.

## EImzaApiDatabasePoolPressure

Uzun sorgu, lock, bağlantı kaçağı ve DB kapasitesini inceleyin. Havuzu DB `max_connections`
değerini aşacak biçimde büyütmeyin.

## EImzaApiPodRestarting

`OOMKilled`, probe, node eviction ve uygulama çıkış kodunu ayırın. CrashLoop sırasında
replica sayısını artırmak kök nedeni gizlememelidir.
