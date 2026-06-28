# Archive Nexus 브랜드 가이드

Archive Nexus는 ArchiveOS와 동일한 공식 `A` 마크를 사용하고, 가로형 로고에서 `NEXUS`를 청록색으로 구분합니다.

## 자산

- `archive-nexus-mark.svg` — 사이드바, 헤더, 앱 아이콘 등 정사각형 영역용 마크
- `archive-nexus-lockup.svg` — GitHub README와 제품 소개 영역용 가로형 로고
- `../../frontend/public/archive-nexus-mark.svg` — 실제 프론트엔드 UI에서 사용하는 마크
- `../../frontend/public/icon.svg` — 브라우저 탭 아이콘

모든 자산은 동일한 공식 마크 형상을 사용합니다. ArchiveOS와 Archive Nexus 사이에서 마크 자체를 다르게 변형하지 않습니다.

## 핵심 규칙

- 배경은 순수 검정 `#000000`을 사용합니다.
- `A`는 흰색에서 밝은 회색으로 이어지는 매우 약한 명도 변화만 허용합니다.
- 청록색 노드는 한 개만 사용합니다.
- 노드 오른쪽 연결선과 아래쪽 구조를 공식 형상 그대로 유지합니다.
- `ARCHIVE`와 `NEXUS` 사이에 충분한 간격을 유지해 `E`와 `N`이 겹치지 않게 합니다.
- 비율을 찌그러뜨리거나 임의로 회전하지 않습니다.
- 외곽 발광, 과한 그림자, 3D 돌출 효과를 추가하지 않습니다.

## 사용 예시

```html
<img src="docs/brand/archive-nexus-lockup.svg" width="760" alt="Archive Nexus" />
```

```html
<img src="/archive-nexus-mark.svg" width="32" height="32" alt="" aria-hidden="true" />
```

## 색상

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| Background | `#000000` | 로고 배경 |
| Primary | `#FFFFFF` | A와 ARCHIVE 워드마크 |
| Primary Shadow | `#ECECEC` | A 하단의 미세한 명도 변화 |
| Cyan Start | `#12F2F5` | 노드 상단 |
| Cyan End | `#00DDE8` | 노드 하단 |
| UI Surface | `#0F1214` | 제조 관제 UI 카드 및 패널 |

## 슬로건

> Intelligence. Automation. Convergence.

`NEXUS`의 청록색 워드마크가 제품을 구분하고, 공통 `A` 마크가 ArchiveOS 제품군의 시각적 일관성을 유지합니다.
