package com.minyook.overnight.ui.file

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.R
import com.minyook.overnight.data.model.AnalysisResult
import com.minyook.overnight.data.model.CriterionResult
import com.google.gson.Gson
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.widget.FrameLayout
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream

class AnalysisResultActivity : AppCompatActivity() {

    // 로컬 저장소 관련 상수 및 객체
    private val PREFS_NAME = "AnalysisPrefs"
    private val KEY_ANALYSIS_RESULT = "analysis_result_json"
    private val gson = Gson()

    // 뷰 객체
    private lateinit var tvTotalScore: TextView
    private lateinit var tvCenterScoreValue: TextView
    private lateinit var pieChart: PieChart
    private lateinit var recyclerCriteriaTop: RecyclerView
    private lateinit var recyclerCriteriaBottom: RecyclerView
    private lateinit var tvFeedbackArea: TextView
    private lateinit var layoutFeedback: LinearLayout
    private lateinit var btnDownloadExcel: Button
    private lateinit var btnDownloadPdf: Button
    private lateinit var btnMyPage: Button
    private lateinit var donutChartContainer: FrameLayout
    private lateinit var tvTotalSummary: TextView

    // analysisResult는 널 허용 타입으로 변경 (로딩 실패 시를 대비)
    private var analysisResult: AnalysisResult? = null
    private val feedbackBuilder = StringBuilder()

    // Presentation 및 Upload 정보 (로컬 저장을 위한 데이터 클래스)
    data class PresentationInfo(val title: String, val date: String, val criteria: List<String>)
    data class UploadInfo(val fileName: String, val fileSize: Long, val uploadDate: String)
    private val KEY_PRESENTATION_INFO = "presentation_info_json"
    private val KEY_UPLOAD_INFO = "upload_info_json"

    // ⭐️ 파일 다운로드를 위한 변수 및 Launcher ⭐️
    private var lastGeneratedFileContent: String? = null // Launcher 실행 직전에 저장할 내용
    private var lastGeneratedFileType: FileType? = null

    private val fileSaveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = lastGeneratedFileContent
            val type = lastGeneratedFileType

            if (content != null && type != null) {
                writeContentToFile(it, content, type)
            } else {
                Toast.makeText(this, "파일 내용이 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "파일 저장이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_result)

        // 뷰 초기화
        tvTotalScore = findViewById(R.id.tv_total_score)
        tvCenterScoreValue = findViewById(R.id.tv_center_score_value)
        pieChart = findViewById(R.id.pie_chart)
        recyclerCriteriaTop = findViewById(R.id.recycler_criteria_top)
        recyclerCriteriaBottom = findViewById(R.id.recycler_criteria_bottom)
        tvFeedbackArea = findViewById(R.id.tv_feedback_area)
        layoutFeedback = findViewById(R.id.layout_feedback_container)
        btnDownloadExcel = findViewById(R.id.btn_download_excel)
        btnDownloadPdf = findViewById(R.id.btn_download_pdf)
        btnMyPage = findViewById(R.id.btn_my_page)
        donutChartContainer = findViewById(R.id.donut_chart_container)
        tvTotalSummary = findViewById(R.id.tv_total_summary)

        // 1. 로컬 데이터 로드 시도 (우선 순위)
        val loadedResult = loadAnalysisResult()
        if (loadedResult != null) {
            analysisResult = loadedResult
            Log.d("Storage", "Loaded data from local storage.")
            displayResults()
        } else {
            // 로컬 데이터 없으면 API 호출 시뮬레이션 (데이터 생성 및 저장)
            Log.d("Storage", "No local data found. Simulating API call for initial data.")
            callGeminiApiForAnalysis()
        }

        // 2. 다운로드 버튼 리스너
        btnDownloadExcel.setOnClickListener { downloadFile(FileType.EXCEL) }
        btnDownloadPdf.setOnClickListener { downloadFile(FileType.PDF) }

        // 3. 마이페이지 버튼 리스너
        btnMyPage.setOnClickListener {
            Toast.makeText(this, "마이페이지로 이동합니다.", Toast.LENGTH_SHORT).show()
        }

        // ⭐️ 4. 총점 요약 영역 클릭 리스너 추가
        tvTotalSummary.setOnClickListener {
            handleTotalSummaryClick()
        }
    }

    // -----------------------------------------------------
    // 💾 로컬 저장소 함수 (분석 결과 저장/로드)
    // -----------------------------------------------------

