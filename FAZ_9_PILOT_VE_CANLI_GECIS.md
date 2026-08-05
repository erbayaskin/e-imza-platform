# Faz 9 — Pilot ve kademeli canlıya geçiş

> Güncel gerçekleşme ve açık kabul durumu için [PROJECT_STATUS.md](PROJECT_STATUS.md) esas alınır.

## Ön koşullar

- Faz 8'in 30 dakikalık yük testi eşikleri geçmiş olmalı.
- Bağımsız kontrollü sızma testinde açık kritik/yüksek bulgu olmamalı.
- En az bir gerçek kart/okuyucu/sürücü matrisi tamamen geçmiş olmalı.
- Canlı OIDC, HA PostgreSQL, TSA/ESHS sözleşmesi ve güven kökleri onaylanmış olmalı.
- RPO/RTO, veri saklama, olay bildirimi ve on-call listesi imzalanmış olmalı.

Bu kapılar geçmeden pilot, yalnız sentetik/test verisiyle teknik deneme olarak adlandırılır.

## Aşamalar

| Aşama | Kapsam | Asgari gözlem | İlerleme ölçütü |
|---|---|---:|---|
| 0 | İç ekip, yazılım token | 2 iş günü | güvenlik/işlev regresyonu yok |
| 1 | %5 gerçek kullanıcı, sınırlı kart matrisi | 5 iş günü | SLO, destek ve imza başarısı hedefte |
| 2 | %25 kullanıcı | 5 iş günü | kritik/yüksek olay yok, kapasite payı yeterli |
| 3 | %100 kademeli | 2 iş günü yakın izleme | iş sahibi ve güvenlik onayı |

Her aşamada API digest'i, ajan MSI hash'i, kart/sürücü matrisi, güven snapshot sürümü,
algoritma politikası, TSA sağlayıcısı ve OIDC yapılandırması sabitlenir.

## Durdurma koşulları

- Yanlış `VALID`, tenant/yetki ihlali veya PIN/özel anahtar şüphesi.
- Kritik/yüksek güvenlik bulgusu.
- 5xx ≥%1, normal p95 ≥2 s veya haricî akış p95 ≥10 s eşiklerinin kalıcı ihlali.
- Kart kilitlenmesi ya da belirli kart/sürücüde tekrarlanan hatalı imza.
- Güven deposu, algoritma politikası veya TSA kanıt zincirinde tutarsızlık.
- Yedek/PITR doğrulaması veya on-call erişimi başarısızlığı.

## Go/no-go tutanağı

| Alan | Kanıt/koşu kimliği | Onaylayan | Tarih | Sonuç |
|---|---|---|---|---|
| İşlev ve birlikte çalışabilirlik |  |  |  | Bekliyor |
| Fiziksel kart matrisi |  |  |  | Bekliyor |
| Güvenlik/sızma testi |  |  |  | Bekliyor |
| Performans/SLO |  |  |  | Bekliyor |
| Yedek/PITR/RTO |  |  |  | Bekliyor |
| Hukuk/KVKK/uyumluluk |  |  |  | Bekliyor |
| Operasyon/on-call |  |  |  | Bekliyor |

Tüm satırlar “Geçti” olmadan üretim canlı kararı verilemez.
