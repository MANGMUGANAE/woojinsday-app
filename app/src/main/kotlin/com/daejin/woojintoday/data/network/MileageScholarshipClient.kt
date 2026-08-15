package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.ZipInputStream

private const val TAG = "MileageScholarshipClient"

/** 게시글 상세페이지의 첨부파일 영역에서만 얻을 수 있는 xlsx 대상자 명단의 다운로드 링크. */
data class MileageAttachment(val downloadUrl: String)

sealed class MileageEligibility {
    data object Found : MileageEligibility()
    /** [values]는 명단 xlsx에서 읽어낸 셀 값 전체 — "명단보기"에서 그대로 보여주고 검색도 할 수 있다. */
    data class NotFound(val values: List<String>) : MileageEligibility()
    data class Error(val message: String) : MileageEligibility()
}

/**
 * 189번 게시판(공지사항 통합) 전체를 제목검색("마일리지장학")으로 훑어 마일리지 장학 관련 글만 걸러온다.
 * 목록 조회는 www.daejin.ac.kr의 SSO 리다이렉트 체인(checkToken.do -> nsso -> 원래 주소)을 타야
 * 실제 검색 결과가 나온다(체인 없이 바로 artclList.do를 두드리면 검색어를 무시하고 상단 고정글만
 * 내려준다) — NoticeClient와 동일한 이유로 자체 CookieJar가 필요하다. 반면 글 상세보기와 첨부파일
 * 다운로드는 로그인/세션 없이도 바로 열린다(실측 확인됨).
 */
class MileageScholarshipClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun fetchNotices(): NoticeListResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(buildSearchUrl())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val notices = parseNotices(html)
                Log.d(TAG, "마일리지 검색 응답 코드=${response.code}, 파싱된 공지 수=${notices.size}")
                NoticeListResult.Success(notices)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            NoticeListResult.NetworkError("마일리지 장학 공지를 불러오지 못했습니다.")
        }
    }

    /** 상세페이지에 xlsx 첨부(대상자 명단)가 있는 글만 다운로드/미리보기 링크를 돌려주고,
     *  그 외(시행 안내 글 등 첨부가 없거나 xlsx가 아닌 글)는 null. */
    suspend fun findXlsxAttachment(detailPath: String): MileageAttachment? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BASE_URL + detailPath)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val doc = Jsoup.parse(html)
                val downloadLink = doc.selectFirst("a.file-download") ?: return@withContext null
                val fileName = downloadLink.text().trim()
                if (!fileName.endsWith(".xlsx", ignoreCase = true)) return@withContext null
                val downloadHref = downloadLink.attr("href")
                MileageAttachment(downloadUrl = BASE_URL + downloadHref)
            }
        } catch (e: IOException) {
            Log.e(TAG, "첨부파일 확인 실패: $detailPath", e)
            null
        }
    }

    /** [downloadUrl]의 xlsx를 받아 셀 값 중 [studentNo]와 정확히 일치하는 게 있는지 검사한다. */
    suspend fun checkEligibility(downloadUrl: String, studentNo: String): MileageEligibility =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                } ?: return@withContext MileageEligibility.Error("명단 파일을 받지 못했습니다.")

                val values = parseXlsxValues(bytes)
                if (values.contains(studentNo)) {
                    MileageEligibility.Found
                } else {
                    MileageEligibility.NotFound(values)
                }
            } catch (e: IOException) {
                Log.e(TAG, "명단 다운로드 실패", e)
                MileageEligibility.Error("명단 파일을 받지 못했습니다.")
            }
        }

    private fun parseNotices(html: String): List<Notice> {
        val document = Jsoup.parse(html)
        return document.select("table.board-table tbody tr").mapNotNull { row ->
            val link = row.selectFirst("td.td-subject a") ?: return@mapNotNull null
            val title = (link.selectFirst("strong")?.text() ?: link.ownText()).trim()
            if (title.isBlank() || "마일리지" !in title) return@mapNotNull null

            Notice(
                title = title,
                writer = row.selectFirst("td.td-write")?.text()?.trim().orEmpty(),
                date = row.selectFirst("td.td-date")?.text()?.trim().orEmpty(),
                views = row.selectFirst("td.td-access")?.text()?.trim().orEmpty(),
                hasAttachment = row.selectFirst("td.td-file p.file-y") != null,
                isNew = link.selectFirst("span.new") != null,
                detailPath = link.attr("href")
            )
        }
    }

    /** www.daejin.ac.kr의 CMS 프론트컨트롤러(subview.do?enc=...)를 통해 189번 게시판을 검색어와 함께
     *  호출하는 URL을 만든다 — LectureTimetableNoticeClient가 1573번 게시판에 쓰는 것과 같은
     *  enc 인코딩 규칙("fnct1|@@|" + 전체 경로+쿼리스트링을 통째로 한 번 URL인코딩 + base64). */
    private fun buildSearchUrl(): String {
        val innerPath = "/bbs/daejin/189/artclList.do?bbsClSeq=&bbsOpenWrdSeq=&isViewMine=false" +
            "&srchColumn=sj&srchWrd=${URLEncoder.encode(SEARCH_WORD, "UTF-8")}&"
        val combined = "fnct1|@@|" + URLEncoder.encode(innerPath, "UTF-8")
        val enc = Base64.getEncoder().encodeToString(combined.toByteArray(Charsets.UTF_8))
        return HttpUrl.Builder()
            .scheme("https")
            .host("www.daejin.ac.kr")
            .addPathSegments("daejin/1002/subview.do")
            .addQueryParameter("enc", enc)
            .build()
            .toString()
    }

    companion object {
        const val BASE_URL = "https://www.daejin.ac.kr"
        private const val SEARCH_WORD = "마일리지장학"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(MileageScholarshipCookieJar())
                .build()
        }
    }
}