    private fun saveAnalysisResult(result: AnalysisResult) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = gson.toJson(result)
        prefs.edit().putString(KEY_ANALYSIS_RESULT, jsonString).apply()
    }

    private fun loadAnalysisResult(): AnalysisResult? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_ANALYSIS_RESULT, null)
        return if (jsonString != null) {
            gson.fromJson(jsonString, AnalysisResult::class.java)
        } else {
            null
        }
    }

    fun savePresentationInfo(info: PresentationInfo) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = gson.toJson(info)
        prefs.edit().putString(KEY_PRESENTATION_INFO, jsonString).apply()
    }

    fun saveUploadInfo(info: UploadInfo) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = gson.toJson(info)
        prefs.edit().putString(KEY_UPLOAD_INFO, jsonString).apply()
    }

    // -----------------------------------------------------
    // ⭐️ 모의 데이터 생성 함수 (재사용성)
    // -----------------------------------------------------

    private fun generateMockAnalysisResult(): AnalysisResult {
        // 이 데이터가 로컬에 저장되는 초기값이 됩니다.
        val resultsList = listOf(
            CriterionResult(
                criterionName = "논리성",
                maxScore = 20,
                actualScore = 18,
                feedback = "발표의 도입부와 결론이 명확하게 연결되어 청중의 이해를 도왔습니다. 다만, 주요 근거 중 한 부분이 다소 약했습니다."
            ),
            CriterionResult(
                criterionName = "발표 태도",
                maxScore = 30,
                actualScore = 25,
                feedback = "시선 처리가 좋았고 자신감이 느껴졌습니다. 하지만 몇 차례 말을 더듬는 구간이 있어 유창성을 약간 저해했습니다."
            ),
            CriterionResult(
                criterionName = "시각 자료",
                maxScore = 50,
                actualScore = 45,
                feedback = "슬라이드 디자인이 깔끔하고 정보 밀도가 적절했습니다. 이미지 출처를 명확히 표기했다면 완벽했을 것입니다."
            )
        )

        return AnalysisResult(
            totalMaxScore = 100,
            totalActualScore = 88,
            results = resultsList
        )
    }


    // -----------------------------------------------------
    // 🔴 Gemini API 호출 (로컬 데이터 없을 때 초기값 생성)
    // -----------------------------------------------------

    private fun callGeminiApiForAnalysis() = lifecycleScope.launch(Dispatchers.IO) {
        Log.d("API_CALL", "로컬 데이터가 없어 초기 모의 데이터 생성 영역으로 진입.")

        delay(1000) // API 호출 지연 시뮬레이션

        launch(Dispatchers.Main) {
            try {
                // 모의 데이터 생성 및 할당
                val mockResult = generateMockAnalysisResult()
                analysisResult = mockResult

                // 생성된 결과를 로컬에 저장하고 화면에 표시
                saveAnalysisResult(mockResult)
                displayResults()

            } catch (e: Exception) {
                Log.e("Gemini", "모의 데이터 생성 및 UI 업데이트 실패: ${e.message}")
                Toast.makeText(this@AnalysisResultActivity, "데이터 생성 및 UI 업데이트 실패", Toast.LENGTH_LONG).show()
                // 실패 시 UI를 빈 상태로 둠
                tvTotalScore.text = "총점: Error"
                tvCenterScoreValue.text = "--"
                pieChart.visibility = View.GONE
            }
        }
    }


    // -----------------------------------------------------
    // 🔴 UI 표시 및 도넛 차트 로직
    // -----------------------------------------------------

    private fun displayResults() {
        val result = analysisResult ?: return

        tvTotalScore.text = "총점: ${result.totalActualScore}/${result.totalMaxScore}점"
        tvCenterScoreValue.text = "${result.totalActualScore}점"

        pieChart.visibility = View.VISIBLE
        setupDonutChart(result.results, result.totalMaxScore)
        setupCriteriaList(result.results)

        tvTotalSummary.text = createSummaryReview(result)

        feedbackBuilder.clear()
        tvFeedbackArea.text = ""
        layoutFeedback.visibility = View.GONE
    }

    /**
     * ⭐️ 총평 텍스트를 생성하는 함수 (로컬 데이터 기반의 총평 생성)
     */
    private fun createSummaryReview(result: AnalysisResult): String {
        val count = result.results.size
        val totalScore = result.totalActualScore

        val summary = StringBuilder()
        summary.append("총평: 발표 분석이 완료되었습니다.\n")
        summary.append("총점은 ${totalScore}/${result.totalMaxScore}점으로 우수합니다.\n")
        summary.append("주요 ${count}개 항목에 대해 긍정적인 평가를 받았습니다. 개선이 필요한 항목은 상세 피드백을 확인하세요.")

        return summary.toString()
    }

    private fun setupDonutChart(results: List<CriterionResult>, totalMaxScore: Int) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        val actualScores = results.sumOf { it.actualScore }
        val remainingScore = totalMaxScore - actualScores

        val itemColors = listOf(
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FFC107"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#FF5722")
        )

        results.forEachIndexed { index, result ->
            entries.add(PieEntry(result.actualScore.toFloat(), result.criterionName))
            colors.add(itemColors.getOrElse(index) { Color.GRAY })
        }

        if (remainingScore > 0) {
            entries.add(PieEntry(remainingScore.toFloat(), "남은 점수"))
            colors.add(Color.parseColor("#E0E0E0")) // 회색
        }

        val dataSet = PieDataSet(entries, "분석 결과").apply {
            this.colors = colors
            sliceSpace = 2f
            setDrawValues(false)
        }

        val data = PieData(dataSet)

        pieChart.data = data
        pieChart.setUsePercentValues(false)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = true
        pieChart.setDrawEntryLabels(false)

        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 70f
        pieChart.transparentCircleRadius = 73f
        pieChart.setHoleColor(Color.TRANSPARENT)

        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    private fun setupCriteriaList(results: List<CriterionResult>) {
        val splitIndex = 2
        val topList = results.take(splitIndex)
        val bottomList = results.drop(splitIndex)

        // 상위 리스트 (항목 1~2)
        recyclerCriteriaTop.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val topAdapter = CriteriaListAdapter(topList) { criterionResult ->
            handleCriteriaClick(criterionResult)
        }
        recyclerCriteriaTop.adapter = topAdapter

        // 하위 리스트 (항목 3~n)
        recyclerCriteriaBottom.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val bottomAdapter = CriteriaListAdapter(bottomList) { criterionResult ->
            handleCriteriaClick(criterionResult)
        }
        recyclerCriteriaBottom.adapter = bottomAdapter
    }

    /**
     * 총점 요약 칸 클릭 시 모든 항목의 상세 피드백을 누적/토글하여 표시합니다.
     */
    private fun handleTotalSummaryClick() {
        val result = analysisResult ?: return
        val totalFeedbackText = createTotalFeedbackString(result).trim()

        if (tvFeedbackArea.text.toString().trim() == totalFeedbackText) {
            feedbackBuilder.clear()
            tvFeedbackArea.text = ""
            layoutFeedback.visibility = View.GONE
            Toast.makeText(this, "전체 상세 피드백 숨김", Toast.LENGTH_SHORT).show()
        } else {
            feedbackBuilder.clear().append(totalFeedbackText)
            tvFeedbackArea.text = totalFeedbackText
            layoutFeedback.visibility = View.VISIBLE
            Toast.makeText(this, "전체 상세 피드백 표시", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 전체 피드백 내용을 생성합니다.
     */
    private fun createTotalFeedbackString(result: AnalysisResult): String {
        val totalHeader = "⭐️ 최종 총점 피드백 (${result.totalActualScore}/${result.totalMaxScore}점) ⭐️\n\n"
        val allFeedback = result.results.joinToString("\n\n") { criterionResult ->
            "--- ${criterionResult.criterionName} (${criterionResult.actualScore}/${criterionResult.maxScore}점) ---\n피드백: ${criterionResult.feedback}"
        }
        return totalHeader + allFeedback
    }

    /**
     * 항목 클릭 시 피드백을 누적/토글하여 표시합니다.
     */
    private fun handleCriteriaClick(result: CriterionResult) {
        val feedbackHeader = "--- ${result.criterionName} (${result.actualScore}/${result.maxScore}점) ---\n"
        val feedbackContent = "${result.feedback}\n\n"

        val totalFeedbackPrefix = "⭐️ 최종 총점 피드백"
        if (tvFeedbackArea.text.toString().startsWith(totalFeedbackPrefix)) {
            feedbackBuilder.clear()
        }

        // 개별 항목 피드백 누적/토글 로직
        val currentText = feedbackBuilder.toString()
        if (currentText.contains(feedbackHeader)) {
            val newText = currentText.replace(feedbackHeader + feedbackContent, "")
            feedbackBuilder.clear().append(newText)
        } else {
            feedbackBuilder.append(feedbackHeader).append(feedbackContent)
        }

        tvFeedbackArea.text = feedbackBuilder.toString().trim()

        if (feedbackBuilder.isEmpty()) {
            layoutFeedback.visibility = View.GONE
        } else {
            layoutFeedback.visibility = View.VISIBLE
        }
    }


    // -----------------------------------------------------
    // 🔴 파일 다운로드 로직 및 저장 (BOM 포함)
    // -----------------------------------------------------
    enum class FileType { EXCEL, PDF }

    private fun downloadFile(type: FileType) {
        // analysisResult가 null이면 모의 데이터를 사용하여 파일 콘텐츠를 생성합니다.
        val dataToUse = analysisResult ?: generateMockAnalysisResult()

        val fileContent = generateFileContent(type, dataToUse)

        // 1. 파일 이름 및 MIME 타입 설정
        val mimeType: String
        val fileExtension: String

        if (type == FileType.EXCEL) {
            fileExtension = ".csv"
            mimeType = "text/csv"
        } else { // FileType.PDF
            // ⚠️ PDF 라이브러리가 없으므로, .txt 파일로 저장하여 파일 손상 오류를 방지합니다.
            fileExtension = ".txt"
            mimeType = "text/plain"
        }

        val defaultFileName = "AnalysisResult_${System.currentTimeMillis()}${fileExtension}"

        // 2. Launcher 실행: 사용자에게 저장 위치를 선택하도록 요청
        lastGeneratedFileContent = fileContent
        lastGeneratedFileType = type
        fileSaveLauncher.launch(defaultFileName)

        Toast.makeText(this,
            "${type} 파일 다운로드 준비 중...",
            Toast.LENGTH_SHORT).show()
    }

    /**
     * ⭐️ Excel (CSV) 형식에 맞춰 데이터를 생성합니다. (팀명, 기준별 점수, 총점)
     */
    private fun generateFileContent(type: FileType, result: AnalysisResult): String {

        return when (type) {
            FileType.EXCEL -> {
                // 팀명은 임시로 "테스트팀" 사용. 실제로는 PresentationInfo에서 로드해야 합니다.
                val teamName = "테스트팀"

                // 1. 헤더 생성: 팀명, 기준 목록, 총점
                val criteriaNames = result.results.joinToString(",") { it.criterionName }
                val header = "팀명,${criteriaNames},총점\n" // 예: "팀명,논리성,발표 태도,시각 자료,총점"

                // 2. 데이터 행 생성
                val criteriaScores = result.results.joinToString(",") { it.actualScore.toString() }
                val totalScore = result.totalActualScore

                val dataRow = "${teamName},${criteriaScores},${totalScore}\n" // 예: "테스트팀,18,25,45,88"

                // 3. 최종 CSV 내용
                header + dataRow
            }
            FileType.PDF -> {
                // PDF는 현재 텍스트 파일로 저장됩니다.
                val totalScoreLine = "총점: ${result.totalActualScore}/${result.totalMaxScore}점\n\n"

                val feedbackContent = result.results.joinToString("\n\n") {
                    "--- ${it.criterionName} (${it.actualScore}/${it.maxScore}점) ---\n피드백: ${it.feedback}"
                }

                totalScoreLine + feedbackContent
            }
        }
    }

    /**
     * ⭐️ 주어진 URI에 문자열 콘텐츠를 기록합니다. (Excel/CSV에 BOM 추가)
     */
    private fun writeContentToFile(uri: Uri, content: String, type: FileType) = lifecycleScope.launch(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->

                // ⭐️ Excel 파일(CSV)인 경우 UTF-8 BOM(Byte Order Mark) 추가 (한글 깨짐 방지)
                if (type == FileType.EXCEL) {
                    // UTF-8 BOM: 0xEF, 0xBB, 0xBF
                    outputStream.write(0xEF)
                    outputStream.write(0xBB)
                    outputStream.write(0xBF)
                }

                // 인코딩을 UTF-8로 명시하여 저장
                outputStream.write(content.toByteArray(Charsets.UTF_8))

                launch(Dispatchers.Main) {
                    Toast.makeText(this@AnalysisResultActivity,
                        "${type} 파일 저장 성공! (${uri.lastPathSegment})",
                        Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("Download", "File writing failed: ${e.message}")
            launch(Dispatchers.Main) {
                Toast.makeText(this@AnalysisResultActivity,
                    "${type} 파일 저장 중 오류 발생.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}