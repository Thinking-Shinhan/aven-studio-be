# Brand Mood Analysis Test Guide

## 목적

- 브랜드 자료 텍스트를 Claude Haiku 4.5로 분석해 사용자 확인용 무드 JSON을 반환한다.
- 분석 결과는 바로 저장하지 않는다.
- 사용자가 확인/수정한 결과를 별도 저장 API로 `brand_moods`에 저장한다.

> 주의: `POST /brands/{brandId}/moods/analyze`는 실제 Claude API 호출 비용이 발생한다.

## 사전 조건

- 서버 환경변수
  - `ANTHROPIC_API_KEY`
  - `ANTHROPIC_MODEL`, 기본값: `claude-haiku-4-5`
- 로그인 쿠키
  - `Cookie: accessToken={accessToken}`
- 로그인 사용자의 `brand_id`와 path의 `{brandId}`가 같아야 한다.

## 추가/변경 API

| API 이름 | Method | Endpoint | 설명 |
|---|---:|---|---|
| 브랜드 무드 분석 | POST | `/brands/{brandId}/moods/analyze` | Claude로 브랜드 무드 분석 JSON 반환. DB 저장 없음 |
| 브랜드 무드 저장 | POST | `/brands/{brandId}/moods` | 사용자가 확인/수정한 무드 결과 저장 |
| 브랜드 무드 단건 조회 | GET | `/brands/{brandId}/moods/{brandMoodId}` | 저장된 무드 조회 |

## 1. 브랜드 무드 분석

```http
POST https://api.aven-studio.store/brands/{brandId}/moods/analyze
Content-Type: application/json
Cookie: accessToken={accessToken}
```

```json
{
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "sourceText": "POLYTERU는 동북아시아 패션 문화와 복식사의 다양한 장르에서 영감을 받아 정교한 패턴과 우수한 소재로 현대적 일상복을 선보이는 미니멀 설계 레이블입니다. 컬렉션은 차분한 뉴트럴 톤, 절제된 실루엣, 자연스러운 원단감, 단정한 봉제 디테일을 중심으로 전개됩니다.",
  "customInstruction": "이미지 생성에 필요한 시각 무드 중심으로 분석해주세요."
}
```

### Expected 200 Response

```json
{
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "brandSummary": "동북아시아 패션 문화와 복식사의 다양한 장르에서 영감을 받은 미니멀 설계 레이블",
  "designPhilosophy": "정교한 패턴과 우수한 소재로 현대적 일상복을 제안한다",
  "brandTone": ["정제됨", "차분함", "미니멀"],
  "colorTemperature": "뉴트럴",
  "saturation": "낮음",
  "contrast": "중간",
  "lighting": "부드러운 자연광",
  "surfaceTexture": "자연스러운 원단감과 단정한 봉제감",
  "backgroundMood": "미니멀 에디토리얼",
  "composition": "깔끔한 중앙 구도",
  "primaryColors": ["뉴트럴 톤"],
  "avoidElements": ["과한 장식성", "저가형 쇼핑몰 느낌"],
  "customBrandNote": "자료가 부족한 항목은 추가 룩북 또는 상세페이지 자료로 보완할 수 있습니다.",
  "rawAnalysisJson": "{...}"
}
```

## 2. 브랜드 무드 저장

분석 응답을 프론트에서 사용자에게 보여주고, 사용자가 수정한 최종 값을 저장한다.

```http
POST https://api.aven-studio.store/brands/{brandId}/moods
Content-Type: application/json
Cookie: accessToken={accessToken}
```

```json
{
  "name": "POLYTERU 기본 무드",
  "sourceType": "TEXT",
  "sourceUrl": "https://polyteru-store.com",
  "brandSummary": "동북아시아 패션 문화와 복식사의 다양한 장르에서 영감을 받은 미니멀 설계 레이블",
  "designPhilosophy": "정교한 패턴과 우수한 소재로 현대적 일상복을 제안한다",
  "brandTone": ["정제됨", "차분함", "미니멀"],
  "colorTemperature": "뉴트럴",
  "saturation": "낮음",
  "contrast": "중간",
  "lighting": "부드러운 자연광",
  "surfaceTexture": "자연스러운 원단감과 단정한 봉제감",
  "backgroundMood": "미니멀 에디토리얼",
  "composition": "깔끔한 중앙 구도",
  "primaryColors": ["뉴트럴 톤"],
  "avoidElements": ["과한 장식성", "저가형 쇼핑몰 느낌"],
  "customBrandNote": "사용자가 확인 후 저장한 무드입니다.",
  "rawAnalysisJson": "{}"
}
```

### Expected 201 Response

```json
{
  "brandMoodId": 2,
  "brandId": 1,
  "name": "POLYTERU 기본 무드",
  "version": 2,
  "status": "ACTIVE",
  "brandSummary": "...",
  "brandTone": ["정제됨", "차분함", "미니멀"],
  "colorTemperature": "뉴트럴",
  "avoidElements": ["과한 장식성", "저가형 쇼핑몰 느낌"],
  "brandInstruction": "[BRAND MOOD]..."
}
```

## 3. 브랜드 무드 조회

```http
GET https://api.aven-studio.store/brands/{brandId}/moods/{brandMoodId}
Cookie: accessToken={accessToken}
```

기대값:

- `brandMoodId`가 저장 응답과 같음
- `brandTone`, `primaryColors`, `avoidElements`가 배열로 반환됨
- `brandInstruction`이 생성되어 있음

## DB 확인

```sql
select
  id,
  user_id,
  brand_id,
  name,
  version,
  status,
  source_type,
  source_url,
  tone_keywords,
  mood_attributes,
  color_palette,
  negative_keywords,
  brand_instruction,
  created_at,
  updated_at
from brand_moods
order by id desc
limit 5;
```

성공 기준:

- 새 row가 생성됨
- `version`이 브랜드별로 증가함
- `tone_keywords`, `mood_attributes`, `color_palette`, `negative_keywords`가 JSONB로 저장됨
- `brand_instruction`이 비어 있지 않음
