# 이미지 생성/업로드 테스트 가이드

이 문서는 Postman으로 아래 기능을 테스트하기 위한 가이드다.

- 상품 앞면/뒷면/코디 이미지 업로드
- 업로드 이미지 조회
- 브랜드 무드 분석/저장/조회
- 추천 모델/포즈/배경 이미지 조회
- 이미지 생성 요청
- 생성 결과 자동 저장 확인

> 비용 주의: `POST /image-generation/jobs`만 실제 이미지 생성 API를 호출하므로 비용이 발생할 수 있다.

## 0. 테스트 전 준비

### 서버 주소

로컬:

```text
http://localhost:8080
```

배포 서버:

```text
https://api.aven-studio.store
```

아래 예시는 로컬 기준으로 작성했다. 배포 서버에서 테스트할 때는 host만 바꾸면 된다.

### 인증 방식

이 서버는 `Authorization: Bearer` 헤더가 아니라 `accessToken` 쿠키를 사용한다.

로그인 후 Postman 쿠키 저장소에 `accessToken`이 들어가 있어야 한다.

### DB 선반영 SQL

`asset_role` 컬럼이 아직 Supabase에 없다면 먼저 실행한다.

```sql
alter table assets
    add column if not exists asset_role varchar(30);

update assets
set asset_role = case
    when type = 'GENERATED' then 'GENERATED_RESULT'
    when type = 'IMAGE' then 'PRODUCT_IMAGE'
    else asset_role
end
where asset_role is null;
```

### 테스트에 필요한 데이터

아래 값은 실제 DB에 존재해야 한다.

| 값 | 설명 |
| --- | --- |
| `brandId` | 로그인 사용자의 브랜드 ID |
| `productId` | 해당 브랜드에 속한 상품 ID |
| `brandMoodId` | 저장된 브랜드 무드 ID |

로그인 사용자의 브랜드와 요청의 `brandId`, `productId` 소속 브랜드가 맞지 않으면 실패한다.

## 1. 로그인

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `POST` | `/auth/login` | 없음 |

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

응답 헤더의 `Set-Cookie`에 `accessToken`이 있는지 확인한다.

이후 요청에는 아래 쿠키가 포함되어야 한다.

```http
Cookie: accessToken={accessToken}
```

## 2. 상품 이미지 업로드

모델컷 생성에 필요한 상품 앞면, 뒷면, 코디 이미지를 업로드한다.

업로드된 이미지는 GCS에 저장되고, `assets` 테이블에 저장된다. 다음에 같은 상품을 선택하면 `GET /assets/products/{productId}/images`로 다시 조회할 수 있다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `POST` | `/assets/products/{productId}/images` | 이미지 생성 비용 없음 |

### Request

```http
POST http://localhost:8080/assets/products/1/images
Content-Type: multipart/form-data
Cookie: accessToken={accessToken}
```

Postman `Body > form-data`:

| Key | Type | 예시 Value |
| --- | --- | --- |
| `files` | File | front.png |
| `roles` | Text | PRODUCT_FRONT |
| `files` | File | back.png |
| `roles` | Text | PRODUCT_BACK |

`files`와 `roles`는 같은 순서로 매칭된다.

예시:

- 첫 번째 `files` = `front.png`
- 첫 번째 `roles` = `PRODUCT_FRONT`
- 두 번째 `files` = `back.png`
- 두 번째 `roles` = `PRODUCT_BACK`

### role 값

| role | 의미 |
| --- | --- |
| `PRODUCT_FRONT` | 상품 앞면 |
| `PRODUCT_BACK` | 상품 뒷면 |
| `COORDINATION_ITEM` | 코디용 하의, 신발 등 |
| `DETAIL` | 디테일 이미지 |
| `PRODUCT_IMAGE` | 역할 미분류 상품 이미지 |

### Expected Response

Status: `201 Created`

```json
[
  {
    "assetId": 101,
    "assetRole": "PRODUCT_FRONT",
    "imageUrl": "https://storage.googleapis.com/...",
    "objectName": "products/1/...",
    "originalFilename": "front.png",
    "contentType": "image/png",
    "size": 123456
  },
  {
    "assetId": 102,
    "assetRole": "PRODUCT_BACK",
    "imageUrl": "https://storage.googleapis.com/...",
    "objectName": "products/1/...",
    "originalFilename": "back.png",
    "contentType": "image/png",
    "size": 123456
  }
]
```

### 다음 단계에서 사용할 값

이미지 생성 요청의 `referenceJson.productImages`에 `assetId`를 넣는다.

```json
{
  "productImages": [101, 102]
}
```

## 3. 상품 이미지 조회

상품을 다시 선택했을 때 기존 업로드 이미지가 계속 보이는지 확인한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `GET` | `/assets/products/{productId}/images` | 없음 |

