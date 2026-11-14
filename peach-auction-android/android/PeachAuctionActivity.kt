package com.example.peachauction

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** 연동 확인용 화면. 실제 앱에서는 이 화면 대신 기존 UI에서 클라이언트를 호출해도 됩니다. */
class PeachAuctionActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var resultView: TextView
    private lateinit var refreshButton: Button
    private lateinit var nextButton: Button
    private lateinit var varietySpinner: Spinner
    private var pageNo = 1
    private var date = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultView = TextView(this).apply { textSize = 16f; setPadding(24, 24, 24, 24) }
        refreshButton = Button(this).apply { text = "오늘 경매 새로고침" }
        nextButton = Button(this).apply { text = "다음 페이지"; isEnabled = false }
        varietySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PeachAuctionActivity,
                android.R.layout.simple_spinner_dropdown_item,
                PeachVariety.values().toList()
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@PeachAuctionActivity).apply { text = "복숭아 품종 선택" })
            addView(varietySpinner)
            addView(refreshButton)
            addView(nextButton)
            addView(ScrollView(this@PeachAuctionActivity).apply { addView(resultView) })
        }
        setContentView(content)
        refreshButton.setOnClickListener { date = PeachAuctionClient.todayInSeoul(); load(1) }
        nextButton.setOnClickListener { load(pageNo + 1) }
        date = PeachAuctionClient.todayInSeoul()
        varietySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                date = PeachAuctionClient.todayInSeoul()
                load(1)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun load(requestedPage: Int) {
        refreshButton.isEnabled = false
        nextButton.isEnabled = false
        varietySpinner.isEnabled = false
        resultView.text = "조회 중입니다…"
        val selectedVariety = varietySpinner.selectedItem as PeachVariety
        val selectedDate = date
        executor.execute {
            try {
                val page = PeachAuctionClient(BuildConfig.DATA_GO_KR_KEY).fetchPage(
                    settlementDate = selectedDate, pageNo = requestedPage, variety = selectedVariety
                )
                val fetchedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Seoul")
                }.format(Date())
                val display = buildString {
                    append("${selectedVariety.displayName} · 조회 시각 $fetchedAt\n")
                    append("정산일 ${page.settlementDate} · ${page.pageNo}페이지\n")
                    append("선택한 조건의 전체 ${page.totalCount}건 중 이 페이지 ${page.scannedCount}건\n\n")
                    if (page.auctions.isEmpty()) {
                        append(if (page.pageNo == 1) "선택한 품종의 오늘 거래 정보가 없습니다.\n" else "이 페이지에 거래 정보가 없습니다.\n")
                    }
                    page.auctions.forEach { auction ->
                        append("${auction.auctionTime}  ${auction.market}\n")
                        val price = if (auction.priceWon.isBlank()) "가격 정보 없음" else "${auction.priceWon}원"
                        append("${auction.item} ${auction.variety}  $price\n")
                        append("규격 ${auction.unitQuantity} ${auction.unit} ${auction.packaging} · 등급 ${auction.grade}\n")
                        append("산지 ${auction.origin} · 법인 ${auction.corporation}\n\n")
                    }
                    if (page.hasNext) append("다음 페이지에서 계속 확인하세요.")
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        pageNo = page.pageNo
                        resultView.text = display
                        refreshButton.isEnabled = true
                        varietySpinner.isEnabled = true
                        nextButton.isEnabled = page.hasNext
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val message = if (e is java.io.IOException || e is IllegalArgumentException) {
                            e.message ?: "알 수 없는 오류"
                        } else "설정을 확인하고 다시 시도하세요."
                        resultView.text = "조회하지 못했습니다: $message"
                        refreshButton.isEnabled = true
                        varietySpinner.isEnabled = true
                        // 실패한 페이지를 다시 요청할 수 있습니다.
                        nextButton.isEnabled = requestedPage > 1
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

