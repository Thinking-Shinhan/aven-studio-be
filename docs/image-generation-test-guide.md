# 이미지 생성 자동저장 테스트 가이드

## 테스트 목적

- `POST /image-generation/jobs` 요청으로 Google AI 이미지 생성이 호출되는지 확인한다.
- `requestedCount` 수만큼 병렬 생성 호출이 수행되는지 확인한다.
- 생성된 이미지가 GCS에 자동 저장되는지 확인한다.
- 저장된 이미지가 `assets` 테이블에 `GENERATED` 상태로 자동 저장되는지 확인한다.
- `GET /image-generation/jobs/{jobId}`로 생성 결과와 signed URL을 조회할 수 있는지 확인한다.

> 주의: 실제 이미지 생성 API 호출 비용은 `POST /image-generation/jobs`를 호출할 때 발생한다.

## 사전 준비

### 서버 환경변수

서버 실행 전에 아래 환경변수가 필요하다.

```bash
export DB_USERNAME="{supabase db username}"
export DB_PASSWORD="{supabase db password}"
export JWT_SECRET="{jwt secret}"
export GOOGLE_AI_API_KEY="{google ai studio api key}"
export GOOGLE_AI_IMAGE_MODEL="gemini-2.5-flash-image"
```

`GOOGLE_AI_IMAGE_MODEL`은 생략하면 기본값으로 `gemini-2.5-flash-image`가 사용된다.

### DB 데이터

테스트 전에 아래 데이터가 DB에 존재해야 한다.

- 로그인 가능한 사용자
- 브랜드
- 상품
- 브랜드 무드
- 상품과 브랜드의 연결 관계

요청에 사용하는 `brandId`, `productId`, `brandMoodId`는 실제 DB에 존재하는 값이어야 한다.

## 1. 로그인

### API

| 기능 | Method | Endpoint |
|---|---:|---|
| 로그인 | POST | `/auth/login` |

### Request

```http
POST http://localhost:8080/auth/login
Content-Type: application/json
```

```json
{
  "loginId": "testuser1",
  "password": "Test1234!"
}
```

### 확인

응답 `Set-Cookie` 헤더에서 `accessToken` 쿠키를 확인한다.

이 서버의 JWT 필터는 `Authorization: Bearer` 헤더가 아니라 `accessToken` 쿠키를 읽는다.
Postman에서는 로그인 요청 후 쿠키가 자동 저장되면 그대로 다음 요청을 보내면 된다.

```http
Cookie: accessToken={accessToken}
```

## 2. 이미지 생성 요청

### API

| 기능 | Method | Endpoint |
|---|---:|---|
| 이미지 생성 요청 | POST | `/image-generation/jobs` |

### Request

```http
POST http://localhost:8080/image-generation/jobs
Content-Type: application/json
Cookie: accessToken={accessToken}
```

```json
{
  "brandId": 1,
  "productId": 1,
  "brandMoodId": 1,
  "cutType": "MODEL_CUT",
  "generationMode": "PARALLEL",
  "prompt": "프리미엄 패션 이커머스 모델컷을 생성한다. 상품의 색상과 형태를 유지하고, 브랜드 무드는 차분한 쿨톤, 저채도, 정제된 분위기로 반영한다. 최종 결과에는 한 명의 모델만 등장해야 하며 텍스트, 워터마크, 콜라주를 만들지 않는다.",
  "negativePrompt": "cheap ecommerce, neon, watermark, text overlay, collage",
  "userOptionsJson": "{\"colorTemperature\":\"cool\",\"cameraAngle\":\"eye_level_front\",\"framing\":\"full_body\"}",
  "referenceJson": "{}",
  "requestedCount": 2
}
```

### 필드 설명

| 필드 | 필수 | 설명 |
|---|---:|---|
| `brandId` | O | 이미지 생성에 사용할 브랜드 ID |
| `productId` | O | 생성 결과를 연결할 상품 ID |
| `brandMoodId` | O | 이미지 생성에 사용할 브랜드 무드 ID |
| `cutType` | O | `MODEL_CUT`, `PRODUCT_CUT` 등 컷 유형 |
| `generationMode` | X | 현재는 `PARALLEL` 사용 |
| `prompt` | O | 최종 생성 프롬프트 |
| `negativePrompt` | X | 금지 프롬프트 기록용 |
| `userOptionsJson` | X | 사용자 선택 옵션 JSON 문자열 |
| `referenceJson` | X | 참조 이미지 정보 JSON 문자열 |
| `requestedCount` | X | 생성 요청 수. 현재 테스트 단계에서는 1~2 |

### Expected 201 Response