### Request

```http
GET http://localhost:8080/assets/products/1/images
Cookie: accessToken={accessToken}
```

### Expected Response

Status: `200 OK`

```json
[
  {
    "assetId": 102,
    "assetRole": "PRODUCT_BACK",
    "imageUrl": "https://signed-url...",
    "objectName": "products/1/...",
    "originalFilename": "back.png",
    "contentType": "image/png",
    "size": 123456
  },
  {
    "assetId": 101,
    "assetRole": "PRODUCT_FRONT",
    "imageUrl": "https://signed-url...",
    "objectName": "products/1/...",
    "originalFilename": "front.png",
    "contentType": "image/png",
    "size": 123456
  }
]
```

### 확인할 것

- `assetRole`이 업로드한 값과 맞는지 확인
- `imageUrl`을 브라우저에서 열었을 때 이미지가 보이는지 확인
- 같은 상품을 다시 조회해도 업로드한 이미지가 남아 있는지 확인

## 4. 브랜드 무드 분석

브랜드 무드 분석은 이미지 생성 비용이 발생하지 않는다. Claude API 비용은 발생할 수 있다.

분석 API는 DB에 저장하지 않는다. 사용자에게 보여준 뒤 저장 API를 따로 호출해야 한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `POST` | `/brands/{brandId}/moods/analyze` | 이미지 생성 비용 없음 |

### Request

```http
POST http://localhost:8080/brands/1/moods/analyze
Content-Type: application/json
Cookie: accessToken={accessToken}
```

```json
{
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "sourceText": "POLYTERU는 동북아시아 패션 문화와 복식사의 다양한 장르에서 영감을 받아 정교한 패턴과 우수한 소재로 현대적 일상복을 선보이는 미니멀 설계 레이블입니다.",
  "customInstruction": "이미지 생성에 필요한 시각 무드 중심으로 분석해주세요."
}
```

### Expected Response

Status: `200 OK`

```json
{
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "brandSummary": "동북아시아 패션 문화와 복식사에서 영감을 받은 미니멀 설계 레이블",
  "designPhilosophy": "정교한 패턴과 우수한 소재로 현대적 일상복을 전개",
  "brandTone": ["차분함", "절제됨", "정교함"],
  "colorTemperature": "뉴트럴",
  "saturation": "낮음",
  "contrast": "낮음",
  "lighting": "자연광, 부드러운 조명",
  "surfaceTexture": "자연스러운 원단감, 정교한 봉제 디테일",
  "backgroundMood": "미니멀하고 차분한 배경",
  "composition": "절제된 실루엣을 강조하는 구도",
  "primaryColors": ["뉴트럴 톤"],
  "avoidElements": ["화려한 색상", "과도한 장식"],
  "customBrandNote": "원단의 질감과 봉제 정교함이 돋보이는 조명과 구성 필요",
  "rawAnalysisJson": "{...}"
}
```

## 5. 브랜드 무드 저장

분석 결과를 사용자가 확인하거나 수정한 뒤 DB에 저장한다.

이미지 생성 요청에는 여기서 저장된 `brandMoodId`가 필요하다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `POST` | `/brands/{brandId}/moods` | 없음 |

### Request

```http
POST http://localhost:8080/brands/1/moods
Content-Type: application/json
Cookie: accessToken={accessToken}
```

```json
{
  "name": "POLYTERU 기본 무드",
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "brandSummary": "동북아시아 패션 문화와 복식사에서 영감을 받은 미니멀 설계 레이블",
  "designPhilosophy": "정교한 패턴과 우수한 소재로 현대적 일상복을 전개",
  "brandTone": ["차분함", "절제됨", "정교함"],
  "colorTemperature": "뉴트럴",
  "saturation": "낮음",
  "contrast": "낮음",
  "lighting": "자연광, 부드러운 조명",
  "surfaceTexture": "자연스러운 원단감, 정교한 봉제 디테일",
  "backgroundMood": "미니멀하고 차분한 배경",
  "composition": "절제된 실루엣을 강조하는 구도",
  "primaryColors": ["뉴트럴 톤"],
  "avoidElements": ["화려한 색상", "과도한 장식"],
  "customBrandNote": "원단의 질감과 봉제 정교함이 돋보이는 조명과 구성 필요",
  "brandInstruction": "[BRAND MOOD]\n브랜드 요약: 동북아시아 패션 문화와 복식사에서 영감을 받은 미니멀 설계 레이블\n브랜드 톤: 차분함, 절제됨, 정교함\n",
  "rawAnalysisJson": "{}"
}
```

### Expected Response

Status: `201 Created`

