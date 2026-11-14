package com.example.peachauction

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** 연동 확인용 화면. 실제 앱에서는 이 화면 대신 기존 UI에서 클라이언트를 호출해도 됩니다. */
class PeachAuctionActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var resultView: TextView
    private lateinit var refreshButton: Button
    private lateinit var nextButton: Button
    private var pageNo = 1
    private var date = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultView = TextView(this).apply { textSize = 16f; setPadding(24, 24, 24, 24) }
        refreshButton = Button(this).apply { text = "오늘 경매 새로고침" }
        nextButton = Button(this).apply { text = "다음 페이지"; isEnabled = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(refreshButton)
            addView(nextButton)
            addView(ScrollView(this@PeachAuctionActivity).apply { addView(resultView) })
        }
        setContentView(content)
        refreshButton.setOnClickListener { date = PeachAuctionClient.todayInSeoul(); load(1) }
        nextButton.setOnClickListener { load(pageNo + 1) }
        date = PeachAuctionClient.todayInSeoul()
        load(1)
    }

    private fun load(requestedPage: Int) {
        refreshButton.isEnabled = false
        nextButton.isEnabled = false
        resultView.text = "조회 중입니다…"
        executor.execute {
            try {
                val page = PeachAuctionClient(BuildConfig.DATA_GO_KR_KEY).fetchPage(
                    settlementDate = date, pageNo = requestedPage
                    // 백도만 보려면 varietyCode = "11"을 전달하세요.
                )
                val display = buildString {
                    append("정산일 ${page.settlementDate} · ${page.pageNo}페이지\n")
                    append("복숭아 전체 ${page.totalCount}건 중 이 페이지 ${page.scannedCount}건 확인\n")
                    append("이 페이지 복숭아 ${page.auctions.size}건\n\n")
                    if (page.auctions.isEmpty()) append("이 페이지에 복숭아 거래가 없습니다.\n")
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