/** xlsx(zip 안에 xl/worksheets/sheet*.xml + xl/sharedStrings.xml)를 풀어 안에 있는 셀 값을 전부
 *  꺼낸다. Apache POI 등 무거운 라이브러리 없이, xlsx가 결국 zip+xml이라는 점만 이용한 최소 구현 —
 *  시트가 여러 개일 수 있어 전부 훑고, 셀 값이 숫자로 바로 박혀있는 경우와 공유 문자열 테이블
 *  (sharedStrings)을 참조하는 경우(t="s") 둘 다 처리한다. */
private fun parseXlsxValues(xlsxBytes: ByteArray): List<String> {
    val sharedStrings = mutableListOf<String>()
    val sheetXmls = mutableListOf<String>()

    ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            when {
                entry.name == "xl/sharedStrings.xml" -> {
                    sharedStrings += parseSharedStrings(zip.readBytes().toString(Charsets.UTF_8))
                }
                entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) -> {
                    sheetXmls += zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            entry = zip.nextEntry
        }
    }

    return sheetXmls.flatMap { sheetXml -> extractCellValues(sheetXml, sharedStrings) }
}

private val CELL_BLOCK_REGEX = Regex("<c\\b([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
private val CELL_VALUE_REGEX = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
private val SHARED_STRING_ITEM_REGEX = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
private val TEXT_RUN_REGEX = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

private fun extractCellValues(sheetXml: String, sharedStrings: List<String>): List<String> =
    CELL_BLOCK_REGEX.findAll(sheetXml).mapNotNull { cell ->
        val attrs = cell.groupValues[1]
        val inner = cell.groupValues[2]
        val rawValue = CELL_VALUE_REGEX.find(inner)?.groupValues?.get(1)?.trim()
        if (rawValue.isNullOrEmpty()) return@mapNotNull null
        if ("t=\"s\"" in attrs) {
            rawValue.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: rawValue
        } else {
            rawValue
        }
    }.toList()

private fun parseSharedStrings(xml: String): List<String> =
    SHARED_STRING_ITEM_REGEX.findAll(xml).map { si ->
        TEXT_RUN_REGEX.findAll(si.groupValues[1]).joinToString("") { it.groupValues[1] }.unescapeXmlEntities()
    }.toList()

private fun String.unescapeXmlEntities(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

/** 검색 시 거치는 SSO 리다이렉트 체인이 쿠키를 넘겨받아야 끝나므로 NoticeClient와 동일한 최소
 *  호스트별 CookieJar가 필요하다 — 상세보기/다운로드는 쿠키가 없어도 그대로 동작한다. */
private class MileageScholarshipCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val existing = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]?.filter { it.expiresAt > now } ?: emptyList()
    }
}
