# Image Generation API Test Guide

## 1. 서버 실행 전 확인

현재 `application.yml` 기준으로 로컬 서버 실행에는 아래 환경변수가 필요하다.

```bash
export DB_USERNAME=...
export DB_PASSWORD=...
export JWT_SECRET=...
```

`JWT_SECRET`은 HS256 서명에 사용되므로 최소 32바이트 이상 문자열을 사용한다.

```bash
./gradlew bootRun
```

서버 기본 주소:

```text
http://localhost:8080
```

현재 `/auth/**`, `/error` 외 API는 인증이 필요하다.

## 2. 이번에 추가한 API

| API 문서 기능명 | Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- | --- |
| 이미지 생성 옵션 조회 | GET | `/image-generation/options` | 모델컷/제품컷 공통 선택 옵션 조회 | 필요 |
| 제품컷/모델컷 샷 템플릿 조회 | GET | `/image-generation/shot-templates?cutType=PRODUCT_CUT` | 컷 유형별 샷 템플릿 목록 조회 | 필요 |
| 레퍼런스 에셋 선택지 조회 | GET | `/image-generation/reference-assets?assetType=MODEL` | 모델/배경/포즈 레퍼런스 선택지 조회 | 필요 |

## 3. 인증 테스트

### 3.1 회원가입

```http
POST /auth/register
Content-Type: application/json
```

```json
{
  "loginId": "testuser1",
  "name": "테스트사용자",
  "password": "Test1234!",
  "passwordConfirm": "Test1234!",
  "brandName": "AVEN"
}
```

성공 기준:

```text
201 Created
```