```json
{
  "brandMoodId": 3,
  "brandId": 1,
  "name": "POLYTERU 기본 무드",
  "version": 1,
  "status": "ACTIVE",
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "brandInstruction": "[BRAND MOOD]\n..."
}
```

### 다음 단계에서 사용할 값

이미지 생성 요청의 `brandMoodId`에 응답의 `brandMoodId`를 넣는다.

## 6. 추천 모델/포즈/배경 이미지 조회

서비스가 미리 제공하는 모델, 포즈, 배경 이미지를 조회한다.

여기서 받은 `referenceAssetId`를 이미지 생성 요청에 사용한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `GET` | `/image-generation/reference-assets?assetType=MODEL` | 없음 |
| `GET` | `/image-generation/reference-assets?assetType=POSE` | 없음 |
| `GET` | `/image-generation/reference-assets?assetType=BACKGROUND` | 없음 |

### Request

```http
GET http://localhost:8080/image-generation/reference-assets?assetType=MODEL
Cookie: accessToken={accessToken}
```

```http
GET http://localhost:8080/image-generation/reference-assets?assetType=POSE
Cookie: accessToken={accessToken}
```

```http
GET http://localhost:8080/image-generation/reference-assets?assetType=BACKGROUND
Cookie: accessToken={accessToken}
```

### Expected Response

```json
{
  "assetType": "MODEL",
  "referenceAssets": [
    {
      "referenceAssetId": 1,
      "key": "model_001",
      "assetType": "MODEL",
      "label": "추천 모델 1",
      "description": "차분한 인상의 모델",
      "imageUrl": "https://..."
    }
  ]
}
```

### 다음 단계에서 사용할 값

이미지 생성 요청의 `referenceJson`에 아래 값을 넣는다.

```json
{
  "modelReferenceId": 1,
  "poseReferenceId": 2,
  "backgroundReferenceId": 3
}
```

## 7. 이미지 생성 옵션 조회

색온도, 카메라 각도, 구도, 비율, 출력 개수 옵션을 조회한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `GET` | `/image-generation/options` | 없음 |

### Request

```http
GET http://localhost:8080/image-generation/options
Cookie: accessToken={accessToken}
```

### 확인

응답의 `key` 값을 이미지 생성 요청의 `userOptionsJson`에 넣는다.

예:

```json
{
  "colorTemperature": "COOL",
  "cameraAngle": "EYE_LEVEL_FRONT",
  "framing": "FULL_BODY",
  "aspectRatio": "4:5"
}
```

## 8. 이미지 생성 요청

> 비용 주의: 이 API는 실제 Google AI 이미지 생성 API를 호출한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `POST` | `/image-generation/jobs` | Google AI 이미지 생성 비용 발생 |

### Request

```http
POST http://localhost:8080/image-generation/jobs
Content-Type: application/json
Cookie: accessToken={accessToken}
```

`referenceJson`과 `userOptionsJson`은 현재 객체가 아니라 JSON 문자열로 보내야 한다.

```json
{
  "brandId": 1,
  "productId": 1,
  "brandMoodId": 3,
  "cutType": "MODEL_CUT",
  "generationMode": "PARALLEL",
  "prompt": "프리미엄 패션 이커머스 모델컷을 생성해줘. 상품의 색상과 형태는 유지해줘.",
  "negativePrompt": "cheap ecommerce, neon, watermark, text overlay, collage",
  "userOptionsJson": "{\"colorTemperature\":\"COOL\",\"cameraAngle\":\"EYE_LEVEL_FRONT\",\"framing\":\"FULL_BODY\",\"aspectRatio\":\"4:5\"}",
  "referenceJson": "{\"productImages\":[101,102],\"modelReferenceId\":1,\"poseReferenceId\":2,\"backgroundReferenceId\":3}",
  "requestedCount": 1
}
```

### 필드 설명

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `brandId` | Y | 브랜드 ID |
| `productId` | Y | 상품 ID |
| `brandMoodId` | Y | 저장된 브랜드 무드 ID |
| `cutType` | Y | `MODEL_CUT` 또는 `PRODUCT_CUT` |
| `generationMode` | N | 현재는 `PARALLEL` 사용 |
| `prompt` | Y | 사용자 추가 요청 |
| `negativePrompt` | N | 제외할 요소 |
| `userOptionsJson` | N | 사용자 선택 옵션 JSON 문자열 |
| `referenceJson` | N | 참조 이미지 정보 JSON 문자열 |
| `requestedCount` | N | 생성 이미지 수. 1~4 |

### referenceJson 설명

```json
{
  "productImages": [101, 102],
  "modelReferenceId": 1,
  "poseReferenceId": 2,
  "backgroundReferenceId": 3
}
```

