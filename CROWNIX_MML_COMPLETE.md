# Crownix MML 렌더링 완전 스펙 + 원본 소스

`crownix-viewer.min.js` (1.9MB, minified)에서 리버스 엔지니어링한 **XML(MML) → 화면 렌더링** 완전 명세.
이 파일 하나로 동일한 MML 리포트를 다른 언어/환경에서 재현 가능.

---

## PART 1. 스펙 문서


`crownix-viewer.min.js`에서 추출한 **XML(MML) → 화면 렌더링** 핵심 로직 명세입니다.
이 문서 하나면 동일한 MML 리포트를 다른 언어/환경에서 재현할 수 있습니다.

---

## 1. 전체 파이프라인

```
서버 응답 (MML XML 문자열)
        ↓
$.parseXML() 로 XML DOM 파싱
        ↓
<HEAD> 파싱 → 폰트/속성 테이블 만들기 (parseHead)
        ↓
<PG> 각 페이지 순회 → drawPage()
        ↓
페이지 내부 요소를 재귀 순회하며 태그별 draw 함수 호출
        ↓
┌───────────────┬───────────────┐
Canvas 렌더링       DOM 렌더링(절대좌표)
(선/도형/이미지)     (텍스트)
```

**중요**: 텍스트는 Canvas가 아니라 `<div>` 절대좌표로 그림 (검색/복사 가능하도록).
선/사각형/이미지 등은 Canvas에 그림.

---

## 2. MML XML 최상위 구조

```xml
<?xml version="1.0" encoding="utf-8"?>
<MML version="2.9">
  <MRD>report_definition.mrd</MRD>       <!-- 리포트 정의 파일명 -->
  <HEAD>
    <INFORMATION>
      <TITLE>제목</TITLE>
      <MRD>...</MRD>
      <MRDPATH>...</MRDPATH>
      <MRDPARAM>...</MRDPARAM>
      <PGINFO cover="..." startnum="..." pginit="..." exceptpg="..."/>
    </INFORMATION>
    <FONTLIST>
      <FONT id="0" name="맑은 고딕"/>
      <FONT id="1" name="굴림"/>
      ...
    </FONTLIST>
    <TALIST>   <!-- Text Attribute List -->
      <TA id="0" fi="0" pt="100" cl="#000000" cb="0" ci="0" cu="0" cs="0" .../>
      ...
    </TALIST>
    <FALIST>   <!-- Face(Fill) Attribute List -->
      <FA id="0" fc="#FFFFFF" al="1" pt="0"/>
      ...
    </FALIST>
    <LALIST>   <!-- Line Attribute List -->
      <LA id="0" st="0" wd="10" lc="#000000" al="1" ap="0" at="0" as="1" db="0"/>
      ...
    </LALIST>
    <IMLIST>   <!-- Indexed Image List -->
      <IM id="0" va="data:image/png;base64,..."/>
    </IMLIST>
    <VALIST>   <!-- Variable List -->
      <VA key="..."/>텍스트</VA>
    </VALIST>
  </HEAD>
  <PG no="1">                            <!-- 페이지 -->
    <TL to="100" bo="150" le="200" ri="500" tid="0" ha="0">텍스트</TL>
    <LN sx="10" sy="20" ex="100" ey="20" lid="0"/>
    <RA sx="0" sy="0" ex="500" ey="700" lid="0" fid="0"/>
    ...
  </PG>
  <PG no="2">...</PG>
</MML>
```

---

## 3. HEAD 파싱 결과 (모든 요소가 참조하는 스타일 테이블)

### 3.1 FONTLIST → `fontList`
| 속성 | 의미 |
|---|---|
| `id` | 폰트 ID (다른 요소에서 `fi` 로 참조) |
| `name` | 폰트 이름 (예: "맑은 고딕") |

### 3.2 TALIST → `textAttrList[id] = {...}`
텍스트 스타일 프리셋. 각 `<TA>` 속성:
| 속성 | 의미 | 예시 |
|---|---|---|
| `fi` | Font Index (FONTLIST 참조) | "0" |
| `pt` | 폰트 크기 × 10 (10pt → "100") | "100" |
| `cl` | 색상 | "#000000" |
| `cb` | Bold ("1"이면 bold) | "0" |
| `ci` | Italic | "0" |
| `cu` | Underline | "0" |
| `cs` | Strikethrough | "0" |
| `cr` | 반전 (검정 배경 + 흰 글자) | "0" |
| `sc` | 세로 스크립트 관련 | - |
| `ls` | Letter spacing (1/1000 in 단위) | - |
| `ss` | Super/Subscript ("1"=super, "2"=sub) | - |
| `es` | Effect Style (회전 등) | - |

### 3.3 FALIST → `faceAttrList[id] = {...}`
면 채우기 스타일:
| 속성 | 의미 |
|---|---|
| `fc` | Fill Color (기본 "#FFFFFF") |
| `al` | Alpha (0~1, 기본 1) |
| `pt` | Pattern (0=단색, 1~10=패턴) |

### 3.4 LALIST → `lineAttrList[id] = {...}`
선 스타일:
| 속성 | 의미 |
|---|---|
| `st` | Style (**0=solid, 1=dash, 2=dash, 3=dash-dot [10,2,2,2], 4=dash-dot-dot [10,2,2,2,2,2]**) |
| `wd` | Width (선 두께, adjustWidth로 스케일 조정) |
| `lc` | Line Color (기본 "#000000") |
| `al` | Alpha (기본 1) |
| `ap` | Arrow Position (0=없음, 1~9=화살표 조합) |
| `at` | Arrow Type |
| `as` | Arrow Size |
| `db` | Double line 여부 |

### 3.5 IMLIST → `indexedImageList[id] = Image`
`<IM id="0" va="data:image/...;base64,..."/>` — base64 이미지를 미리 로드.

---

## 4. 좌표 변환 (매우 중요!)

MML의 모든 좌표는 **논리 단위**이고, 픽셀로 변환해야 합니다.

```javascript
// 원본 코드 (m2soft.crownix.Painter.adjustCoord)
function adjustCoord(mmlCoord, lineWidth, margin, scale) {
  if (margin == null) margin = 24.3;          // 기본 마진 24.3 pt
  if (!lineWidth) lineWidth = 0;
  
  // MML 좌표는 10.3배 스케일
  let px = Math.floor(mmlCoord / 10.3 + margin) 
         + (lineWidth % 2 === 0 ? 0 : 0.5);   // 홀수 두께면 반픽셀 offset (선명하게)
  
  if (scale && scale != 1) {
    px -= ((px * scale) % 1) / scale;
  }
  return px;
}

// 역변환
function reverseAdjustCoord(pixel) {
  return Math.floor(pixel * 10.3);
}
```

**핵심 상수**: 
- **1 pixel ≈ 10.3 MML units**
- **기본 페이지 margin: 24.3 pixel** (문서 가장자리)

---

## 5. 페이지 그리기 (drawPage) — 태그 디스패치

`<PG>` 안의 모든 자식 요소를 재귀 순회하면서 태그별로 draw 함수를 호출합니다.

### 5.1 컨테이너 태그 (자식이 있으므로 재귀 진입)
```
PR, TB, TE, FC, SO, SG, IL, CA, SL
```
이 태그들은 자체적으로 그리지 않고, 자식으로 다시 내려감.

### 5.2 그리기 태그 → draw 함수 매핑

| 태그 | 의미 | 그리기 함수 | 필요한 속성 |
|---|---|---|---|
| **TL** | Text Line (텍스트) | `drawText` | `le, ri, to, bo, tid, ha, ls, ba, es, nrt, wd` |
| **LN** | Line (선) | `drawLine` | `sx, sy, ex, ey, lid` |
| **RA** | Rectangle | `drawRect` | `sx, sy, ex, ey, lid, fid` |
| **RR** | Rounded Rectangle | `drawRoundeRect` | +`aw, ah, ap, cl` |
| **EP** | Ellipse | `drawEllipse` | 사각형과 동일 |
| **DM** | Diamond | `drawDiamond` | 사각형과 동일 |
| **PL** | Parallelogram | `drawParallelogram` | +`cx` |
| **HH** | Hexahedron | `drawHexaheron` | +`cx, cy` |
| **CY** | Cylinder | `drawCylinder` | +`sh` |
| **CV** | Curve (베지어) | `drawCurve` | +`cx1, cy1, cx2, cy2` |
| **LL** | PolyLine | `drawPolyLine` | `dt` (파이프 구분) |
| **SC** | Scribble (자유곡선) | `drawScribble` | `dt` |
| **PO** | Polygon | `drawPolygon` | `dt` |
| **IM** | Image | `drawImage` | `sx, sy, ex, ey` + 이미지 로드 |
| **FF** | Form Field (입력폼) | `drawFormField` | 텍스트 + 입력 |
| **CT** | Chart | `drawChart` | 자식이 SVG or CC |
| **TG** | Grid | `drawGrid` | 표 |
| **CL, LY** | Layer | `drawLayer` | 하위 페이지 참조 |
| **ERR** | Error | (에러 표시) | |

### 5.3 조건부 스킵
```javascript
var exceptList = element.attr("ect"); // "d"=display 제외, "p"=print 제외
if (exceptList && exceptList.indexOf(currentMedia) > -1) return;
```
`ect` 속성이 현재 매체(d=display/p=print)를 포함하면 그리지 않음.

---

## 6. 개별 draw 함수 원본 코드

### 6.1 drawLine
```javascript
function drawLine(element, lineAttr) {
  if (element.attr("vs") === "0") return;  // 숨김
  const sx = adjustCoord(element.attr("sx"), lineAttr.wd);
  const sy = adjustCoord(element.attr("sy"), lineAttr.wd);
  const ex = adjustCoord(element.attr("ex"), lineAttr.wd);
  const ey = adjustCoord(element.attr("ey"), lineAttr.wd);
  canvas.drawLine(sx, sy, ex, ey, lineAttr);
}
```