```json
{
  "jobId": 1,
  "status": "SUCCEEDED",
  "cutType": "MODEL_CUT",
  "generationMode": "PARALLEL",
  "aiName": "gemini-2.5-flash-image",
  "requestedCount": 2,
  "outputCount": 2,
  "latencyMs": 12000,
  "errorMessage": null,
  "results": [
    {
      "resultId": 1,
      "assetId": 10,
      "outputIndex": 0,
      "status": "GENERATED",
      "imageUrl": "https://storage.googleapis.com/..."
    },
    {
      "resultId": 2,
      "assetId": 11,
      "outputIndex": 1,
      "status": "GENERATED",
      "imageUrl": "https://storage.googleapis.com/..."
    }
  ]
}
```

## 3. 이미지 생성 결과 조회

### API

| 기능 | Method | Endpoint |
|---|---:|---|
| 이미지 생성 결과 조회 | GET | `/image-generation/jobs/{jobId}` |

### Request

```http
GET http://localhost:8080/image-generation/jobs/1
Cookie: accessToken={accessToken}
```

### 확인

- `status`가 `SUCCEEDED`인지 확인한다.
- `outputCount`가 요청한 `requestedCount`와 같은지 확인한다.
- `results[].assetId`가 존재하는지 확인한다.
- `results[].imageUrl`을 브라우저에서 열었을 때 이미지가 보이는지 확인한다.

## 4. 라이브러리 저장 확인

현재 생성 결과는 `assets` 테이블에 자동 저장된다.

### DB 확인

`assets` 테이블에서 아래 조건으로 확인한다.

```sql
select
    id,
    product_id,
    type,
    status,
    file_url,
    object_name,
    prompt,
    created_at
from assets
where product_id = {productId}
order by id desc;
```

기대값:

- `type = GENERATED`
- `status = GENERATED`
- `product_id`가 요청한 상품 ID와 동일
- `object_name`이 `generated/{productId}/{jobId}/...` 형태
- `prompt`에 요청 프롬프트 저장

### 이미지 생성 결과 테이블 확인

```sql
select
    r.id,
    r.job_id,
    r.asset_id,
    r.output_index,
    r.status,
    r.created_at
from image_generation_results r
where r.job_id = {jobId}
order by r.output_index asc;
```

기대값:

- `asset_id`가 `assets.id`와 연결됨
- `output_index`가 `0`, `1` 순서로 저장됨
- `status = GENERATED`

### 이미지 생성 요청 테이블 확인

```sql
select
    id,
    brand_id,
    product_id,
    brand_mood_id,
    user_id,
    cut_type,
    generation_mode,
    ai_name,
    status,
    requested_count,
    output_count,
    latency_ms,
    error_message,
    created_at,
    updated_at
from image_generation_jobs
where id = {jobId};
```

기대값:

- `status = SUCCEEDED`
- `requested_count = 2`
- `output_count = 2`
- `ai_name = gemini-2.5-flash-image`
- `latency_ms`가 기록됨

## 5. 실패 케이스 테스트

실패 케이스는 실제 생성 비용이 발생하지 않는 값 위주로 먼저 확인한다.

### 인증 없이 요청

```http
POST http://localhost:8080/image-generation/jobs
Content-Type: application/json
```

기대값:

- `401 Unauthorized`

### requestedCount 3 요청

```json
{
  "brandId": 1,
  "productId": 1,
  "brandMoodId": 1,
  "cutType": "MODEL_CUT",
  "prompt": "test",
  "requestedCount": 3
}
```

기대값:

- `400 Bad Request`
- 현재 테스트 단계에서는 최대 2장까지만 허용

### 존재하지 않는 상품 ID

```json
{
  "brandId": 1,
  "productId": 999999,
  "brandMoodId": 1,
  "cutType": "MODEL_CUT",
  "prompt": "test",
  "requestedCount": 1
}
```

기대값:

- `400 Bad Request`
- 상품을 찾을 수 없다는 에러

### GOOGLE_AI_API_KEY 없이 생성 요청

서버 환경변수에서 `GOOGLE_AI_API_KEY`를 제거하고 생성 요청을 보낸다.

기대값:

- 이미지 생성 호출 전에 설정 오류 발생
- `image_generation_jobs.status = FAILED`
- `error_message`에 API key 미설정 메시지 저장

## 6. 비용 주의

아래 요청은 실제 Google AI 이미지 생성 API를 호출하므로 비용이 발생할 수 있다.

- `POST /image-generation/jobs`

아래 요청은 생성 API를 호출하지 않는다.

- `GET /image-generation/jobs/{jobId}`
- DB 조회
- 로그인

## 7. 이번 구현 API 목록

| 기능명 | Method | Endpoint | 설명 |
|---|---:|---|---|
| 이미지 생성 요청 | POST | `/image-generation/jobs` | Google AI 이미지 생성 요청, GCS 자동 저장, assets 자동 저장 |
| 이미지 생성 결과 조회 | GET | `/image-generation/jobs/{jobId}` | 생성 Job 상태와 결과 이미지 signed URL 조회 |
