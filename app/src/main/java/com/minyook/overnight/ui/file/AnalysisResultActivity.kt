package com.minyook.overnight.ui.file

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.HorizontalAlignment.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.minyook.overnight.data.model.CriterionResult
import com.minyook.overnight.databinding.ActivityAnalysisResultBinding
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding
    private lateinit var db: FirebaseFirestore

    private var contentId: String? = null
    private var topicId: String? = null
    private var presentationId: String? = null


    private var currentTopicName: String = ""    // 비교 검색을 위한 주제 이름
    private var currentContentName: String = "" // 과목(폴더) 이름 저장
    private var currentTeamName: String = ""    // 현재 팀 이름 저장
    private var totalScore = 0
    private var resultList = ArrayList<CriterionResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        // ID 받기
        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")
        presentationId = intent.getStringExtra("presentationId")

        if (contentId != null && topicId != null) {
            // 1. 과목 이름 먼저 가져오기
            fetchContentName()
            // 2. 주제 및 분석 시작
            fetchTopicAndSimulateAnalysis()
        } else {
            Toast.makeText(this, "데이터 오류: ID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        setupButtons()
    }

    //과목(폴더) 이름 가져오기 (엑셀 파일명용)
    private fun fetchContentName() {
        db.collection("contents").document(contentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentContentName = document.getString("contentName") ?: "과목"
                }
            }
    }

    private fun fetchTopicAndSimulateAnalysis() {
        db.collection("contents").document(contentId!!)
            .collection("topics").document(topicId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val standards = document.get("standards") as? List<HashMap<String, Any>>

                    // Topic에 저장된 teamInfo와 topicName 가져오기
                    val teamName = document.getString("teamInfo") ?: "알 수 없는 팀"
                    currentTopicName = document.getString("topicName") ?: ""

                    if (standards != null) {
                        runFakeAIAnalysis(standards, teamName, currentTopicName)
                    }
                } else {
                    Toast.makeText(this, "주제 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun runFakeAIAnalysis(standards: List<HashMap<String, Any>>, teamName: String, topicName: String) {
        resultList.clear()
        totalScore = 0
        val scoresToSave = ArrayList<HashMap<String, Any>>()

        val randomFeedbacks = listOf(
            "목소리 톤이 안정적입니다.", "전달력이 훌륭합니다.", "논리적인 구성입니다.", "자신감이 넘칩니다."
        )

        for (std in standards) {
            val name = std["standardName"] as? String ?: "항목"
            val maxScore = (std["standardScore"] as? Number)?.toInt() ?: 0

            val minScore = (maxScore * 0.7).toInt()
            val actualScore = Random.nextInt(minScore, maxScore + 1)
            val fakeFeedback = randomFeedbacks.random()

            totalScore += actualScore
            resultList.add(CriterionResult(name, maxScore, actualScore, fakeFeedback))

            val scoreMap = hashMapOf<String, Any>(
                "standardName" to name,
                "standardScore" to maxScore,
                "scoreValue" to actualScore,
                "feedback" to fakeFeedback
            )
            scoresToSave.add(scoreMap)
        }

        val overallFeedback = "전체적으로 ${totalScore}점의 훌륭한 발표입니다."
        displayResults()

        // ⭐ [핵심] DB 업데이트
        if (presentationId != null) {
            val updates = hashMapOf<String, Any>(
                "totalScore" to totalScore,
                "scores" to scoresToSave,
                "overallFeedback" to overallFeedback,
                "status" to "completed",
                "gradeAt" to com.google.firebase.Timestamp.now(),
                "teamInfo" to teamName,    // 팀명 저장
                "topicName" to topicName   // 주제명 저장
            )

            db.collection("presentations").document(presentationId!!)
                .update(updates)
                .addOnSuccessListener {
                    // 저장 확인용 메시지 (테스트 끝나면 주석 처리하세요)
                    // Toast.makeText(this, "결과 저장 완료: $teamName", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "결과 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "오류: ID가 없어 결과를 저장하지 못했습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayResults() {
        binding.tvTotalScore.text = "$totalScore / 100"
        val grade = if (totalScore >= 90) "S" else if (totalScore >= 80) "A" else "B"
        binding.tvCenterScoreValue.text = grade
        binding.tvTotalSummary.text = "AI 분석 완료. $grade 등급입니다."

        setupPieChart(totalScore)

        val adapter = CriteriaListAdapter(resultList) { item ->
            showDetailFeedback(item)
        }
        binding.recyclerCriteriaTop.layoutManager = LinearLayoutManager(this)
        binding.recyclerCriteriaTop.adapter = adapter
    }

    private fun showDetailFeedback(item: CriterionResult) {
        binding.cardFeedback.visibility = View.VISIBLE
        binding.tvFeedbackArea.text = "[ ${item.criterionName} ]\n${item.feedback}"
    }

    private fun setupPieChart(score: Int) {
        val pieChart = binding.pieChart
        pieChart.setUsePercentValues(false)
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.isRotationEnabled = false
        pieChart.setTouchEnabled(false)
        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 70f
        pieChart.setHoleColor(Color.WHITE)
        pieChart.transparentCircleRadius = 0f

        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(score.toFloat(), ""))
        entries.add(PieEntry((100 - score).toFloat(), ""))
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#4F6EF3"))
        colors.add(Color.parseColor("#E0E0E0"))
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colors
        dataSet.sliceSpace = 0f
        val data = PieData(dataSet)
        data.setDrawValues(false)
        pieChart.data = data
        pieChart.animateY(1400, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun setupButtons() {
        binding.btnDownloadExcel.setOnClickListener { saveExcel() }
        binding.btnDownloadPdf.setOnClickListener { savePdf() }
        binding.btnMyPage.setOnClickListener { finish() }
    }

    private fun saveExcel() {
        if (currentTopicName.isEmpty()) {
            Toast.makeText(this, "주제 정보가 로드되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "데이터 불러오는 중...", Toast.LENGTH_SHORT).show()

        // topicName이 같은 데이터 조회
        db.collection("presentations")
            .whereEqualTo("contentId", contentId)
            .whereEqualTo("topicName", currentTopicName)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "비교할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                createAndSaveExcel(documents.documents)
            }
            .addOnFailureListener {
                Toast.makeText(this, "데이터 조회 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createAndSaveExcel(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("결과 요약")

            // 1. 모든 문서(팀)를 훑어서 '등장한 적 있는 모든 평가 기준'을 수집합니다.
            // (중복 제거를 위해 Set 사용)
            val allCriteriaSet = mutableSetOf<String>()

            for (doc in docs) {
                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                if (scoresList != null) {
                    for (scoreMap in scoresList) {
                        val name = scoreMap["standardName"] as? String
                        if (name != null) {
                            allCriteriaSet.add(name)
                        }
                    }
                }
            }

            // 보기 좋게 가나다 순으로 정렬하여 리스트로 변환
            val allCriteriaList = allCriteriaSet.sorted()


            // -------------------------------------------------------
            // 2. 헤더 행 생성
            // [팀명] | [기준 A] | [기준 B] | [기준 C] ... | [총점]
            // -------------------------------------------------------
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("팀명")

            for ((index, criterionName) in allCriteriaList.withIndex()) {
                headerRow.createCell(index + 1).setCellValue(criterionName)
            }
            // 마지막 열: 총점
            headerRow.createCell(allCriteriaList.size + 1).setCellValue("총점")

            // -------------------------------------------------------
            // 3. 데이터 행 채우기
            // -------------------------------------------------------
            for ((rowIndex, doc) in docs.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)

                // (1) 팀명
                val teamName = doc.getString("teamInfo") ?: "Team-${doc.id.take(5)}"
                row.createCell(0).setCellValue(teamName)

                // (2) 점수 매핑 (해당 기준이 이 팀에 없으면 0점 또는 공란 처리)
                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                val docTotalScore = doc.getLong("totalScore")?.toDouble() ?: 0.0

                for ((colIndex, criterionName) in allCriteriaList.withIndex()) {
                    // 이 팀의 점수 목록에서 해당 기준(criterionName)을 찾음
                    val match = scoresList?.find { it["standardName"] == criterionName }

                    val cell = row.createCell(colIndex + 1)

                    if (match != null) {
                        // 점수가 있으면 -> 숫자 입력
                        val score = (match["scoreValue"] as? Number)?.toDouble() ?: 0.0
                        cell.setCellValue(score)
                    } else {
                        // 점수가 없으면(이 팀의 기준이 아님) -> 문자 "-" 입력
                        cell.setCellValue("-")
                    }
                }
                // (3) 총점
                row.createCell(allCriteriaList.size + 1).setCellValue(docTotalScore)
            }

            // 파일명 : 과목명_주제명.xlsx (공백은 언더바로 교체)
            val safeContent = currentContentName.replace(" ", "_")
            val safeTopic = currentTopicName.replace(" ", "_")
            val fileName = "${safeContent}_${safeTopic}.xlsx"

            saveFileToDownloads(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { outputStream ->
                workbook.write(outputStream)
                workbook.close()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "엑셀 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePdf() {
        try {
            // 1. 팀 이름 확인 및 기본값 설정 (빈 값이면 'Team_Unknown' 사용)
            var finalTeamName = currentTeamName.trim()
            if (finalTeamName.isEmpty()) {
                finalTeamName = "Team_Unknown"
            }

            // 팀명_결과.pdf
            // 2. 파일명 생성 (공백 -> 언더바 변경)
            val safeTeamName = finalTeamName.replace(" ", "_")
            val fileName = "${safeTeamName}.pdf"

            saveFileToDownloads(fileName, "application/pdf") { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)

                // 1. 한글 폰트 로드 (assets/font.ttf 필수!!)
                try {
                    val font = PdfFontFactory.createFont("assets/malgun.ttf", PdfEncodings.IDENTITY_H)
                    document.setFont(font)
                } catch (e: Exception) {
                    // 폰트 파일이 없을 경우 경고 로그 (영어만 출력됨)
                    e.printStackTrace()
                    Toast.makeText(this, "폰트 로드 실패 (font.ttf 확인 필요)", Toast.LENGTH_SHORT).show()
                }

                // 2. 디자인 구현 (보내주신 이미지 스타일)

                // --- 제목 (가운데 정렬, 큰 글씨) ---
                val title = Paragraph("$currentTeamName 팀")
                    .setFontSize(24f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                document.add(title)
                document.add(Paragraph("\n")) // 줄바꿈

                // --- 평가 기준 섹션 ---
                document.add(Paragraph("평가 기준").setFontSize(14f).setBold())

                var maxTotal = 0
                for (item in resultList) {
                    document.add(Paragraph("• ${item.criterionName} : ${item.maxScore}점").setFontSize(12f))
                    maxTotal += item.maxScore
                }
                document.add(Paragraph("• 합계 : ${maxTotal}점").setFontSize(12f))
                document.add(Paragraph("\n"))

                // --- 채점 결과 섹션 ---
                document.add(Paragraph("채점 결과").setFontSize(14f).setBold())
                document.add(Paragraph("\n"))

                for (item in resultList) {
                    // 항목명 : 점수 (굵게)
                    document.add(Paragraph("${item.criterionName} : ${item.actualScore}점").setFontSize(12f).setBold())
                    // 피드백
                    document.add(Paragraph("피드백 : ${item.feedback}").setFontSize(12f))

                    // 구분선
                    val line = SolidLine(1f)
                    line.color = com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY
                    val ls = LineSeparator(line)
                    ls.setMarginTop(5f)
                    ls.setMarginBottom(5f)
                    document.add(ls)
                }

                document.add(Paragraph("\n"))

                // --- 총점 (맨 아래) ---
                document.add(Paragraph("총점 : ${totalScore}점").setFontSize(18f).setBold())

                document.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFileToDownloads(fileName: String, mimeType: String, writeAction: (OutputStream) -> Unit) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val resolver = contentResolver
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        }

        if (uri != null) {
            resolver.openOutputStream(uri)?.use(writeAction)
            Toast.makeText(this, "다운로드 완료: $fileName", Toast.LENGTH_LONG).show()
        }
    }
}