### 6.2 drawRect
```javascript
function drawRect(element, lineAttr, faceAttr) {
  const sx = adjustCoord(element.attr("sx"), lineAttr.wd);
  const sy = adjustCoord(element.attr("sy"), lineAttr.wd);
  const ex = adjustCoord(element.attr("ex"), lineAttr.wd);
  const ey = adjustCoord(element.attr("ey"), lineAttr.wd);
  canvas.drawRect(sx, sy, ex, ey, lineAttr, faceAttr);
}
```

### 6.3 drawRoundeRect (라운드 사각형)
```javascript
function drawRoundeRect(element, lineAttr, faceAttr) {
  const sx = adjustCoord(element.attr("sx"), lineAttr.wd);
  const sy = adjustCoord(element.attr("sy"), lineAttr.wd);
  const ex = adjustCoord(element.attr("ex"), lineAttr.wd);
  const ey = adjustCoord(element.attr("ey"), lineAttr.wd);
  const aw = element.attr("aw") / 10.3;   // corner width
  const ah = element.attr("ah") / 10.3;   // corner height
  const ap = element.attr("ap");           // corner bitmask (1=TL, 2=TR, 4=BL, 8=BR)
  canvas.drawRect(sx, sy, ex, ey, lineAttr, faceAttr, aw, ah, ap);
  canvas.setClip(element.attr("cl"));
}
```

### 6.4 drawPolyLine / drawPolygon / drawScribble
```javascript
function drawPolyLine(element, lineAttr) {
  const data = element.attr("dt").split("|");
  const points = data.map(p => {
    const [x, y] = p.split(",");
    return [adjustCoord(x, lineAttr.wd), adjustCoord(y, lineAttr.wd)];
  });
  canvas.drawPolyLine(lineAttr, points);
}
// drawPolygon, drawScribble도 동일 패턴
```

### 6.5 drawImage
```javascript
function drawImage(element, image) {
  const sx = adjustCoord(element.attr("sx"));
  const sy = adjustCoord(element.attr("sy"));
  const ex = adjustCoord(element.attr("ex"));
  const ey = adjustCoord(element.attr("ey"));
  canvas.drawImage(image, sx, sy, ex-sx, ey-sy);
}
```

### 6.6 drawText (가장 복잡, 핵심)

**출력 방식**: Canvas가 아니라 `<div>` 절대좌표로 배치.

```javascript
function drawText(element, fontList, textAttrList) {
  const left   = adjustCoord(element.attr("le"));
  const right  = adjustCoord(element.attr("ri"));
  const top    = adjustCoord(element.attr("to")) - 1;
  const bottom = adjustCoord(element.attr("bo")) + 1;
  
  // 정렬: 0=left, 1=center, 2=right
  let hAlign = parseInt(element.attr("ha")) || 0;
  const lSpace = parseFloat(element.attr("ls")) / 1000 || 0;  // letter-spacing (inch)
  const background = element.attr("ba");   // "1"이면 배경 오브젝트
  const es = element.attr("es") || "0";    // Effect Style (회전)
  const noRtrim = element.attr("nrt") === "1";
  const mmlWidth = element.attr("wd");
  
  // es가 세로쓰기/회전 조정하는 경우
  if (es === "1" || es === "3") hAlign = 1;         // rotate ±90 → center
  else if (es === "2") {                            // rotate 180 → swap
    if (hAlign === 0) hAlign = 2;
    else if (hAlign === 2) hAlign = 0;
  }
  const alignMap = {0: "left", 1: "center", 2: "right"};
  const alignStr = alignMap[hAlign];
  
  // div 생성
  const div = createDiv({
    position: "absolute",
    "white-space": "pre",
    left: (left + offset.x) + "px",
    top:  (top + offset.y) + "px",
    "text-align": alignStr,
    "line-height": (bottom - top) + "px",
    height: (bottom - top) + "px",
    "letter-spacing": lSpace + "in"
  });
  
  if (background === "1") div.addClass("crownix-background-object");
  
  // 자식이 있으면(멀티 스타일) 각 자식을 <span>으로, 아니면 텍스트 그대로
  if (element.children().length > 0) {
    element.children().each(function(index, tx) {
      const textAttr = textAttrList[tx.attr("tid")] || {};
      const textCss = makeTextCss(tx, textAttr, fontList);
      let text = tx.text().replace(/(\r|\n)/g, "");
      if (alignStr === "left" && index === lastIndex && !noRtrim) {
        text = text.replace(/\s+$/, "");  // right trim
      }
      const span = createSpan(textCss, text);
      // 반전(cr), superscript/subscript(ss) 등 처리...
      div.append(span);
    });
  } else {
    const textAttr = textAttrList[element.attr("tid")] || {};
    const textCss = makeTextCss(element, textAttr, fontList);
    let text = element.text().replace(/(\r|\n)/g, "");
    if (alignStr === "left" && !noRtrim) text = text.replace(/\s+$/, "");
    div.css(textCss).text(text);
  }
  
  // 회전 처리 (es)
  if (es === "1")      div.css("transform", "rotate(-90deg)");
  else if (es === "2") div.css("transform", "rotate(-180deg)");
  else if (es === "3") div.css("transform", "rotate(-270deg)");
  
  // 세로쓰기 (ve 속성)
  if (element.attr("ve")) {
    div.css({"writing-mode": "vertical-lr", "line-height": "normal"});
  }
  
  // 페이지 번호 (ft="1" or "2")
  if (element.attr("ft") === "1" || element.attr("ft") === "2") {
    div.addClass("crownix-pagenum");
  }
  
  container.append(div);
  adjustText(div, right-left, bottom-top, mmlWidth, alignStr, ...);  // 폭 맞추기
}
```

### 6.7 makeTextCss (텍스트 스타일 계산)
```javascript
function makeTextCss(element, textAttr, fontList) {
  const css = {};
  // 폰트: element.fn 우선, 없으면 textAttr.fi 로 fontList 조회
  const fontName = element.attr("fn") || fontList.get(textAttr.fi) || "";
  css["font-family"] = `"${fontName}"`;
  // 크기: element.pt/10 우선, 없으면 textAttr.pt/10 (기본 10pt)
  css["font-size"] = ((element.attr("pt") ? element.attr("pt")/10 : textAttr.pt/10) || 10) + "pt";
  // 색상 (hyperlink 없을 때만)
  if (!element.attr("hl") && !element.attr("hi")) {
    css.color = element.attr("cl") || textAttr.cl || "#000000";
  }
  // Bold / Italic / Underline / Strikethrough
  const isBold = element.attr("cb") ? element.attr("cb") === "1" : textAttr.cb === "1";
  if (isBold) css["font-weight"] = "bold";
  const isItalic = element.attr("ci") ? element.attr("ci") === "1" : textAttr.ci === "1";
  if (isItalic) css["font-style"] = "italic";
  const isUnderline = element.attr("cu") ? element.attr("cu") === "1" : textAttr.cu === "1";
  if (isUnderline) css["text-decoration"] = "underline";
  const isStrike = element.attr("cs") ? element.attr("cs") === "1" : textAttr.cs === "1";
  if (isStrike) css["text-decoration"] = (css["text-decoration"] || "") + " line-through";
  // 숨김
  if (element.attr("vs") === "0") css.visibility = "hidden";
  return css;
}
```

---

## 7. Canvas 실제 그리기 함수 (참고)

### 7.1 선 그리기 (dash pattern 포함)
```javascript
// dash 패턴 상수
const DASH_PATTERNS = {
  0: null,           // solid
  1: [2, 2],         // dash
  2: [2, 2],         // dash (동일)
  3: [10, 2, 2, 2],           // dash-dot
  4: [10, 2, 2, 2, 2, 2]      // dash-dot-dot
};

function canvasDrawLine(ctx, x1, y1, x2, y2, lineAttr) {
  const pattern = DASH_PATTERNS[lineAttr.st];
  if (pattern) {
    ctx.setLineDash(pattern.map(v => v * lineAttr.wd));
  } else {
    ctx.setLineDash([]);
  }
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.strokeStyle = lineAttr.lc;
  ctx.lineWidth = lineAttr.wd;
  ctx.globalAlpha = lineAttr.al;
  
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
  
  // 화살표 (ap 속성)
  drawArrow(ctx, x1, y1, x2, y2, lineAttr);
}
```

### 7.2 사각형 (라운드 코너 포함)
```javascript
function canvasDrawRect(ctx, x1, y1, x2, y2, lineAttr, faceAttr, cornerW, cornerH, cornerMask) {
  ctx.beginPath();
  if (typeof cornerW === "number" && typeof cornerH === "number") {
    // 라운드 사각형: cornerMask 비트로 각 코너 결정
    // bit 1 = TopLeft, 2 = TopRight, 4 = BottomLeft, 8 = BottomRight
    ctx.moveTo(x1, y1 + cornerH);
    ctx.lineTo(x1, y2 - cornerH);
    (cornerMask & 4) ? ctx.quadraticCurveTo(x1, y2, x1 + cornerW, y2) : ctx.lineTo(x1, y2);
    ctx.lineTo(x2 - cornerW, y2);
    (cornerMask & 8) ? ctx.quadraticCurveTo(x2, y2, x2, y2 - cornerH) : ctx.lineTo(x2, y2);
    ctx.lineTo(x2, y1 + cornerH);
    (cornerMask & 2) ? ctx.quadraticCurveTo(x2, y1, x2 - cornerW, y1) : ctx.lineTo(x2, y1);
    ctx.lineTo(x1 + cornerW, y1);
    (cornerMask & 1) ? ctx.quadraticCurveTo(x1, y1, x1, y1 + cornerH) : ctx.lineTo(x1, y1);
  } else {
    ctx.rect(x1, y1, x2 - x1, y2 - y1);
  }
  ctx.closePath();
  fillWithFaceAttr(ctx, faceAttr, lineAttr);   // 채우기
  strokeWithLineAttr(ctx, lineAttr);            // 선
}
```

