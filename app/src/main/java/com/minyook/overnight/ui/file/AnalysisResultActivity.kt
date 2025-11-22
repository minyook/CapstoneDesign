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
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
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

    // 비교 검색을 위한 주제 이름
    private var currentTopicName: String = ""

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

        // 디버깅용 로그
        if (presentationId == null) {
            Toast.makeText(this, "오류: 발표 ID가 전달되지 않았습니다.", Toast.LENGTH_LONG).show()
        }

        if (contentId != null && topicId != null) {
            fetchTopicAndSimulateAnalysis()
        } else {
            Toast.makeText(this, "데이터 오류: ID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        setupButtons()
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

            // --- [Python 코드 참고] 헤더 동적 생성 ---
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("팀명")

            // 현재 기준(resultList)을 바탕으로 헤더 작성
            for ((index, criterion) in resultList.withIndex()) {
                headerRow.createCell(index + 1).setCellValue(criterion.criterionName)
            }
            headerRow.createCell(resultList.size + 1).setCellValue("총점")

            // --- 데이터 채우기 ---
            for ((rowIndex, doc) in docs.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)

                // 1. 팀명 (DB에 없으면 ID 뒷자리 사용)
                val teamName = doc.getString("teamInfo") ?: "Team_${doc.id.take(4)}"
                row.createCell(0).setCellValue(teamName)

                // 2. 점수 매핑
                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                val docTotalScore = doc.getLong("totalScore")?.toDouble() ?: 0.0

                if (scoresList != null) {
                    for ((colIndex, criterion) in resultList.withIndex()) {
                        // 순서가 섞여도 이름으로 찾아서 정확한 위치에 점수 넣기
                        val match = scoresList.find { it["standardName"] == criterion.criterionName }
                        val score = (match?.get("scoreValue") as? Number)?.toDouble() ?: 0.0
                        row.createCell(colIndex + 1).setCellValue(score)
                    }
                }
                // 3. 총점
                row.createCell(resultList.size + 1).setCellValue(docTotalScore)
            }

            // --- [Python 코드 참고] 파일명 공백 처리 (safe_topic) ---
            // 공백을 언더바(_)로 변경하여 파일명 오류 방지
            val safeTopicName = currentTopicName.replace(" ", "_")
            val fileName = "${safeTopicName}_Result_${System.currentTimeMillis()}.xlsx"

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
            val fileName = "Report_${System.currentTimeMillis()}.pdf"
            saveFileToDownloads(fileName, "application/pdf") { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)

                // 한글 깨짐 방지: assets 폴더에 폰트가 없다면 영어로 출력됩니다.
                // 임시로 영어 타이틀 사용
                document.add(Paragraph("OvernightAI Report").setBold().setFontSize(24f))

                // 팀 이름 출력 (저장된 값 없을 경우 대비)
                // fetchTopicAndSimulateAnalysis()에서 가져온 팀명을 전역 변수로 저장해두거나,
                // 여기서는 이미 저장 로직을 수행했으므로 presentation 문서에서 다시 읽어올 수도 있지만,
                // 편의상 현재 세션의 값을 사용할 수는 없으므로(비동기라) DB 값을 다시 읽거나 해야 함.
                // 하지만 지금은 '현재 팀'의 결과이므로, 이미 알고 있는 정보를 사용하는 게 빠름.
                // fetch.. 에서 teamName을 전역 변수로 빼두면 좋습니다. (여기선 생략됨)

                document.add(Paragraph("\nTotal Score: $totalScore").setFontSize(18f))

                // 테이블 생성
                val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 4f)))
                table.setWidth(UnitValue.createPercentValue(100f))
                table.addHeaderCell(Cell().add(Paragraph("Item")))
                table.addHeaderCell(Cell().add(Paragraph("Score")))
                table.addHeaderCell(Cell().add(Paragraph("Feedback")))

                for (item in resultList) {
                    table.addCell(Paragraph(item.criterionName))
                    table.addCell(Paragraph("${item.actualScore} / ${item.maxScore}"))
                    table.addCell(Paragraph(item.feedback))
                }
                document.add(table)
                document.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF 저장 실패", Toast.LENGTH_SHORT).show()
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