| 필드 | 넣는 값 |
| --- | --- |
| `productImages` | 상품 이미지 업로드 API 응답의 `assetId` 배열 |
| `modelReferenceId` | 추천 모델 조회 API 응답의 `referenceAssetId` |
| `poseReferenceId` | 추천 포즈 조회 API 응답의 `referenceAssetId` |
| `backgroundReferenceId` | 추천 배경 조회 API 응답의 `referenceAssetId` |

### Expected Response

Status: `201 Created`

```json
{
  "jobId": 14,
  "status": "SUCCEEDED",
  "cutType": "MODEL_CUT",
  "generationMode": "PARALLEL",
  "aiName": "gemini-2.5-flash-image",
  "requestedCount": 1,
  "outputCount": 1,
  "latencyMs": 17386,
  "errorMessage": null,
  "results": [
    {
      "resultId": 8,
      "assetId": 14,
      "outputIndex": 0,
      "status": "GENERATED",
      "imageUrl": "https://signed-url...",
      "imageBase64": null
    }
  ]
}
```

### 확인할 것

- `status = SUCCEEDED`
- `outputCount = requestedCount`
- `results[].assetId` 존재
- `results[].imageUrl`을 열었을 때 이미지가 보임

## 9. 이미지 생성 결과 조회

이미 생성된 job의 상태와 결과 이미지를 다시 조회한다.

### API

| Method | Endpoint | 비용 |
| --- | --- | --- |
| `GET` | `/image-generation/jobs/{jobId}` | 없음 |

### Request

```http
GET http://localhost:8080/image-generation/jobs/14
Cookie: accessToken={accessToken}
```

### Expected Response

`POST /image-generation/jobs` 응답과 같은 구조다.

## 10. DB 확인

### 업로드 이미지 확인

```sql
select
    id,
    product_id,
    type,
    asset_role,
    status,
    object_name,
    original_filename,
    created_at
from assets
where product_id = {productId}
order by id desc;
```

기대값:

- 업로드 이미지: `type = IMAGE`
- 앞면 이미지: `asset_role = PRODUCT_FRONT`
- 뒷면 이미지: `asset_role = PRODUCT_BACK`

### 생성 이미지 자동 저장 확인

```sql
select
    id,
    product_id,
    type,
    asset_role,
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

- 생성 이미지: `type = GENERATED`
- 생성 이미지 역할: `asset_role = GENERATED_RESULT`
- `status = GENERATED`
- `object_name`이 `generated/{productId}/{jobId}/...` 형태
- `prompt`에 브랜드 무드와 사용자 요청이 결합된 최종 프롬프트 저장

### 이미지 생성 job 확인

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

- 성공 시 `status = SUCCEEDED`
- 실패 시 `status = FAILED`, `error_message`에 실패 원인 저장
- `requested_count`와 `output_count` 확인

### 이미지 생성 결과 확인

```sql
select
    id,
    job_id,
    asset_id,
    output_index,
    status,
    created_at
from image_generation_results
where job_id = {jobId}
order by output_index asc;
```

기대값:

- `asset_id`가 `assets.id`와 연결됨
- `output_index`가 0부터 순서대로 저장됨
- `status = GENERATED`

## 11. 실패 케이스

### 인증 없이 요청

```http
GET http://localhost:8080/assets/products/1/images
```

기대값:

- `401 Unauthorized`

### files와 roles 개수 불일치

예:

- `files` 2개
- `roles` 1개

기대값:

- `400 Bad Request`
- 메시지: `이미지 파일 수와 이미지 역할 수가 일치해야 합니다.`

### requestedCount 5 요청

```json
{
  "brandId": 1,
  "productId": 1,
  "brandMoodId": 3,
  "cutType": "MODEL_CUT",
  "prompt": "test",
  "requestedCount": 5
}
```

기대값:

- `400 Bad Request`
- 메시지: `must be less than or equal to 4`

### 로그인 사용자 브랜드와 요청 브랜드 불일치

기대값:

- `400 Bad Request`
- 메시지: `로그인 사용자의 브랜드와 요청 브랜드가 일치하지 않습니다.`

## 12. 비용 발생 여부 정리

| API | 비용 |
| --- | --- |
| `POST /image-generation/jobs` | Google AI 이미지 생성 비용 발생 |
| `POST /brands/{brandId}/moods/analyze` | Claude API 비용 발생 가능 |
| `POST /brands/{brandId}/moods/analyze/pdf` | Claude API 비용 발생 가능 |
| `POST /assets/products/{productId}/images` | 이미지 생성 비용 없음 |
| `GET /assets/products/{productId}/images` | 없음 |
| `GET /image-generation/reference-assets` | 없음 |
| `GET /image-generation/options` | 없음 |
| `GET /image-generation/jobs/{jobId}` | 없음 |