---

## 8. 실전 재구현 최소 골격 (JavaScript)

```javascript
class CrownixRenderer {
  constructor(canvas, textContainer) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.textContainer = textContainer;
    this.offset = { x: 0, y: 0 };
    this.margin = 24.3;
  }
  
  adjustCoord(mmlCoord, lineWidth = 0) {
    return Math.floor(mmlCoord / 10.3 + this.margin) 
         + (lineWidth % 2 === 0 ? 0 : 0.5);
  }
  
  async render(mmlXmlString) {
    const doc = new DOMParser().parseFromString(mmlXmlString, "text/xml");
    const head = this.parseHead(doc.querySelector("HEAD"));
    const pages = doc.querySelectorAll("PG");
    for (const page of pages) {
      this.drawPage(page, head);
    }
  }
  
  parseHead(headEl) {
    const head = { fontList: new Map(), textAttrList: {}, faceAttrList: {}, 
                    lineAttrList: {}, indexedImageList: {} };
    
    headEl.querySelectorAll("FONTLIST > *").forEach(f => 
      head.fontList.set(f.getAttribute("id"), f.getAttribute("name")));
    
    headEl.querySelectorAll("TALIST > *").forEach(ta => {
      head.textAttrList[ta.getAttribute("id")] = {
        fi: ta.getAttribute("fi"), pt: ta.getAttribute("pt"),
        cl: ta.getAttribute("cl"), cb: ta.getAttribute("cb"),
        ci: ta.getAttribute("ci"), cu: ta.getAttribute("cu"),
        cs: ta.getAttribute("cs"), /* ... */
      };
    });
    
    headEl.querySelectorAll("FALIST > *").forEach(fa => {
      head.faceAttrList[fa.getAttribute("id")] = {
        fc: fa.getAttribute("fc") || "#FFFFFF",
        al: fa.getAttribute("al") || 1,
        pt: fa.getAttribute("pt") || 0
      };
    });
    
    headEl.querySelectorAll("LALIST > *").forEach(la => {
      head.lineAttrList[la.getAttribute("id")] = {
        st: parseInt(la.getAttribute("st") || 0),
        wd: parseFloat(la.getAttribute("wd") || 10) / 10.3,  // adjustWidth
        lc: la.getAttribute("lc") || "#000000",
        al: parseFloat(la.getAttribute("al") || 1),
        ap: parseInt(la.getAttribute("ap") || 0),
      };
    });
    
    headEl.querySelectorAll("IMLIST > *").forEach(im => {
      const img = new Image();
      img.src = im.getAttribute("va");
      head.indexedImageList[im.getAttribute("id")] = img;
    });
    
    return head;
  }
  
  drawPage(pageEl, head) {
    const traverse = (el) => {
      for (const child of el.children) {
        this.drawElement(child, head);
        // 컨테이너 태그면 재귀
        if (["PR","TB","TE","FC","SO","SG","IL","CA","SL"].includes(child.tagName)) {
          traverse(child);
        }
      }
    };
    traverse(pageEl);
  }
  
  drawElement(el, head) {
    const tag = el.tagName.toUpperCase();
    const lineAttr = head.lineAttrList[el.getAttribute("lid")];
    const faceAttr = head.faceAttrList[el.getAttribute("fid")];
    
    switch (tag) {
      case "LN": return this.drawLine(el, lineAttr);
      case "RA": return this.drawRect(el, lineAttr, faceAttr);
      case "TL": return this.drawText(el, head.fontList, head.textAttrList);
      case "IM": return this.drawImage(el, head.indexedImageList);
      case "EP": return this.drawEllipse(el, lineAttr, faceAttr);
      // ... 나머지 태그
    }
  }
  
  drawLine(el, lineAttr) {
    if (!lineAttr || el.getAttribute("vs") === "0") return;
    const sx = this.adjustCoord(el.getAttribute("sx"), lineAttr.wd);
    const sy = this.adjustCoord(el.getAttribute("sy"), lineAttr.wd);
    const ex = this.adjustCoord(el.getAttribute("ex"), lineAttr.wd);
    const ey = this.adjustCoord(el.getAttribute("ey"), lineAttr.wd);
    
    const ctx = this.ctx;
    ctx.save();
    const DASH = {0:[], 1:[2,2], 2:[2,2], 3:[10,2,2,2], 4:[10,2,2,2,2,2]};
    ctx.setLineDash((DASH[lineAttr.st] || []).map(v => v * lineAttr.wd));
    ctx.strokeStyle = lineAttr.lc;
    ctx.lineWidth = lineAttr.wd;
    ctx.globalAlpha = lineAttr.al;
    ctx.beginPath();
    ctx.moveTo(sx, sy);
    ctx.lineTo(ex, ey);
    ctx.stroke();
    ctx.restore();
  }
  
  drawRect(el, lineAttr, faceAttr) {
    const sx = this.adjustCoord(el.getAttribute("sx"), lineAttr?.wd || 0);
    const sy = this.adjustCoord(el.getAttribute("sy"), lineAttr?.wd || 0);
    const ex = this.adjustCoord(el.getAttribute("ex"), lineAttr?.wd || 0);
    const ey = this.adjustCoord(el.getAttribute("ey"), lineAttr?.wd || 0);
    
    const ctx = this.ctx;
    ctx.save();
    ctx.beginPath();
    ctx.rect(sx, sy, ex - sx, ey - sy);
    if (faceAttr) {
      ctx.fillStyle = faceAttr.fc;
      ctx.globalAlpha = faceAttr.al;
      ctx.fill();
    }
    if (lineAttr && lineAttr.wd > 0) {
      ctx.strokeStyle = lineAttr.lc;
      ctx.lineWidth = lineAttr.wd;
      ctx.globalAlpha = lineAttr.al;
      ctx.stroke();
    }
    ctx.restore();
  }
  
  drawText(el, fontList, textAttrList) {
    const left   = this.adjustCoord(el.getAttribute("le"));
    const right  = this.adjustCoord(el.getAttribute("ri"));
    const top    = this.adjustCoord(el.getAttribute("to")) - 1;
    const bottom = this.adjustCoord(el.getAttribute("bo")) + 1;
    
    const hAlign = parseInt(el.getAttribute("ha")) || 0;
    const alignMap = {0:"left", 1:"center", 2:"right"};
    
    const div = document.createElement("div");
    Object.assign(div.style, {
      position: "absolute",
      whiteSpace: "pre",
      left: (left + this.offset.x) + "px",
      top:  (top + this.offset.y) + "px",
      width: (right - left) + "px",
      height: (bottom - top) + "px",
      lineHeight: (bottom - top) + "px",
      textAlign: alignMap[hAlign],
    });
    
    // 자식이 있으면 <span>으로 각각, 없으면 통짜 텍스트
    if (el.children.length > 0) {
      for (const tx of el.children) {
        const ta = textAttrList[tx.getAttribute("tid")] || {};
        const span = document.createElement("span");
        this.applyTextCss(span, tx, ta, fontList);
        span.textContent = tx.textContent.replace(/[\r\n]/g, "");
        div.appendChild(span);
      }
    } else {
      const ta = textAttrList[el.getAttribute("tid")] || {};
      this.applyTextCss(div, el, ta, fontList);
      div.textContent = el.textContent.replace(/[\r\n]/g, "");
    }
    
    this.textContainer.appendChild(div);
  }
  
  applyTextCss(dom, el, textAttr, fontList) {
    const fontName = el.getAttribute("fn") || fontList.get(textAttr.fi) || "";
    dom.style.fontFamily = `"${fontName}"`;
    const pt = el.getAttribute("pt") ? el.getAttribute("pt")/10 
                                     : (textAttr.pt || 100) / 10;
    dom.style.fontSize = pt + "pt";
    dom.style.color = el.getAttribute("cl") || textAttr.cl || "#000000";
    if ((el.getAttribute("cb") || textAttr.cb) === "1") dom.style.fontWeight = "bold";
    if ((el.getAttribute("ci") || textAttr.ci) === "1") dom.style.fontStyle = "italic";
    let deco = [];
    if ((el.getAttribute("cu") || textAttr.cu) === "1") deco.push("underline");
    if ((el.getAttribute("cs") || textAttr.cs) === "1") deco.push("line-through");
    if (deco.length) dom.style.textDecoration = deco.join(" ");
  }
  
  drawImage(el, indexedImageList) {
    const sx = this.adjustCoord(el.getAttribute("sx"));
    const sy = this.adjustCoord(el.getAttribute("sy"));
    const ex = this.adjustCoord(el.getAttribute("ex"));
    const ey = this.adjustCoord(el.getAttribute("ey"));
    const idx = el.getAttribute("idx") || el.getAttribute("id");
    const img = indexedImageList[idx];
    if (img && img.complete) {
      this.ctx.drawImage(img, sx, sy, ex - sx, ey - sy);
    } else if (img) {
      img.onload = () => this.ctx.drawImage(img, sx, sy, ex - sx, ey - sy);
    }
  }
}
```

---

## 9. 주요 미확인 항목 (샘플 XML로 검증 필요)

다음은 원본 코드에서 위치는 확인했지만 세부 로직이 복잡해서 실제 XML 샘플과 대조가 필요한 부분들입니다:

