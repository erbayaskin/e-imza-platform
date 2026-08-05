# Faz 8 yük testi

Bu dizin referans düğümde tekrar üretilebilir k6 testini tanımlar. Ölçüm ayrı ayrı yapılır:

- sertifika doğrulama: 50 işlem/saniye;
- imza doğrulama: 20 işlem/saniye;
- 100 eşzamanlı kullanıcı kapasitesi;
- 30 dakika kesintisiz yük;
- uygulama içi normal akışlarda p95 `< 2 s`;
- kontrollü TSA/OCSP gecikme testinde p95 `< 10 s`.

## Hazırlık

1. Üretimle aynı Java, PostgreSQL ve ters vekil ayarlarını kullanın.
2. `performance/fixtures` altında sürüm kontrolüne alınmayan, kişisel veri içermeyen
   `certificate.json` ve `signature.json` test vektörlerini oluşturun.
3. Kontrollü test OIDC sağlayıcısından yalnız `eimza.validate` kapsamlı, kısa ömürlü token alın.
4. Test kök/alt köklerini yönetim API'siyle ayrı test veritabanına yükleyin.
5. OCSP/SİL/TSA için canlı uç değil, gecikmesi ve hata biçimi denetlenebilen sahte servis kullanın.

## Çalıştırma

```shell
k6 run -e SCENARIO=certificate -e RATE=50 -e DURATION=30m \
  -e BASE_URL=https://test-host:8443 -e BEARER_TOKEN=... \
  -e FIXTURE=performance/fixtures/certificate.json performance/k6/phase8-load-test.js

k6 run -e SCENARIO=signature -e RATE=20 -e DURATION=30m \
  -e BASE_URL=https://test-host:8443 -e BEARER_TOKEN=... \
  -e FIXTURE=performance/fixtures/signature.json performance/k6/phase8-load-test.js
```

## Ölçek testi

Önce tek API örneği, sonra iki API örneği çalıştırılır. Her koşuda k6 özeti yanında CPU,
bellek, GC duraklaması, PostgreSQL bağlantı havuzu, sorgu gecikmesi, OCSP/TSA gecikmesi ve
HTTP 429/5xx sayısı kaydedilir. Daha güçlü sunucular için oran kademeli artırılır; yeni üst
sınır ancak 30 dakikalık test eşikleri geçerse kapasite beyanına eklenir.
