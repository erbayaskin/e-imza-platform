# Kubernetes üretim dağıtımı

Bu paket satıcıdan bağımsız Kubernetes kaynaklarıdır. PostgreSQL küme dışında yönetilen/HA
servistir. `secret.example.yaml` yalnız şemadır; uygulanmaz ve gerçek değer içermez.

## İlk kurulum

1. Registry imajını SBOM ve test raporuyla üretin, tarayın ve imzalayın.
2. Üretim overlay'indeki registry, SHA-256 digest, DNS adı ve TLS secret adını değiştirin.
3. Secret yöneticisi/CSI/External Secrets ile `eimza-api-secrets` oluşturun.
4. `EIMZA_DB_*`, OIDC issuer/audience/JWS algoritması, tenant claim ve TSA değerlerini girin.
5. PostgreSQL ağ kuralını yalnız küme egress adreslerine açın; TLS zorunlu tutun.
6. `kubectl kustomize deploy/kubernetes/overlays/prod` çıktısını politika aracında doğrulayın.
7. `kubectl diff -k deploy/kubernetes/overlays/prod` ile değişikliği inceleyin.
8. GitOps pull request onayından sonra senkronize edin.
9. `kubectl -n eimza rollout status deployment/eimza-api --timeout=10m` çalıştırın.
10. Sağlık, hata oranı, p95 gecikme, DB havuzu ve imza doğrulama sentetik testini kontrol edin.

## Yükseltme ve geri alma

Yalnız digest değiştirerek yayın yapılır. Flyway migration'ı geriye uyumlu “expand/migrate/
contract” düzeninde olmalıdır; uygulama geri alınırken yeni şema eski pod tarafından okunabilir
kalmalıdır.

```shell
kubectl diff -k deploy/kubernetes/overlays/prod
kubectl apply -k deploy/kubernetes/overlays/prod
kubectl -n eimza rollout status deployment/eimza-api --timeout=10m
kubectl -n eimza rollout undo deployment/eimza-api
```

GitOps kullanılıyorsa `kubectl apply/undo` yerine onaylı commit geri alınır. Acil doğrudan
müdahale sonradan aynı commit'e yansıtılmalıdır; aksi halde yapılandırma sapması oluşur.

## Taşınabilirlik notları

- OpenShift restricted politikasıyla uyum için root, privilege escalation ve Linux
  capability kullanılmaz. OpenShift SCC'nin UID/GID ataması için
  `deploy/kubernetes/overlays/openshift` kullanılır.
- `ingressClassName`, ingress/monitoring namespace adları, DNS ve TLS secret kuruma göre
  overlay'de değiştirilir.
- FQDN tabanlı OIDC/TSA/OCSP/SİL egress kuralı standart NetworkPolicy ile taşınabilir biçimde
  ifade edilemez. Üretimde onaylı egress proxy veya CNI FQDN politikası kullanılmalıdır.
- HPA'nın çalışması için metrics-server; alarm kaynakları için Prometheus Operator önerilir.

## GitOps

`deploy/gitops` altındaki Argo CD Application ve AppProject şablonlarında repository adresi
değiştirilir. Üretimde otomatik sync/prune kapalıdır: onaylı yayın penceresinde manuel sync
yapılır, başarısız yayında önceki digest'i geri getiren Git commit'i uygulanır. Argo CD sync
window ve RBAC kuralları platform yöneticisi tarafından tanımlanır.