1. **`drawGrid` (TG 태그)** — 표 그리기. 셀 병합, 헤더/바디 구분 등 복잡한 로직
2. **`drawChart` (CT 태그)** — 차트. 내부에 SVG 또는 CC(Crownix Chart) 자식 있음
3. **`drawFormField` (FF 태그)** — 입력 폼 필드 (양방향)
4. **`adjustText`** — 글자 폭이 셀보다 넓을 때 자간/스케일 조정
5. **`ect` 속성** — 매체별 렌더링 제외 (d=display, p=print)
6. **하이퍼링크** (`hl`, `hi` 속성)
7. **페이지 번호** (`ft="1"` 또는 `ft="2"` — 현재 페이지 / 전체 페이지)
8. **폼모드 렌더링** (`isFormEditMode`)

실제 성적표 같은 문서에서 최소 필요 태그는:
- **RA** (사각형 - 테두리/배경)
- **LN** (선 - 표 구분선)
- **TL** (텍스트)

이 셋만으로도 성적표 대부분이 재현 가능합니다.

---

## 10. 참고: 파일 내 원본 위치 (crownix-viewer.min.js 92,930줄 beautified 기준)

| 함수/상수 | 라인 번호 |
|---|---|
| Canvas 상수 (dash 패턴) | 7878-7886 |
| `this.drawLine` (canvas) | 8190 |
| `this.drawRect` (canvas) | 8264 |
| `this.drawText` (canvas) | 8514 |
| `adjustCoord` | 33741, 39343 |
| `drawLine` (element→canvas) | 33757 |
| `drawRect` | 33808 |
| `makeTextCss` | 34251 |
| `drawText` (DOM 배치) | 34321 |
| `drawImage` | 34838 |
| `drawPage` (메인 디스패처) | 38974 |
| `parseHead` | 56614 |

---

## PART 2. 원본 소스 발췌 (검증용)

아래는 `crownix-viewer.min.js`에서 뽑은 실제 코드입니다.
minified 원본을 beautify한 것이라 변수명이 축약(T,S,N...)되어 있지만 로직은 원본 그대로입니다.
스펙과 대조하면서 정확성 검증할 때 참고하세요.