### 3.2 로그인

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "loginId": "testuser1",
  "password": "Test1234!"
}
```

성공 기준:

```text
200 OK
Set-Cookie: accessToken=...
Set-Cookie: refreshToken=...
```

Postman에서는 로그인 응답 후 Cookies에 `accessToken`, `refreshToken`이 저장되는지 확인한다.

## 4. 이미지 생성 옵션 API 테스트

### 4.1 이미지 생성 옵션 조회

```http
GET /image-generation/options
Cookie: accessToken=...
```

확인할 값:

- `cutTypes`: `MODEL_CUT`, `PRODUCT_CUT`
- `colorTemperatures`: `WARM`, `NEUTRAL`, `COOL`
- `cameraAngles`: `EYE_LEVEL_FRONT`, `LEFT_THREE_QUARTER_45`, `RIGHT_THREE_QUARTER_45`, `BACK_VIEW`
- `framings`: `FULL_BODY`, `THREE_QUARTER_BODY`, `HALF_BODY`, `CLOSE_UP`, `WIDE_MARGIN`
- `aspectRatios`: `RATIO_1_1`, `RATIO_2_3`, `RATIO_3_4`, `RATIO_4_5`, `RATIO_16_9`, `RATIO_9_16`
- `outputCounts`: `1`, `2`, `3`, `4`

### 4.2 제품컷 샷 템플릿 조회

```http
GET /image-generation/shot-templates?cutType=PRODUCT_CUT
Cookie: accessToken=...
```

확인할 대표 값:

- `FRONT`
- `BACK`
- `SIDE`
- `THREE_QUARTER`
- `FLAT_LAY`
- `HANGING`
- `TOP_VIEW`
- `UNDER_BRIM`
- `DETAIL`
- `LOGO`
- `FABRIC`
- `CONSTRUCTION`

### 4.3 모델컷 샷 템플릿 조회

```http
GET /image-generation/shot-templates?cutType=MODEL_CUT
Cookie: accessToken=...
```

확인할 대표 값:

- `FRONT_STANDING`
- `SIDE_STANDING`
- `BACK_STANDING`
- `WALKING`
- `SITTING`

### 4.4 레퍼런스 에셋 선택지 조회

```http
GET /image-generation/reference-assets
Cookie: accessToken=...
```

타입별 조회:

```http
GET /image-generation/reference-assets?assetType=MODEL
GET /image-generation/reference-assets?assetType=BACKGROUND
GET /image-generation/reference-assets?assetType=POSE
```

현재 `imageUrl`은 GCS/DB 레퍼런스 에셋 연동 전이라 `null`일 수 있다.

## 5. 기존 상품 API 테스트

### 5.1 상품 목록 조회

```http
GET /products?page=0&size=20
Cookie: accessToken=...
```

필터 예시:

```http
GET /products?category=상의&page=0&size=20
GET /products?category=하의&gender=F&page=0&size=20
GET /products?keyword=shirt&page=0&size=20
```

현재 카테고리 매핑:

- `상의` -> `상의`
- `하의` -> `바지`, `치마`
- `셋업` -> `셋업`
- `악세서리` -> `모자`, `가방`

## 6. 기존 GCS 상품 이미지 API 테스트

현재 GCS API는 테스트 컨트롤러로 열려 있다.

| 기능명 | Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- | --- |
| 상품 이미지 업로드 테스트 | POST | `/test/assets/{productId}` | 상품 이미지 여러 장 GCS 업로드 | 필요 |
| 상품 이미지 signed URL 조회 | GET | `/test/assets/products/{productId}` | 상품 이미지 signed URL 목록 조회 | 필요 |
| 상품 이미지 삭제 테스트 | DELETE | `/test/assets/{assetId}` | GCS 객체 삭제 및 asset 삭제 | 필요 |

### 6.1 상품 이미지 업로드

```http
POST /test/assets/{productId}
Content-Type: multipart/form-data
Cookie: accessToken=...
```

form-data:

```text
files: image1.jpg
files: image2.png
```

허용 이미지:

- `image/jpeg`
- `image/png`
- `image/webp`

최대 크기:

- 파일 1개: 10MB
- 요청 전체: 50MB

성공 응답 예시:

```json
[
  {
    "assetId": 1,
    "objectName": "products/1/uuid.jpg",
    "originalFilename": "image1.jpg",
    "contentType": "image/jpeg",
    "size": 123456
  }
]
```

### 6.2 상품 이미지 signed URL 조회

```http
GET /test/assets/products/{productId}
Cookie: accessToken=...
```

성공 응답에는 6시간 유효한 signed URL이 포함된다.

```json
[
  {
    "assetId": 1,
    "imageUrl": "https://storage.googleapis.com/...",
    "originalFilename": "image1.jpg",
    "contentType": "image/jpeg",
    "size": 123456
  }
]
```

## 7. 현재 코드 분석 요약

### 7.1 인증

- `/auth/register`: 회원가입
- `/auth/login`: 로그인 후 `accessToken`, `refreshToken`을 HttpOnly Cookie로 내려줌
- `/auth/refresh`: refresh token으로 access token 재발급
- `/auth/logout`: refresh token DB 삭제 및 쿠키 만료
- `/auth/**`, `/error` 외 모든 API는 인증 필요

### 7.2 GCS

- `GcsService.upload()`가 `products/{productId}/{uuid}.{ext}` 경로로 업로드
- DB에는 `fileUrl`, `objectName`, `originalFilename`, `contentType`, `size`, `status`, `product_id` 저장
- 조회 시 `objectName`으로 6시간짜리 signed URL 생성
- 삭제 시 GCS 객체 삭제 후 DB asset 삭제

### 7.3 상품

- `Product`는 `Brand`와 N:1 관계
- 상품 목록은 `category`, `color`, `gender`, `keyword` 필터 지원
- 목록 API는 Pageable 기반

### 7.4 이미지 생성 옵션

- 현재는 DB 없이 서버 코드의 고정 선택지로 제공
- 프론트와 백엔드의 option key를 먼저 맞추기 위한 MVP용 API
- 추후 `shot_templates`, `reference_assets` 테이블과 GCS URL을 연결하면 서비스 로직만 교체 가능

## 8. 다음 개발 순서

1. `reference-assets`를 실제 DB/GCS 레퍼런스 에셋과 연결
2. `brand_moods` 엔티티/API 추가
3. 브랜드 무드 분석 결과 저장/수정 API 추가
4. `image_generation_jobs` 엔티티/API 추가
5. prompt compiler 서비스 추가
6. Nano Banana Pro provider adapter 추가
7. 모델컷 병렬 생성 요청 구현
8. 제품컷 shot template 기반 병렬 생성 요청 구현
