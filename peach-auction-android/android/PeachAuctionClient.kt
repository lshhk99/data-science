package com.example.peachauction

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 사용자에게 보여줄 이름과 공공데이터의 품종 코드를 연결합니다. */
enum class PeachVariety(val displayName: String, internal val apiCode: String?) {
    ALL("전체 복숭아", null),
    YELLOW_PEACH("황도", "10"),
    WHITE_PEACH("백도", "11"),
    YUMYEONG("유명", "08"),
    GYEONGBONG("경봉", "57"),
    CHEONJUNGDO_WHITE("천중도백도", "21"),
    OTHER("기타", "99");

    override fun toString(): String = displayName
}

/** 동기 API: 반드시 작업 스레드에서 호출하세요. 인증키는 일반 인증키(Decoding)입니다. */
class PeachAuctionClient(private val serviceKey: String) {
    data class Auction(
        val auctionTime: String,
        val settlementDate: String,
        val market: String,
        val corporation: String,
        val item: String,
        val variety: String,
        val priceWon: String,
        val unitQuantity: String,
        val unit: String,
        val packaging: String,
        val grade: String,
        val origin: String
    )

    data class Page(
        val settlementDate: String,
        val pageNo: Int,
        val totalCount: Int,
        val scannedCount: Int,
        val auctions: List<Auction>,
        val hasNext: Boolean
    )

    /** 날짜는 거래정산일입니다. 응답 순서는 API가 정하며 최신순을 보장하지 않습니다. */
    @Throws(IOException::class)
    fun fetchPage(
        settlementDate: String = todayInSeoul(),
        pageNo: Int = 1,
        rows: Int = 100,
        marketCode: String? = null,
        variety: PeachVariety = PeachVariety.ALL
    ): Page {
        require(serviceKey.isNotBlank()) { "공공데이터포털 일반 인증키(Decoding)가 필요합니다." }
        require(pageNo >= 1 && rows in 1..1000) { "페이지는 1 이상, 한 페이지 건수는 1~1000이어야 합니다." }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsedDate = try { dateFormat.parse(settlementDate) } catch (_: Exception) { null }
        require(parsedDate != null && dateFormat.format(parsedDate) == settlementDate) { "날짜는 YYYY-MM-DD 형식이어야 합니다." }

        val params = linkedMapOf(
            "serviceKey" to serviceKey.trim(),
            "returnType" to "json",
            "pageNo" to pageNo.toString(),
            "numOfRows" to rows.toString(),
            "cond[trd_clcln_ymd::EQ]" to settlementDate,
            "cond[gds_lclsf_cd::EQ]" to "06",
            "cond[gds_mclsf_cd::EQ]" to "04"
        )
        marketCode?.takeIf { it.isNotBlank() }?.let { params["cond[whsl_mrkt_cd::EQ]"] = it }
        variety.apiCode?.let { params["cond[gds_sclsf_cd::EQ]"] = it }
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection = URL("https://apis.data.go.kr/B552845/katRealTime2/trades2?$query")
            .openConnection() as HttpURLConnection
        val text = try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("공공데이터 API HTTP 오류: $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            // URL과 인증키가 포함될 수 있는 원본 예외 메시지는 외부에 전달하지 않습니다.
            throw IOException("경매정보 통신에 실패했습니다. 네트워크와 인증 상태를 확인하세요.")
        } finally {
            connection.disconnect()
        }
        return parsePage(text, settlementDate, pageNo, rows)
    }

    internal fun parsePage(text: String, date: String, pageNo: Int, rows: Int): Page {
        val root = try { JSONObject(text) } catch (_: Exception) {
            throw IOException("API가 JSON이 아닌 응답을 반환했습니다. 인증키와 활용 승인을 확인하세요.")
        }
        val response = root.optJSONObject("response")
            ?: throw IOException("API 응답에 response가 없습니다.")
        val header = response.optJSONObject("header")
        val code = header?.optString("resultCode", "") ?: ""
        if (code.isNotEmpty() && code != "00" && code != "0000") {
            throw IOException("공공데이터 API 오류 코드: $code")
        }
        val body = response.optJSONObject("body") ?: throw IOException("API 응답에 body가 없습니다.")
        val total = body.optString("totalCount", "").toIntOrNull()
            ?: throw IOException("API 응답에 전체 건수가 없습니다.")
        if (total < 0) throw IOException("API 전체 건수가 올바르지 않습니다.")
        val container = body.opt("items")
        val raw = when (container) {
            null, JSONObject.NULL -> null
            is JSONObject -> container.opt("item")
            is JSONArray -> container
            is String -> if (container.isBlank()) null else throw IOException("API 항목 형식이 올바르지 않습니다.")
            else -> throw IOException("API 항목 형식이 올바르지 않습니다.")
        }
        val items = when (raw) {
            null, JSONObject.NULL -> emptyList()
            is JSONObject -> listOf(raw)
            is JSONArray -> (0 until raw.length()).map { index ->
                raw.optJSONObject(index) ?: throw IOException("API 경매 항목 형식이 올바르지 않습니다.")
            }
            is String -> if (raw.isBlank()) emptyList() else throw IOException("API 경매 항목 형식이 올바르지 않습니다.")
            else -> throw IOException("API 경매 항목 형식이 올바르지 않습니다.")
        }
        val auctions = items.map { item ->
            Auction(
                auctionTime = item.value("scsbd_dt"), settlementDate = item.value("trd_clcln_ymd"),
                market = item.value("whsl_mrkt_nm"), corporation = item.value("corp_nm"),
                item = item.value("gds_mclsf_nm"), variety = item.value("gds_sclsf_nm"),
                priceWon = item.value("scsbd_prc"), unitQuantity = item.value("unit_qty"),
                unit = item.value("unit_nm"), packaging = item.value("pkg_nm"),
                grade = item.value("grd_nm"), origin = item.value("plor_nm")
            )
        }
        // 빈 페이지가 예상보다 빨리 반환된 경우, 완료로 오인하지 않도록 오류를 냅니다.
        if (items.size < rows && pageNo.toLong() * rows < total) {
            throw IOException("API가 요청한 건수보다 적은 항목을 반환했습니다. 한 페이지 건수를 줄여 다시 시도하세요.")
        }
        return Page(date, pageNo, total, items.size, auctions, pageNo.toLong() * rows < total)
    }

    private fun JSONObject.value(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    companion object {
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        fun todayInSeoul(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Seoul")
        }.format(Date())
    }
}