```javascript
// ============================================================
// crownix-viewer.min.js 에서 추출한 핵심 렌더링 코드
// 원본은 minified라 변수명이 축약(T,S,N...)되어 있음.
// beautify + 개행 처리한 버전 (원본 로직 그대로).
// ============================================================

// -----------------------------------------------------------
// 1. Canvas 클래스 상수 (drawLine의 lineAttr.st 값)
// -----------------------------------------------------------
// var G=0;   // solid (line style 0)
// var D=1;   // dash  (line style 1) -> [2,2]
// var C=2;   // dash  (line style 2) -> [2,2]
// var w=3;   // dash-dot (line style 3) -> [10,2,2,2]
// var s=4;   // dash-dot-dot (line style 4) -> [10,2,2,2,2,2]


// -----------------------------------------------------------
// 2. adjustCoord — MML 좌표 → 픽셀 변환
// -----------------------------------------------------------
m2soft.crownix.Painter.adjustCoord=function(a,b,c,d){
if(c==null||c==undefined){
c=24.3
}
if(!b){
b=0
}
var e=Math.floor(a/10.3+c)+(b%2===0?0:0.5);
if(d&&d!=1){
e-=((e*d)%1)/d
}
return e
}
;
m2soft.crownix.Painter.reverseAdjustCoord=function(a){
return Math.floor(a*10.3)
}
;

// -----------------------------------------------------------
// 3. 개별 draw 함수 (element → canvas 호출)
// -----------------------------------------------------------
var adjustCoord=function(mi,width){
if(go.options.textOnCanvas){
var scale=viewerScroll.scale*window.devicePixelRatio||1;
if(scale>go.options.maxScaleRatio){
scale=go.options.maxScaleRatio
}
return Painter.adjustCoord(mi,width,margin,scale)
}
else{
return Painter.adjustCoord(mi,width,margin)
}

}
;
var applyPriorityToFaceAttr=Painter.applyPriorityToFaceAttr;
var applyPriorityToLineAttr=Painter.applyPriorityToLineAttr;
var drawLine=function(element,lineAttr){
if(element.attr("vs")&&element.attr("vs")=="0"){
return
}
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
cxCanvas.drawLine(sx,sy,ex,ey,lineAttr)
}
;
var drawCurve=function(element,lineAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
var cx1=adjustCoord(element.attr("cx1"),lineAttr.wd);
var cy1=adjustCoord(element.attr("cy1"),lineAttr.wd);
var cx2=adjustCoord(element.attr("cx2"),lineAttr.wd);
var cy2=adjustCoord(element.attr("cy2"),lineAttr.wd);
cxCanvas.drawCurve(sx,sy,ex,ey,lineAttr,cx1,cy1,cx2,cy2)
}
;
var drawPolyLine=function(element,lineAttr){
var data=element.attr("dt").split("|");
var points=[];
for(var i=0,max=data.length;
i<max;
i++){
var point=data[i].split(",");
var x=adjustCoord(point[0],lineAttr.wd);
var y=adjustCoord(point[1],lineAttr.wd);
points.push([x,y])
}
cxCanvas.drawPolyLine(lineAttr,points)
}
;
var drawScribble=function(element,lineAttr){
var data=element.attr("dt").split("|");
var points=[];
for(var i=0,max=data.length;
i<max;
i++){
var point=data[i].split(",");
var x=adjustCoord(point[0]);
var y=adjustCoord(point[1]);
points.push([x,y])
}
cxCanvas.drawScribble(lineAttr,points)
}
;
var drawRect=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
cxCanvas.drawRect(sx,sy,ex,ey,lineAttr,faceAttr)
}
;
var drawRoundeRect=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
var aw=element.attr("aw")/10.3;
var ah=element.attr("ah")/10.3;
var ap=element.attr("ap");
cxCanvas.drawRect(sx,sy,ex,ey,lineAttr,faceAttr,aw,ah,ap);
cxCanvas.setClip(element.attr("cl"))
}
;
var drawEllipse=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
cxCanvas.drawEllipse(sx,sy,ex,ey,lineAttr,faceAttr)
}
;
var drawDiamond=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
cxCanvas.drawDiamond(sx,sy,ex,ey,lineAttr,faceAttr)
}
;
var drawParallelogram=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
var cx=element.attr("cx")/10.3;
cxCanvas.drawParallelogram(sx,sy,ex,ey,lineAttr,faceAttr,cx)
}
;
var drawPolygon=function(element,lineAttr,faceAttr){
var data=element.attr("dt").split("|");
var points=[];
for(var i=0,max=data.length;
i<max;
i++){
var point=data[i].split(",");
var x=adjustCoord(point[0],lineAttr.wd);
var y=adjustCoord(point[1],lineAttr.wd);
points.push([x,y])
}
cxCanvas.drawPolygon(lineAttr,faceAttr,points)
}
;
var drawHexaheron=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
var cx=element.attr("cx")/10.3;
var cy=element.attr("cy")/10.3;
cxCanvas.drawHexaheron(sx,sy,ex,ey,cx,cy,lineAttr,faceAttr)
}
;
var drawCylinder=function(element,lineAttr,faceAttr){
var sx=adjustCoord(element.attr("sx"),lineAttr.wd);
var sy=adjustCoord(element.attr("sy"),lineAttr.wd);
var ex=adjustCoord(element.attr("ex"),lineAttr.wd);
var ey=adjustCoord(element.attr("ey"),lineAttr.wd);
var sh=element.attr("sh")/10.3;
cxCanvas.drawCylinder(sx,sy,ex,ey,lineAttr,faceAttr,sh)
}
;
var drawSVG=function(element){
var left=adjustCoord(element.attr("sx"));
var right=adjustCoord(element.attr("ex"));
var top=adjustCoord(element.attr("sy"));
var bottom=adjustCoord(element.attr("ey"));
element.find("svg").clone().attr({
width:(right-left)+"px",height:(bottom-top)+"px",
}
).css({
position:"absolute",left:left+offset.x,top:top+offset.y,
}
).appendTo(viewerTextDom)
}
;
var drawCxChart=function(element,isPrint){
var left=adjustCoord(element.attr("sx"));
var right=adjustCoord(element.attr("ex"));
var top=adjustCoord(element.attr("sy"));
var bottom=adjustCoord(element.attr("ey"));
var div=$("<div>").css({
position:"absolute",left:left+offset.x,top:top+offset.y,width:(right-left)+"px",height:(bottom-top)+"px",
}
).appendTo(viewerTextDom);
try{
var converter=new ChartConverter();
var ret=converter.convertMml(element);
var chart=new crownix.Chart(div[0],(right-left),(bottom-top));
if(isPrint){
ret.series.forEach(function(series){
series.animation=false
}
)
}
if(ret.data){
chart.build(ret.series,ret.options,ret.data)
}
else{
chart.build(ret.series,ret.options)
}

}
catch(e){
Message.log(e.stack)
}


// -----------------------------------------------------------
// 4. makeTextCss — 텍스트 스타일 CSS 계산
// -----------------------------------------------------------
var makeTextCss=function(element,textAttr,fontList){
var css={

}
;
var makeFontNames=function(fontName){
if(go.alterFontMap&&go.alterFontMap[fontName]){
fontName+='", "'+go.alterFontMap[fontName]
}
return fontName
}
;
var fontName=makeFontNames((element.attr("fn")?element.attr("fn"):fontList.get(textAttr.fi)||""));
css["font-family"]='"'+fontName+'", '+('"'+Constant.FONT_FAMILY_PREFIX+fontName+'"');
css["font-size"]=((element.attr("pt")?element.attr("pt")/10:textAttr.pt/10)||10)+"pt";
if(!element.attr("hl")&&!element.attr("hi")){
css.color=(element.attr("cl")?element.attr("cl"):textAttr.cl)||"#000000"
}
(element.attr("cb")?element.attr("cb")==="1":(element.attr("cb")==undefined&&textAttr.cb==="1"))&&(css["font-weight"]="bold");
(element.attr("ci")?element.attr("ci")==="1":(element.attr("ci")==undefined&&textAttr.ci==="1"))&&(css["font-style"]="italic");
(element.attr("cu")?element.attr("cu")==="1":(element.attr("cu")==undefined&&textAttr.cu==="1"))&&(css["text-decoration"]="underline");
(element.attr("cs")?element.attr("cs")==="1":(element.attr("cs")==undefined&&textAttr.cs==="1"))&&(css["text-decoration"]?(css["text-decoration"]+=" line-through"):(css["text-decoration"]="line-through"));
(element.attr("vs")==="0")&&(css.visibility="hidden");
return css
}
;
var transObjectToCss=function(style){
if(!style||!Object.keys(style).length){
return
}
var css={

}
;
css["font-weight"]=style.bold=="1"?"bold":"normal";
css["font-style"]=style.italic=="1"?"italic":"normal";
if(style.underline=="1"){
css["text-decoration"]="underline"
}
if(style.cancelLine=="1"){
if(style.underline=="1"){
css["text-decoration"]+=" line-through"
}
else{
css["text-decoration"]="line-through"
}

}
if(!css["text-decoration"]){
css["text-decoration"]="none"
}
if(style.color){
css.color=style.color
}
return css
}
;
var transCssToObject=function(field,input){
field.style.bold=input.style.fontWeight=="bold"?"1":"0";
field.style.italic=input.style.fontStyle=="italic"?"1":"0";
field.style.underline=input.style.textDecoration.indexOf("underline")!=-1?"1":"0";
field.style.cancelLine=input.style.textDecoration.indexOf("line-through")!=-1?"1":"0";
if(input.style.color){
var Color=m2soft.crownix.util.Color;
var rgbArr=Color.toRgbArray(input.style.color);
field.style.color=Color.rgbToHex(rgbArr)
}
return field
}
;

// -----------------------------------------------------------
// 5. drawText — 텍스트를 절대좌표 <div>로 배치
// -----------------------------------------------------------
var drawText=function(element,fontList,textAttrList){
var left=adjustCoord(element.attr("le"));
var right=adjustCoord(element.attr("ri"));
var top=adjustCoord(element.attr("to"))-1;
var bottom=adjustCoord(element.attr("bo"))+1;
var hAlign=parseInt(element.attr("ha"))||0;
var lSpace=parseFloat(element.attr("ls"))/1000||0;
var background=element.attr("ba");
var es=element.attr("es")||"0";
var noRtrim=(element.attr("nrt")==="1");
var mmlWidth=element.attr("wd");
var isOverlapBottom=(element.attr("cbo")==="1");
var isOverlapTop=(element.attr("cto")==="1");
if(es==="1"||es==="3"){
hAlign=1
}
else{
if(es==="2"){
if(hAlign===0){
hAlign=2
}
else{
if(hAlign===2){
hAlign=0
}

}

}

}
if(hAlign===0){
hAlign="left"
}
else{
if(hAlign===1){
hAlign="center"
}
else{
if(hAlign===2){
hAlign="right"
}

}

}
var div=$("<div>").css({
position:"absolute","white-space":"pre",left:left+offset.x+"px",top:top+offset.y+"px","text-align":hAlign,"line-height":(bottom-top)+"px",
}
).bind("onFontLoadComplete",function(){
div.css("width","");
adjustText(div,right-left,bottom-top,mmlWidth,hAlign,jpval,textLength,lSpace,isOverlapBottom,isOverlapTop,false)
}
);
if(background==="1"){
div.addClass("crownix-background-object")
}
var textAttr={

}
;
var textCss={

}
;
var text="";
var textLength=0;
var jpval=false;
if(element.children().length>0){
var maxFontSize=0;
var elementlength=element.children().length;
element.children().each(function(index){
var tx=$(this);
textAttr=textAttrList[tx.attr("tid")]||{

}
;
textCss=makeTextCss(tx,textAttr,fontList);
text=tx.text().replace(/(\r|\n)/g,"");
if(hAlign==="left"&&index==elementlength-1&&!noRtrim){
text=Util.string.rtrim(text)
}
textLength+=text.length;
maxFontSize=Math.max(maxFontSize,textCss["font-size"].replace("pt",""));
jpval=tx.attr("sc")||textAttr.sc||0;
var span=$("<span>").css(textCss).text(text).appendTo(div);
if(tx.attr("cr")==="1"||textAttr.cr==="1"){
(textCss.color==="#000000")&&span.css("color","#ffffff");
span.wrapInner('<span style="background-color:#000000"></span>')
}
var ss=tx.attr("ss")||textAttr.ss||"0";
if(ss==="1"){
span.wrapInner('<sup style="line-height: 0;
"></sup>')
}
else{
if(ss==="2"){
span.wrapInner("<sub></sub>")
}

}
if(es==="1"){
div.css("transform","rotate(-90deg)")
}
else{
if(es==="2"){
div.css("transform","rotate(-180deg)")
}
else{
if(es==="3"){
div.css("transform","rotate(-270deg)")
}

}

}

}
);
div.css("font-size",maxFontSize+"pt")
}
else{
textAttr=textAttrList[element.attr("tid")]||{

}
;
textCss=makeTextCss(element,textAttr,fontList);
text=element.text().replace(/(\r|\n)/g,"");
if(hAlign==="left"&&!noRtrim){
text=Util.string.rtrim(text)
}
textLength=text.length;
jpval=element.attr("sc")||textAttr.sc||0;
div.css(textCss).text(text);
if(element.attr("cr")==="1"||textAttr.cr==="1"){
(textCss.color==="#000000")&&div.css("color","#ffffff");
div.wrapInner('<span style="background-color:#000000"></span>')
}
var ss=element.attr("ss")||textAttr.ss||"0";
if(ss==="1"){
div.wrapInner('<sup style="line-height: 0;
"></sup>')
}
else{
if(ss==="2"){
div.wrapInner("<sub></sub>")
}

}
if(es==="1"){
div.css("transform","rotate(-90deg)")
}
else{
if(es==="2"){
div.css("transform","rotate(-180deg)")
}
else{
if(es==="3"){
div.css("transform","rotate(-270deg)")
}

}

}

}
if(bottom){
div.css("height",(bottom-top)+"px")
}
div.css("letter-spacing",lSpace+"in");
if(element.attr("ft")==="1"||element.attr("ft")==="2"){
div.attr({
"pagenum-format":div.text(),"format-type":element.attr("ft")
}
).addClass("crownix-pagenum")
}
div.appendTo(viewerTextDom);
adjustText(div,right-left,bottom-top,mmlWidth,hAlign,jpval,textLength,lSpace,isOverlapBottom,isOverlapTop,true);
if(element.attr("ve")){
div.css({
"writing-mode":(Util.browser.msie?"tb-lr":"vertical-lr"),"-webkit-writing-mode":"vertical-lr","line-height":"normal"
}
)
}
if(Util.browser.webkit){
div.css("min-height",(parseInt(div.css("height"))+1)+"px")
}
if(!!element.attr("hl")||!!element.attr("hi")){
var parent=element.parent();
if(parent.prop("tagName").toUpperCase()==="PR"){
parent=parent.parent()
}
var hyperlinkInfoElement=parent.find('[hi="'+element.attr("hi")+'"][hl]');
if(hyperlinkInfoElement.length==0){
hyperlinkInfoElement=element
}
applyHyperLink(div,top+offset.y,left+offset.x,hyperlinkInfoElement,element)
}

}
;
var adjustText=function(div,width,height,mmlWidth,hAlign,jpval,textLength,lSpace,isOverlapBottom,isOverlapTop,isMakeWrapper){
width+=2;
var adjustCss,adjustScale;
var adjust=calcAdjustCssAndScale(div,mmlWidth,hAlign,width,jpval);
if(adjust!==false){
adjustCss=adjust.css;
adjustScale=adjust.scale
}
else{
if(useAdjustLetterSpace){
adjustLetterSpace(div,textLength,width,lSpace)
}

}
width=clipText(div,adjustCss,adjustScale,width,height,isOverlapBottom,isOverlapTop,isMakeWrapper);
if(adjustCss){
div.css(adjustCss)
}
div.css("width",width+"px");
div.find("span").css("white-space","pre")
}
;
var clipText=function(div,adjustCss,adjustScale,width,height,isOverlapBottom,isOverlapTop,isMakeWrapper){
var clip;
if(isOverlapBottom&&!isOverlapTop){
clip="top"
}
else{
if(isOverlapTop&&!isOverlapBottom){
clip="bottom"
}

}
if(clip){
var maxHeight=0;
var innerSpan=isMakeWrapper?div.wrapInner("<span>").find(":first"):div.find(":first");
if(innerSpan.children().length>0){
innerSpan.children().each(function(){
maxHeight=Math.max(maxHeight,$(this).height())
}
)
}
else{
maxHeight=innerSpan.height()
}
div.css("overflow","hidden");
innerSpan.css("position","relative").css(clip,Math.abs((maxHeight/2)-(height/2)));
if(adjustCss){
width=width/adjustScale
}

}
return width
}
;
var adjustLetterSpace=function(div,textLength,width,lSpace){
var divWidth=div.width();
if(textLength>1&&divWidth>width+Unit.inToPx(lSpace)){
var adjustSpacing=lSpace-Unit.pxToIn((divWidth-width)/(textLength));

// -----------------------------------------------------------
// 6. drawImage
// -----------------------------------------------------------
var drawImage=function(element,image){
var left=adjustCoord(element.attr("sx"));
var right=adjustCoord(element.attr("ex"));
var top=adjustCoord(element.attr("sy"));
var bottom=adjustCoord(element.attr("ey"));
var data=element.attr("dt");
var ratio=element.attr("ra");
var background=element.attr("ba");
var width=right-left;
var height=bottom-top;
if(go.options.imageDrawOption.useCanvas||background){
cxCanvas.drawImage(image,left,top,width,height,ratio);
if(!!element.attr("hl")||!!element.attr("hi")){
var imgElement=$("<div>").css({
position:"absolute",left:left+"px",top:top+"px",width:width+"px",height:height+"px",
}
).appendTo(viewerTextDom);
var parent=element.parent();
if(parent.prop("tagName").toUpperCase()==="PR"){
parent=parent.parent()
}
var hyperlinkInfoElement=parent.find('[hi="'+element.attr("hi")+'"][hl]');
if(hyperlinkInfoElement.length==0){
hyperlinkInfoElement=element
}
applyHyperLink(imgElement,top+offset.y,left+offset.x,hyperlinkInfoElement,element)
}

}
else{
var imgElement;
if(ratio==="1"){
imgElement=$("<img>").css("background",["url(",data,")"].join(""))
}
else{
imgElement=$("<img>").attr("src",data)
}
imgElement.attr({
width:width,height:height,
}
).css({
position:"absolute",left:left+offset.x,top:top+offset.y,
}
).appendTo(viewerTextDom);
if(!!element.attr("hl")||!!element.attr("hi")){
var parent=element.parent();
if(parent.prop("tagName").toUpperCase()==="PR"){
parent=parent.parent()
}
var hyperlinkInfoElement=parent.find('[hi="'+element.attr("hi")+'"][hl]');
if(hyperlinkInfoElement.length==0){
hyperlinkInfoElement=element
}
applyHyperLink(imgElement,top+offset.y,left+offset.x,hyperlinkInfoElement,element)
}

}

}
;
var getElementId=function(element,type){
if(type==="rb"){
return"crownix_form_field_"+element.attr("fi")+"_"+element.attr("gi")+"_"+element.attr("id")
}
else{
return"crownix_form_field_"+element.attr("fi")+"_"+element.attr("id")
}

}
;
var drawFormField=function(element,fontList,textAttrList,parentDom){
var left=adjustCoord(element.attr("le"))+2+offset.x;
var right=adjustCoord(element.attr("ri"))-1+offset.x;

// -----------------------------------------------------------
// 7. drawPage — 페이지 순회 및 태그별 디스패치 (진입점)
// -----------------------------------------------------------
this.drawPage=function(page,head,callback,drawMedia_,offset_,margin_,withoutClear_,isFloatingLayer){
if(!withoutClear_){
cxCanvas.clear()
}
var fontList=head.fontList,textAttrList=head.textAttrList,lineAttrList=head.lineAttrList,faceAttrList=head.faceAttrList,indexedImageList=head.indexedImageList;
drawMedia=drawMedia_||"d";
if(offset_){
offset=offset_
}
if(margin_!=undefined){
margin=margin_
}
cxCanvas.setOffset(offset);
var getLineAttr=function(element){
var lineAttr=lineAttrList[element.attr("lid")];
return lineAttr&&applyPriorityToLineAttr(element,lineAttr)
}
;
var getFaceAttr=function(element){
var faceAttr=faceAttrList[element.attr("fid")];
return faceAttr&&applyPriorityToFaceAttr(element,faceAttr)
}
;
var imageLoader=new m2soft.crownix.ImageLoader({
asyncOnAllLoad:!!go.options.showPageLoadingOverlay
}
);
var drawObject=function(element){
var elementName=element.prop("tagName").toUpperCase();
var exceptList=element.attr("ect");
if(exceptList&&exceptList.indexOf(drawMedia)>-1){
elementName==="IM"&&imageLoader.nextImage();
return
}
var lineAttr=getLineAttr(element);
var faceAttr=getFaceAttr(element);
if(elementName==="LN"){
drawLine(element,lineAttr)
}
else{
if(elementName==="RA"){
drawRect(element,lineAttr,faceAttr)
}
else{
if(elementName==="TL"){
if(go.textOnCanvas){
drawTextToCanvas(element,fontList,textAttrList);
if(element.attr("hl")||element.attr("hi")){
drawText(element,fontList,textAttrList)
}

}
else{
drawText(element,fontList,textAttrList)
}

}
else{
if(elementName==="SC"){
drawScribble(element,lineAttr)
}
else{
if(elementName==="LL"){
drawPolyLine(element,lineAttr)
}
else{
if(elementName==="PO"){
drawPolygon(element,lineAttr,faceAttr)
}
else{
if(elementName==="EP"){
drawEllipse(element,lineAttr,faceAttr)
}
else{
if(elementName==="DM"){
drawDiamond(element,lineAttr,faceAttr)
}
else{
if(elementName==="PL"){
drawParallelogram(element,lineAttr,faceAttr)
}
else{
if(elementName==="HH"){
drawHexaheron(element,lineAttr,faceAttr)
}
else{
if(elementName==="RR"){
drawRoundeRect(element,lineAttr,faceAttr)
}
else{
if(elementName==="CY"){
drawCylinder(element,lineAttr,faceAttr)
}
else{
if(elementName==="CV"){
drawCurve(element,lineAttr)
}
else{
if(elementName==="IM"){
drawImage(element,imageLoader.nextImage())
}
else{
if(elementName==="FF"){
drawFormField(element,fontList,textAttrList)
}
else{
if(elementName==="CT"){
drawChart(element,drawMedia==="p")
}
else{
if(elementName==="CL"||elementName==="LY"){
drawLayer(element)
}
else{
if(elementName==="TG"){
drawGrid(element,isFloatingLayer,fontList,textAttrList)
}

}

}

}

}

}

}

}

}

}

}

}

}

}

}

}

}

}

}
;
var onDrawObject=function(){
if(drawMedia==="p"){
m2soft.crownix.Painter.updatePageNum({
textDom:viewerTextDom,currentPage:parseInt(page.attr("no")),
}
)
}
else{
m2soft.crownix.Painter.updatePageNum()
}
if(callback!==null){
callback()
}

}
;
if(go.options.scrollOptions.useInfiniteScroll&&drawMedia==="d"){
var images=[];
var objectArray=[];
var traverse=function(element){
var children=element.children();
if(children.length>0){
for(var i=0,length=children.length;
i<length;
i++){
var element=$(children[i]);
var elementName=element.prop("tagName").toUpperCase();
objectArray.push(children[i]);
if(elementName==="PR"||elementName==="TB"||elementName==="TE"||elementName==="FC"||elementName==="SO"||elementName==="SG"||elementName==="IL"||elementName==="CA"||elementName==="SL"){
traverse(element)
}

}

}

}
;
traverse(page);
var taskID=parseInt($(canvasDom).parent().attr("no"))||$(canvasDom).parent().parent().attr("class");
var renderTask=go.pageRenderer.getRenderTask(taskID);
objectArray.map(function(object,index){
if(object.nodeName==="IM"||object.nodeName==="im"){
images.push(object)
}
renderTask.add(drawObject,$(object))
}
);
renderTask.add(onDrawObject,null);
renderTask.add(function(){
if(!GuideManager.getInstance(go).getWindow()||GuideManager.getInstance(go).isEnabled){
GuideManager.getInstance(go).show()
}

}
,null);
imageLoader.loadImage(images,indexedImageList,renderTask.start)
}
else{
imageLoader.loadImage(page,indexedImageList,function(){
var traverse=function(element){
var children=element.children();
if(children.length>0){
for(var i=0,length=children.length;
i<length;
i++){
var element=$(children[i]);
var elementName=element.prop("tagName").toUpperCase();
drawObject(element);
if(elementName==="PR"||elementName==="TB"||elementName==="TE"||elementName==="FC"||elementName==="SO"||elementName==="SG"||elementName==="IL"||elementName==="CA"||elementName==="SL"){
traverse(element)
}


// -----------------------------------------------------------
// 8. parseHead — HEAD 태그 파싱 (폰트/속성 테이블 생성)
// -----------------------------------------------------------
this.parseHead=function(W){
var Y=new J(),X=(typeof W==="string")?$($.parseXML(W)).find("HEAD").children():W;
$(X).each(function(){
var Z=this.tagName;
if(Z==="INFORMATION"){
$(this).children("TITLE").each(function(){
Y.title=$(this).text()
}
);
$(this).children("MRD").each(function(){
Y.mrd=$(this).text()
}
);
$(this).children("MRDPATH").each(function(){
Y.mrdpath=$(this).text()
}
);
$(this).children("MRDPARAM").each(function(){
Y.mrdparam=$(this).text()
}
);
$(this).children("PGINFO").each(function(){
if(a==-1){
a=parseInt($(this).attr("cover"))
}
if($(this).attr("startnum")){
z=n.startPageNo=parseInt($(this).attr("startnum"))
}
if($(this).attr("pginit")==="1"){
d=true;
Y.pginit=true
}
f=parseInt($(this).attr("exceptpg"))||0
}
);
if(u>=2.9){
var aa=$(this).children("MRDINFO");
Y.preventSave=aa.attr("rst")==="1"
}

}
else{
if(Z==="TITLE"){
Y.title=$(this).text()
}
else{
if(Z==="MRD"){
Y.mrd=$(this).text()
}
else{
if(Z==="FONTLIST"){
if(n.useWebFont&&t===1){
n.fontLoader.showMessage()
}
$(this).children().each(function(){
Y.fontList.put($(this).attr("id"),$(this).attr("name"));
if(n.useWebFont){
n.fontLoader.load(Number(t).toString(),$(this).attr("name"))
}

}
)
}
else{
if(Z==="TALIST"){
$(this).children().each(function(){
Y.textAttrList[$(this).attr("id")]={
fi:$(this).attr("fi"),pt:$(this).attr("pt"),cl:$(this).attr("cl"),cb:$(this).attr("cb"),ci:$(this).attr("ci"),cu:$(this).attr("cu"),cs:$(this).attr("cs"),cr:$(this).attr("cr"),sc:$(this).attr("sc"),ls:$(this).attr("ls"),ss:$(this).attr("ss"),es:$(this).attr("es"),
}

}
)
}
else{
if(Z==="FALIST"){
$(this).children().each(function(){
Y.faceAttrList[$(this).attr("id")]={
fc:$(this).attr("fc")||"#FFFFFF",al:$(this).attr("al")||1,pt:$(this).attr("pt")||0,
}

}
)
}
else{
if(Z==="LALIST"){
$(this).children().each(function(){
Y.lineAttrList[$(this).attr("id")]={
st:$(this).attr("st")||0,wd:e.adjustWidth($(this).attr("wd")),lc:$(this).attr("lc")||"#000000",al:$(this).attr("al")||1,ap:$(this).attr("ap")||0,at:$(this).attr("at")||0,as:$(this).attr("as")||1,db:$(this).attr("db")||0
}

}
)
}
else{
if(Z==="DSURL"){
Y.dataServerURL=$(this).text()
}
else{
if(Z==="VALIST"){
$(this).children().each(function(){
Y.variableList[$(this).attr("key")]=$(this).text()
}
)
}
else{
if(Z==="IQLIST"){
var af=$(this).attr("an");
$(this).children().each(function(){
var ah=$(this).attr("va");
Y.inqueryAttrList[ah]={
id:$(this).attr("va"),va:$(this).attr("va"),ty:$(this).attr("ty")||"co",ca:$(this).attr("ca")||"",rq:$(this).attr("rq")||0,il:$(this).attr("il")||0,db:$(this).attr("db")||0,ds:$(this).attr("ds"),qr:$(this).attr("qr"),sp:$(this).attr("sp"),pv:$(this).attr("pv"),sd:$(this).attr("sd"),ad:$(this).attr("ad"),se:$(this).attr("se")||"@",sc:$(this).attr("sc"),ac:$(this).attr("ac"),cl:$(this).attr("cl")||i.language||"korean",mu:$(this).attr("mu"),pa:new Array(),an:af
}
;
$(this).children("IQSP_PALIST").each(function(){
var ai=0;
$(this).children().each(function(){
Y.inqueryAttrList[ah].pa[ai]=$(this).attr("va");
ai++
}
)
}
)
}
)
}
else{
if(Z==="IMLIST"){
$(this).children().each(function(){
var ai=$(this);
var ah=Y.indexedImageList[ai.attr("id")]=new Image();
ah.onload=ah.onerror=ah.onabort=function(){
this.finished=true
}
;
ah.src=ai.attr("va");
if(B.debug){
j.log("[indexed image] "+ai.attr("id")+" : "+ai.attr("va"))
}

}
)
}
else{
if(Z==="FLLIST"){
$(this).children().each(function(){
n.layerManager.createLayer($(this))
}
)
}
else{
if(Z==="HLCOLOR"){
var ad=['<style class="crownix-hyperlink-style" type="text/css">'];
var ac=$(this).attr("hu");
var ag=(ac==="1"||ac==="2")?"text-decoration: none;
":"";
var ae=ac==="2"?"text-decoration: underline;
":"";
ad.push("#m2soft-crownix-text a:link {
 color:"+$(this).attr("lc")+";
 "+ag+"
}
");
ad.push("#m2soft-crownix-text a:visited {
 color:"+$(this).attr("vc")+";

}
");
ad.push("#m2soft-crownix-text a:hover {
 color:"+$(this).attr("hc")+";
 "+ae+"
}
");
ad.push("</style>");
$(ad.join("\n")).appendTo("head")
}

}

}

}

}

}

}

// -----------------------------------------------------------
// 9. Canvas 저수준 그리기 함수들 (this.drawLine, drawRect 등)
// -----------------------------------------------------------
var c=d.getContext("2d");
var E=n;
var A=(typeof c.setLineDash==="function");
var G=0;
var D=1;
var C=2;
var w=3;
var s=4;
var M=[2,2];
var z=[2,2];
var t=[10,2,2,2];
var h=[10,2,2,2,2,2];
var u,B,b,f,L;
var r=0,j,q=1,g=0;
var I={
pattern1:function(N,O){
img=document.createElement("canvas");
img.width=1;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(0,-0.5);
imgCtx.lineTo(0,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern2:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=1;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(-0.5,0);
imgCtx.lineTo(0.5,0);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern3:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(4.5,-0.5);
imgCtx.lineTo(-0.5,4.5);
imgCtx.moveTo(8.5,3.5);
imgCtx.lineTo(3.5,8.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern4:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(4.5,4.5);
imgCtx.lineTo(-0.5,-0.5);
imgCtx.moveTo(8.5,8.5);
imgCtx.lineTo(3.5,3.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern5:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(0.5,9);
imgCtx.lineTo(0.5,0.5);
imgCtx.lineTo(9,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern6:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(-1,-1);
imgCtx.lineTo(9,9);
imgCtx.moveTo(-1,9);
imgCtx.lineTo(9,-1);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern7:function(N,O){
img=document.createElement("canvas");
img.width=8;
img.height=8;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(4.5,-0.5);
imgCtx.lineTo(4.5,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern8:function(N,O){
img=document.createElement("canvas");
img.width=4;
img.height=4;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(0.5,-0.5);
imgCtx.lineTo(0.5,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern9:function(N,O){
img=document.createElement("canvas");
img.width=4;
img.height=2;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(3.5,-0.5);
imgCtx.lineTo(3.5,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}
,pattern10:function(N,O){
img=document.createElement("canvas");
img.width=2;
img.height=2;
imgCtx=img.getContext("2d");
imgCtx.strokeStyle=N||"#000";
imgCtx.strokeWidth=1;
imgCtx.moveTo(0.5,-0.5);
imgCtx.lineTo(0.5,0.5);
imgCtx.stroke();
return c.createPattern(img,"repeat")
}

}
;
var k=function(O,N,Q,P){
c.lineCap="butt";
c.beginPath();
c.moveTo(O,N);
c.lineTo(Q,P)
}
;
var a=function(U,Q,ab,aa,N,Y){
var V=U,P=Q,Z=0,T=true,W=0,O=Math.atan2(aa-Q,ab-U);
var X=U<ab;
var S=Q<aa;
c.lineCap="round";
c.beginPath();
c.moveTo(U,Q);
while(X?ab>V:ab<V||S?aa>P:aa<P){
W=Y[Z]*N;
V=X?Math.min(V+(Math.cos(O)*W),ab):Math.max(V+(Math.cos(O)*W),ab);
P=S?Math.min(P+(Math.sin(O)*W),aa):Math.max(P+(Math.sin(O)*W),aa);
if(T){
c.lineTo(V,P)
}
else{
c.moveTo(V,P)
}
Z=(Z+1)%Y.length;
T=!T
}
c.closePath()
}
;
var m=function(P,Q){
c.beginPath();
c.moveTo(P[0][0],P[0][1]);
for(var O=1,N=P.length;
O<N;
O++){
c.lineTo(P[O][0],P[O][1])
}
if(Q){
c.closePath()
}

}
;
var i=function(Y,W,O,Q){
var V=Q.ap,X=Q.lc,S=Q.wd,N=Q.at,P=Q.as,U=(1+S/5)*8*P,T=N==1?U/Math.sqrt(3):U/3;
if(S==0||V==0){
return
}
c.save();
c.translate(Y,W);
c.rotate(O);
c.lineJoin="miter";
c.globalAlpha=1;
c.strokeStyle=X;
c.lineWidth=S;
if(A){
c.setLineDash([])
}
c.beginPath();
if(V==1||V==2||V==3){
c.moveTo(U,-T);
c.lineTo(0,0);
c.lineTo(U,T);
c.lineCap="round";
c.stroke()
}
else{
c.moveTo(0,0);
c.lineTo(U,-T);
c.lineTo(U,T);
c.lineTo(0,0);
c.closePath();
if(V==4||V==5||V==6){
c.fillStyle="#FFFFFF"
}
else{
c.fillStyle=c.strokeStyle
}
c.fill();
c.stroke()
}
c.restore()
}
;
var H=function(P,O){
if(!P){
return
}
var S=P.fc;
var N=P.pt;
var Q=P.al;
c.save();
c.fillStyle=S;
c.globalCompositeOperation="source-over";
c.globalAlpha=Q;
c.fill();
if(N!==0){
c.globalAlpha="1";
c.fillStyle=I["pattern"+N](O.lc);
c.fill()
}
c.restore()
}
;
var l=function(P){
if(!P){
return
}
var V=P.lc;
var O=P.wd;
var U=P.al;
if(O==0){
return
}
var T=[];
var S=[];
c.save();
if(A){
if(P.st==D){
T=M
}
else{
if(P.st==C){
T=z
}
else{
if(P.st==w){
T=t
}
else{
if(P.st==s){
T=h
}

}

}

}
for(var Q=0,N=T.length;
Q<N;
Q++){
S[Q]=T[Q]*O
}
c.setLineDash(S)
}
c.lineCap="round";
c.lineJoin="round";
c.strokeStyle=V;
c.lineWidth=O;
c.globalAlpha=U;
c.stroke();
c.restore()
}
;
this.setClip=function(N){
if(N==="1"){
c.save();
c.clip()
}
else{
if(N==="2"){
c.restore()
}

}

}
;
this.drawLine=function(T,S,P,O,N){
if(A){
k(T,S,P,O)
}
else{
if(N.st==G){
k(T,S,P,O)
}
else{
if(N.st==D){
a(T,S,P,O,N.wd,M)
}
else{
if(N.st==C){
a(T,S,P,O,N.wd,z)
}
else{
if(N.st==w){
a(T,S,P,O,N.wd,t)
}
else{
if(N.st==s){
a(T,S,P,O,N.wd,h)
}
else{
k(T,S,P,O)
}

}

}

}

}

}
l(N);
var Q=N.ap;
if(Q==1||Q==3||Q==4||Q==6||Q==7||Q==9){
i(T,S,Math.atan2(S-O,T-P)+Math.PI,N)
}
if(Q==2||Q==3||Q==5||Q==6||Q==8||Q==9){
i(P,O,Math.atan2(O-S,P-T)+Math.PI,N)
}

}
;
this.drawScribble=function(P,S){
var U,T;
var O=[];
c.beginPath();
c.moveTo(S[0][0],S[0][1]);
for(var Q=0,N=S.length-1;
Q<N;
Q++){
U=S[Q];
T=S[Q+1];
O[0]=(U[0]+T[0])/2;
O[1]=(U[1]+T[1])/2;
if(Q==0){
c.lineTo(O[0],O[1])
}
else{
c.quadraticCurveTo(U[0],U[1],O[0],O[1])
}
if(Q==N-1){
c.lineTo(T[0],T[1])
}

}
l(P)
}
;
this.drawRect=function(U,T,S,Q,P,W,V,N,O){
if(typeof V==="number"&&typeof N==="number"){
c.beginPath();
c.moveTo(U,T+N);
c.lineTo(U,Q-N);
if(O){
if(O&4){
c.quadraticCurveTo(U,Q,U+V,Q)
}
else{
c.lineTo(U,Q)
}
c.lineTo(S-V,Q);
if(O&8){
c.quadraticCurveTo(S,Q,S,Q-N)
}
else{
c.lineTo(S,Q)
}
c.lineTo(S,T+N);
if(O&2){
c.quadraticCurveTo(S,T,S-V,T)
}
else{
c.lineTo(S,T)
}
c.lineTo(U+V,T);
if(O&1){
c.quadraticCurveTo(U,T,U,T+N)
}
else{
c.lineTo(U,T)
}

}
else{
c.quadraticCurveTo(U,Q,U+V,Q);
c.lineTo(S-V,Q);
c.quadraticCurveTo(S,Q,S,Q-N);
c.lineTo(S,T+N);
c.quadraticCurveTo(S,T,S-V,T);
c.lineTo(U+V,T);
c.quadraticCurveTo(U,T,U,T+N)
}
c.closePath()
}
else{
c.beginPath();
c.moveTo(U,T);
c.lineTo(S,T);
c.lineTo(S,Q);
c.lineTo(U,Q);
c.lineTo(U,T);
c.closePath()
}
H(W,P);
l(P)
}
;
this.drawCylinder=function(W,V,T,Q,O,Y,U){
var P;
var N=T-W;
var X=Q-V;
var S=(X-U)/N;
c.save();
c.translate(W,V);
c.scale(1,S);
P=N/2;
c.beginPath();
c.arc(P,P,P,0,Math.PI*2,false);
c.lineTo(P*2,(X/S)-P);
c.arc(P,(X/S)-P,P,0,Math.PI,false);
c.lineTo(0,P);
c.restore();
H(Y,O);
l(O)
}
;
this.drawCurve=function(W,V,T,Q,P,U,O,S,N){
c.beginPath();
if(typeof S==="number"&&typeof N==="number"){
c.moveTo(W,V);
c.bezierCurveTo(U,O,S,N,T,Q)
}
else{
c.moveTo(W,V);
c.quadraticCurveTo(U,O,T,Q)
}
l(P);
var X=P.ap;
if(X==1||X==3||X==4||X==6||X==7||X==9){
i(W,V,Math.atan2(V-O,W-U)+Math.PI,P)
}
if(X==2||X==3||X==5||X==6||X==8||X==9){
i(T,Q,Math.atan2(Q-N,T-S)+Math.PI,P)
}

}
;
this.drawEllipse=function(U,T,S,Q,O,W){
var P;
var N=S-U;
var V=Q-T;
c.save();
c.translate(U+(N/2),T+(V/2));
if(N>V){
c.scale(1,V/N);
P=N/2
}
else{
c.scale(N/V,1);
P=V/2
}
c.beginPath();
c.arc(0,0,P,0,Math.PI*2,false);
c.restore();
H(W,O);
l(O)
}
;
this.drawDiamond=function(T,S,P,O,N,Q){
c.beginPath();
c.moveTo((T+P)/2,S);
c.lineTo(P,(S+O)/2);
c.lineTo((T+P)/2,O);
c.lineTo(T,(S+O)/2);
c.lineTo((T+P)/2,S);
c.closePath();
H(Q,N);
l(N)
}
;
this.drawParallelogram=function(W,V,S,P,O,Y,N){
N=W+N;
var X=[W,P];
var U=[S+W-N,P];
var T=[S,V];
var Q=[N,V];
c.beginPath();
c.moveTo(X[0],X[1]);
c.lineTo(U[0],U[1]);
c.lineTo(T[0],T[1]);
c.lineTo(Q[0],Q[1]);
c.lineTo(X[0],X[1]);
c.closePath();
H(Y,O);
l(O)
}
;
this.drawPolygon=function(N,P,O){
m(O,true);
H(P,N);
l(N)
}
;
this.drawPolyLine=function(N,O){
m(O,false);
l(N);
var P=N.ap;
if(P==1||P==3||P==4||P==6||P==7||P==9){
i(O[0][0],O[0][1],Math.atan2(O[0][1]-O[1][1],O[0][0]-O[1][0])+Math.PI,N)
}
if(P==2||P==3||P==5||P==6||P==8||P==9){
i(O[O.length-1][0],O[O.length-1][1],Math.atan2(O[O.length-1][1]-O[O.length-2][1],O[O.length-1][0]-O[O.length-2][0])+Math.PI,N)
}

}
;
this.drawHexaheron=function(aa,Y,V,T,O,N,P,ab){
O=aa+O;
N=Y+N;
var Z=[aa,T];
var X=[aa,N];
var W=[aa+V-O,Y];
var U=[V,Y];
var S=[V,T+Y-N];
var Q=[O,T];
c.beginPath();
c.moveTo(Z[0],Z[1]);
c.lineTo(X[0],X[1]);
c.lineTo(W[0],W[1]);
c.lineTo(U[0],U[1]);
c.lineTo(S[0],S[1]);
c.lineTo(Q[0],Q[1]);
c.lineTo(Z[0],Z[1]);
c.closePath();
H(ab,P);
l(P);
c.beginPath();
c.moveTo(Q[0],Q[1]);
c.lineTo(O,N);
c.lineTo(U[0],U[1]);
c.moveTo(O,N);
c.lineTo(X[0],X[1]);
l(P)
}
;
this.drawImage=function(T,O,N,P,S,Q){
if(Q=="1"){
c.save();
c.translate(O,N);
c.fillStyle=c.createPattern(T,"repeat");
c.fillRect(0,0,P,S);
c.restore()
}
else{
if(T.naturalWidth+T.naturalHeight>0){
c.drawImage(T,O,N,P,S)
}

}

}
;
this.measureText=function(P,O){
if(typeof O==="object"){
var N=[O.fontStyle,O.fontWeight,O.fontSize,O.fontFamily];
c.font=N.join(" ")
}
return c.measureText(P)
}
;
var F=function(N){
return Math.floor(N)+0.5
}
;
var K=function(Y,V,U,W,N){
var O=Y.split(""),S,Q=V,X=1;
if(c.textAlign==="right"){
O=O.reverse();
X=-1;
Q-=W
}
else{
if(c.textAlign==="center"){
Q=V-(N-W)/2;
c.textAlign="left"
}

}
for(var P=0,T=O.length;
P<T;
P++){
S=O[P];
c.fillText(S,Q,U);
Q+=X*(c.measureText(S).width+W)
}

}
;
this.drawText=function(ac,Z,X,V,T,N){
if(!ac||typeof ac!=="string"){
return
}
c.save();
var Q;
if(typeof N==="object"){
Q=[N.fontStyle,N.fontWeight,N.fontSize,N.fontFamily];
c.font=Q.join(" ");
c.textBaseline=N.textBaseline;
c.textAlign=N.textAlign;
c.fillStyle=c.strokeStyle=N.fillStyle
}
var aa=(typeof N.letterSpacing==="number")?N.letterSpacing:0;
var O=c.measureText(ac).width+(aa*ac.length);
var ab=J.ptToPx(parseFloat(N.fontSize));
if(N.adjustFontSize){
var U=O/(V-Z);
if(U>1){
var ad=parseInt(Q[2]);
Q[2]=(ad/U)+"pt";
c.font=Q.join(" ")
}

}
var Y,W,S,P;
if(N.textAlign==="center"){
Y=Z+(V-Z)/2;
S=Y-(O-aa)/2
}
else{
if(N.textAlign==="right"){
Y=V;
S=Y-O
}
else{
Y=S=Z
}

}
if(N.textBaseline==="middle"){
W=X+(T-X)/2;
P=W-ab/2
}
else{
if(N.textBaseline==="bottom"){
W=T;
P=W-ab
}
else{
W=P=X
}

}
c.beginPath();
c.rect(Z-2,X-2,V-Z+4,T-X+4);
c.clip();
c.closePath();
if(N.fontScale&&N.fontScale!=1){
c.translate(Y,W);
c.scale(N.fontScale,1);
S-=Y;
P-=W;
Y=W=0
}
else{
if(N.verticalWriting){
c.translate(Z+(V-Z)/2,X);
c.rotate(90*Math.PI/180);
S-=Y;
P-=W;
Y=W=0
}

}
if(N.shade){
c.fillStyle=c.strokeStyle="#000000";
c.fillRect(F(S),F(P)-1,F(O)+1,F(ab));
c.fillStyle=c.strokeStyle=(N.fillStyle==="#000000")?"#ffffff":N.fillStyle
}
if(aa!==0){
K(ac,Y,W,N.letterSpacing,O)
}
else{
if(N.adjustFontScale){
c.fillText(ac,Y,W,V-Z)
}
```
