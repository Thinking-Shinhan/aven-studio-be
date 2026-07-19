# 문구 검수 성능 측정

`POST /detail-pages/review-copy`의 클라이언트 전체 시간과 서버 내부 구간별 시간을 같은 요청으로 반복 측정합니다.

## 최초 1회 준비

1. 프론트엔드 개발자 도구의 Network 탭에서 실제 성공한 `review-copy` 요청 payload를 복사합니다.
2. `request.example.json`을 복사해 `request.local.json`을 만들고 실제 `productId`, `assetId`, 블록을 넣습니다.
3. 애플리케이션을 재시작하고 로그인 JWT를 준비합니다.

`request.local.json`은 실제 식별자나 문구를 포함할 수 있으므로 커밋하지 않는 것을 권장합니다.

## 캐싱/병렬화 전 기준선

PowerShell에서 저장소 루트를 기준으로 실행합니다.

```powershell
.\benchmarks\review-copy\run-benchmark.ps1 `
  -Token "JWT_값" `
  -RequestFile ".\benchmarks\review-copy\request.local.json" `
  -Stage before `
  -Warmup 3 `
  -Runs 20
```

실행 결과는 다음 두 파일로 생성됩니다.

- `before-날짜.csv`: 매 요청의 원시 데이터
- `before-날짜-summary.csv`: 발표에 바로 쓸 mean, p50, p95, min, max

원시 CSV에는 Git 커밋, 작업 트리 변경 여부, 요청 JSON의 SHA-256도 함께 기록되어 코드와 입력값을 추적할 수 있습니다. `git_dirty=False` 상태에서 측정해야 가장 깔끔한 증거가 됩니다.

`server_total_ms`는 서버 내부 전체, `image_pipeline_ms`는 이미지 전체 처리, `image_single_total_ms`는 그 요청에서 처리한 모든 이미지의 시간 합, `claude_call_ms`는 Claude 호출 구간입니다. 현재 순차 처리에서는 `image_single_total_ms`와 `image_pipeline_ms`가 거의 비슷합니다. 병렬화 후에는 개별 작업 시간의 합이 파이프라인 벽시계 시간보다 커지는 것이 정상입니다.

측정 중 다른 사용자의 검수 요청이 들어오면 Actuator 누적값 차이에 섞일 수 있으므로 로컬이나 단독 테스트 서버에서 한 번에 한 스크립트만 실행합니다.

## 개선 후 재측정

동일한 서버 환경, payload, JWT 사용자, warmup/runs 값으로 단계 이름만 바꿉니다.

```powershell
# 이미지 병렬화 직후
.\benchmarks\review-copy\run-benchmark.ps1 -Token "JWT_값" -RequestFile ".\benchmarks\review-copy\request.local.json" -Stage parallel -Warmup 3 -Runs 20

# 캐시 적용 직후
.\benchmarks\review-copy\run-benchmark.ps1 -Token "JWT_값" -RequestFile ".\benchmarks\review-copy\request.local.json" -Stage cache -Warmup 3 -Runs 20
```

### 캐시 적용 후 첫 검수(cache miss) 측정

메모리 캐시(Caffeine 등)라면 애플리케이션을 재시작한 직후, 다른 검수 요청을 보내지 않은 상태에서 아래 명령을 한 번만 실행합니다.

```powershell
.\benchmarks\review-copy\run-benchmark.ps1 `
  -Token "JWT_값" `
  -RequestFile ".\benchmarks\review-copy\request.local.json" `
  -Stage cache-miss `
  -Warmup 0 `
  -Runs 1
```

이 한 행이 첫 검수(cache miss) 수치입니다. 그 다음 앱을 재시작하지 않고 `-Stage cache -Warmup 3 -Runs 20`을 실행하면 재검수(cache hit) 수치가 됩니다.

Redis처럼 애플리케이션 밖에 캐시가 유지되는 방식이라면 앱 재시작만으로 비워지지 않습니다. 측정 전에 동일 상품/이미지에 해당하는 테스트 캐시 키만 삭제해야 하며, 전체 운영 캐시 삭제는 피합니다.

각 단계의 CSV와 해당 코드 커밋을 함께 남깁니다. README나 커밋 메시지에는 실행 환경(JDK, 로컬/배포 서버, 이미지 수와 원본 크기, Claude 모델)을 적습니다. 발표에서는 보통 `server_total_ms`, `image_pipeline_ms`의 p50/p95를 단계별 막대그래프로 비교하면 충분합니다.

## 메트릭 직접 확인

JWT 인증 헤더와 함께 아래 엔드포인트를 조회할 수 있습니다.

```text
/actuator/metrics/review.total
/actuator/metrics/review.image.pipeline
/actuator/metrics/review.image.single
/actuator/metrics/review.claude.call
```

Actuator 값은 프로세스 시작 후 누적 통계입니다. 포트폴리오의 원본 증거는 이 스크립트가 요청 전후 누적값의 차이를 저장한 CSV로 사용합니다.
