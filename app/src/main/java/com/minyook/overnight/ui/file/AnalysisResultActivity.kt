package com.minyook.overnight.ui.file

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.minyook.overnight.data.model.CriterionResult
import com.minyook.overnight.databinding.ActivityAnalysisResultBinding
import com.minyook.overnight.ui.custom.MultiSegmentDonutChart // ⭐️ 커스텀 차트 임포트
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import kotlin.random.Random

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding
    private lateinit var db: FirebaseFirestore

    // 식별자 ID
    private var contentId: String? = null
    private var topicId: String? = null
    private var presentationId: String? = null

    // 데이터 저장 변수
    private var currentTopicName: String = ""
    private var currentContentName: String = ""
    private var currentTeamName: String = ""
    private var totalScore = 0
    private var resultList = ArrayList<CriterionResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")
        presentationId = intent.getStringExtra("presentationId")

        if (presentationId != null) {
            if (contentId != null) fetchContentName()
            loadAnalysisResultFromFirestore()
        } else {
            Toast.makeText(this, "결과 ID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupButtons()
    }

    private fun fetchContentName() {
        db.collection("contents").document(contentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentContentName = document.getString("contentName") ?: "과목"
                }
            }
    }

    private fun loadAnalysisResultFromFirestore() {
        binding.tvTotalSummary.text = "데이터 불러오는 중..."

        db.collection("presentations").document(presentationId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentTeamName = document.getString("teamInfo") ?: "Unknown Team"
                    currentTopicName = document.getString("topicName") ?: ""
                    val overallFeedback = document.getString("overallFeedback") ?: "피드백이 없습니다."

                    totalScore = document.getLong("totalScore")?.toInt() ?: 0
                    val scoresList = document.get("scores") as? List<HashMap<String, Any>>

                    resultList.clear()
                    if (scoresList != null) {
                        for (map in scoresList) {
                            val name = map["standardName"] as? String ?: ""
                            val max = (map["standardScore"] as? Number)?.toInt() ?: 0
                            val actual = (map["scoreValue"] as? Number)?.toInt() ?: 0
                            val feedback = map["feedback"] as? String ?: ""

                            resultList.add(CriterionResult(name, max, actual, feedback))
                        }
                    }

                    updateUI(overallFeedback)

                } else {
                    Toast.makeText(this, "결과 문서가 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "데이터 로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(overallFeedback: String) {
        // 1. 총점 텍스트 설정
        binding.tvTotalScore.text = "$totalScore / 100"

        // 2. 중앙 텍스트 (등급 대신 점수 숫자 표시)
        binding.tvCenterScoreValue.text = totalScore.toString()

        // 3. 전체 피드백
        binding.tvTotalSummary.text = overallFeedback

        // 4. ⭐️ [수정됨] 커스텀 차트 그리기
        setupDonutChart()

        // 5. 리스트 설정
        setupRecyclerView()
    }

    // ⭐️ [신규 함수] 커스텀 뷰에 데이터 전달
    private fun setupDonutChart() {
        // XML의 <com.minyook.overnight.ui.custom.MultiSegmentDonutChart ... id="pie_chart">
        // ViewBinding이 자동으로 타입을 인식하지만, 혹시 모르니 타입 확인
        val donutChart = binding.pieChart

        // 데이터 리스트를 넘겨주면 onDraw가 호출되어 그려집니다.
        donutChart.setCriteria(resultList)
    }

    private fun setupRecyclerView() {
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

    private fun setupButtons() {
        binding.btnDownloadExcel.setOnClickListener { saveExcel() }
        binding.btnDownloadPdf.setOnClickListener { savePdf() }
        binding.btnMyPage.setOnClickListener { finish() }
    }

    // ----------------------------------------------------------------
    // 엑셀 저장 (기존 로직 유지)
    // ----------------------------------------------------------------
    private fun saveExcel() {
        if (currentTopicName.isEmpty()) {
            Toast.makeText(this, "주제 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "데이터 불러오는 중...", Toast.LENGTH_SHORT).show()

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
    }

    private fun createAndSaveExcel(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("점수 비교")

            val allCriteriaSet = mutableSetOf<String>()
            for (doc in docs) {
                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                if (scoresList != null) {
                    for (map in scoresList) {
                        val name = map["standardName"] as? String
                        if (name != null) allCriteriaSet.add(name)
                    }
                }
            }
            val allCriteriaList = allCriteriaSet.sorted()

            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("팀명")
            for ((index, criterionName) in allCriteriaList.withIndex()) {
                headerRow.createCell(index + 1).setCellValue(criterionName)
            }
            headerRow.createCell(allCriteriaList.size + 1).setCellValue("총점")

            for ((rowIndex, doc) in docs.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)
                val teamName = doc.getString("teamInfo") ?: "Team-${doc.id.take(5)}"
                row.createCell(0).setCellValue(teamName)

                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                val docTotalScore = doc.getLong("totalScore")?.toDouble() ?: 0.0

                for ((colIndex, criterionName) in allCriteriaList.withIndex()) {
                    val match = scoresList?.find { it["standardName"] == criterionName }
                    val cell = row.createCell(colIndex + 1)
                    if (match != null) {
                        val score = (match["scoreValue"] as? Number)?.toDouble() ?: 0.0
                        cell.setCellValue(score)
                    } else {
                        cell.setCellValue("-")
                    }
                }
                row.createCell(allCriteriaList.size + 1).setCellValue(docTotalScore)
            }

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

    // ----------------------------------------------------------------
    // PDF 저장 (기존 로직 유지)
    // ----------------------------------------------------------------
    private fun savePdf() {
        try {
            var finalTeamName = currentTeamName.trim()
            if (finalTeamName.isEmpty()) finalTeamName = "Team_Unknown"
            val safeTeamName = finalTeamName.replace(" ", "_")
            val fileName = "${safeTeamName}.pdf"

            saveFileToDownloads(fileName, "application/pdf") { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)

                try {
                    val font = PdfFontFactory.createFont("assets/malgun.ttf", PdfEncodings.IDENTITY_H)
                    document.setFont(font)
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "폰트 없음", Toast.LENGTH_SHORT).show() }
                }

                val title = Paragraph("$finalTeamName 팀")
                    .setFontSize(24f).setBold().setTextAlignment(TextAlignment.CENTER)
                document.add(title)
                document.add(Paragraph("\n"))

                document.add(Paragraph("평가 기준").setFontSize(14f).setBold())
                var maxTotal = 0
                for (item in resultList) {
                    document.add(Paragraph("• ${item.criterionName} : ${item.maxScore}점").setFontSize(12f))
                    maxTotal += item.maxScore
                }
                document.add(Paragraph("• 합계 : ${maxTotal}점").setFontSize(12f))
                document.add(Paragraph("\n"))

                document.add(Paragraph("채점 결과").setFontSize(14f).setBold())
                document.add(Paragraph("\n"))

                for (item in resultList) {
                    document.add(Paragraph("${item.criterionName} : ${item.actualScore}점").setFontSize(12f).setBold())
                    document.add(Paragraph("피드백 : ${item.feedback}").setFontSize(12f))

                    val line = SolidLine(1f)
                    line.color = com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY
                    val ls = LineSeparator(line)
                    ls.setMarginTop(5f)
                    ls.setMarginBottom(5f)
                    document.add(ls)
                }

                document.add(Paragraph("\n"))
                document.add(Paragraph("총점 : ${totalScore}점").setFontSize(18f).setBold())
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