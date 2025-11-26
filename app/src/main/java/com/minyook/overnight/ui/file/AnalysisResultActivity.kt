package com.minyook.overnight.ui.file

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.minyook.overnight.data.model.CriterionResult
import com.minyook.overnight.databinding.ActivityAnalysisResultBinding
import com.minyook.overnight.ui.custom.MultiSegmentDonutChart
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding
    private lateinit var db: FirebaseFirestore

    // [수정 1] userId 변수 추가
    private var userId: String? = null
    private var contentId: String? = null // Folder ID
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

        // ★ [핵심] 앱 실행 시 내비게이션 바 숨기기 (전체화면)
        hideSystemUI()

        db = FirebaseFirestore.getInstance()

        // [수정 2] Intent 데이터 수신 (userId 포함)
        userId = intent.getStringExtra("userId")
        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")
        presentationId = intent.getStringExtra("presentationId")

        // 경로를 위한 필수 ID가 모두 있는지 확인
        if (userId != null && contentId != null && topicId != null && presentationId != null) {
            fetchContentName()
            loadAnalysisResultFromFirestore()
        } else {
            // (테스트를 위해 기존 로직 유지 혹은 에러 처리)
            if (presentationId != null && userId == null) {
                // 혹시라도 예전 방식(root 컬렉션)으로 접근하는 경우를 대비한 방어 코드
                loadAnalysisResultFromRoot()
            } else {
                Toast.makeText(this, "데이터 경로를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                // finish() // 필요 시 주석 해제
            }
        }

        setupButtons()
    }

    // ★ [핵심] 화면이 다시 보일 때(다른 창 갔다 왔을 때)도 숨김 모드 유지
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // ★ [핵심 기능] 시스템 바(상태바, 내비게이션 바) 강제 숨김 함수
    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 스와이프 시 잠깐 나타났다가 다시 사라지게 설정
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 상단 상태바 & 하단 내비게이션바 모두 숨김
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    // [수정 4] 폴더 이름 가져오기 (경로 수정)
    private fun fetchContentName() {
        if (userId == null || contentId == null) return

        db.collection("user").document(userId!!)
            .collection("folders").document(contentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentContentName = document.getString("name") ?: "과목" // 필드명 'name' 확인 필요
                }
            }
    }

    private fun loadAnalysisResultFromFirestore() {
        binding.tvTotalSummary.text = "데이터 불러오는 중..."

        // 경로: user/{uid}/folders/{folderId}/topics/{topicId}/presentations/{docId}
        db.collection("user").document(userId!!)
            .collection("folders").document(contentId!!)
            .collection("topics").document(topicId!!)
            .collection("presentations").document(presentationId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // 데이터 파싱 (기존 로직과 동일)
                    currentTeamName = document.getString("teamInfo") ?: "Unknown Team"
                    currentTopicName = document.getString("topicName") ?: ""

                    // 요약 정보 + 영상 요약 합치기
                    val overall = document.getString("overallFeedback") ?: ""
                    val videoSum = document.getString("videoSummary") ?: ""
                    val finalSummary = if(videoSum.isNotEmpty()) "$overall\n\n[영상 요약]\n$videoSum" else overall

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

                    updateUI(finalSummary)

                } else {
                    Toast.makeText(this, "결과 문서를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // (하위 호환용: 기존 Root 컬렉션에서 읽기 - 필요 없으면 삭제 가능)
    private fun loadAnalysisResultFromRoot() {
        db.collection("presentations").document(presentationId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // ... (파싱 로직 동일) ...
                    val overall = document.getString("overallFeedback") ?: "피드백 없음"
                    totalScore = document.getLong("totalScore")?.toInt() ?: 0
                    // ... UI 업데이트 ...
                    updateUI(overall)
                }
            }
    }

    private fun updateUI(overallFeedback: String) {
        binding.tvTotalScore.text = "$totalScore / 100"
        binding.tvCenterScoreValue.text = totalScore.toString()
        binding.tvTotalSummary.text = overallFeedback
        setupDonutChart()
        setupRecyclerView()
    }

    private fun setupDonutChart() {
        val donutChart = binding.pieChart
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

    // --- 엑셀 저장 ---
    // ui/file/AnalysisResultActivity.kt 내부

    // --- 엑셀 저장 (수정됨: 4단계 경로 적용) ---
    // [수정됨] 엑셀 저장 함수
    private fun saveExcel() {
        // 1. 정보가 로딩되지 않았을 때 방어
        if (currentTopicName.isEmpty()) {
            Toast.makeText(this, "아직 데이터를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 필수 ID 확인
        if (userId == null || contentId == null) {
            Toast.makeText(this, "경로 오류: 저장할 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "데이터 통합 및 생성 중...", Toast.LENGTH_SHORT).show()

        // 3. 같은 폴더 내, 같은 주제(TopicName)를 가진 모든 문서를 찾음
        db.collection("user").document(userId!!)
            .collection("folders").document(contentId!!)
            .collection("topics")
            .whereEqualTo("topicName", currentTopicName)
            .get()
            .addOnSuccessListener { topicDocs ->
                if (topicDocs.isEmpty) {
                    Toast.makeText(this, "비교할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 4. 각 Topic의 하위 presentations 컬렉션 가져오기
                val tasks = ArrayList<Task<QuerySnapshot>>()
                for (topic in topicDocs) {
                    val task = topic.reference.collection("presentations")
                        .whereEqualTo("status", "completed")
                        .get()
                    tasks.add(task)
                }

                // 5. 결과 합치기
                Tasks.whenAllSuccess<QuerySnapshot>(tasks).addOnSuccessListener { results ->
                    val allPresentations = ArrayList<DocumentSnapshot>()
                    for (querySnapshot in results) {
                        allPresentations.addAll(querySnapshot.documents)
                    }

                    if (allPresentations.isEmpty()) {
                        Toast.makeText(this, "분석 완료된 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        createAndSaveExcel(allPresentations)
                    }
                }
                    .addOnFailureListener {
                        Toast.makeText(this, "데이터 통합 중 오류: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "주제 검색 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // [수정됨] 엑셀 파일 생성 및 저장 함수
    private fun createAndSaveExcel(docs: List<DocumentSnapshot>) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("발표 점수 비교")

            // -- 1. 헤더 생성 (모든 문서의 평가 기준 수집) --
            val allCriteriaSet = mutableSetOf<String>()
            for (doc in docs) {
                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                if (scoresList != null) {
                    for (map in scoresList) {
                        val name = map["standardName"] as? String
                        if (!name.isNullOrEmpty()) allCriteriaSet.add(name)
                    }
                }
            }
            val allCriteriaList = allCriteriaSet.sorted() // 가나다순 정렬

            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("팀명")
            for ((index, criterionName) in allCriteriaList.withIndex()) {
                headerRow.createCell(index + 1).setCellValue(criterionName)
            }
            headerRow.createCell(allCriteriaList.size + 1).setCellValue("총점")

            // -- 2. 데이터 채우기 --
            for ((rowIndex, doc) in docs.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)

                // 팀명 (없으면 기본값)
                val teamName = doc.getString("teamInfo") ?: "팀 정보 없음"
                row.createCell(0).setCellValue(teamName)

                val scoresList = doc.get("scores") as? List<HashMap<String, Any>>
                val docTotalScore = doc.getLong("totalScore")?.toDouble() ?: 0.0

                // 각 기준별 점수 입력
                for ((colIndex, criterionName) in allCriteriaList.withIndex()) {
                    val match = scoresList?.find { it["standardName"] == criterionName }
                    val cell = row.createCell(colIndex + 1)
                    if (match != null) {
                        val score = (match["scoreValue"] as? Number)?.toDouble() ?: 0.0
                        cell.setCellValue(score)
                    } else {
                        cell.setCellValue("-") // 해당 기준이 없으면 - 표시
                    }
                }
                // 총점
                row.createCell(allCriteriaList.size + 1).setCellValue(docTotalScore)
            }

            // -- 3. 안전한 파일명 생성 (빈 값 방지) --
            var safeContent = currentContentName.trim().replace(" ", "_")
            var safeTopic = currentTopicName.trim().replace(" ", "_")

            // 이름이 비어있으면 기본값 부여
            if (safeContent.isEmpty()) safeContent = "UnknownFolder"
            if (safeTopic.isEmpty()) safeTopic = "Presentation"

            // 현재 시간 추가 (중복 방지)
            val timeStamp = java.text.SimpleDateFormat("MMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "${safeContent}_${safeTopic}_${timeStamp}.xlsx"

            // -- 4. 저장 시도 --
            saveFileToDownloads(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { outputStream ->
                workbook.write(outputStream)
                workbook.close()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "엑셀 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- PDF 저장 ---
    private fun savePdf() {
        try {
            var finalTeamName = currentTeamName.trim()
            if (finalTeamName.isEmpty()) finalTeamName = "Team_Unknown"
            val safeTeamName = finalTeamName.replace(" ", "_")
            val fileName = "${safeTeamName}_Result.pdf"

            // 화면에 표시된 요약 내용을 가져옵니다.
            val summaryText = binding.tvTotalSummary.text.toString()

            saveFileToDownloads(fileName, "application/pdf") { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)

                // 폰트 설정 (한글 깨짐 방지)
                try {
                    val font = PdfFontFactory.createFont("assets/malgun.ttf", PdfEncodings.IDENTITY_H)
                    document.setFont(font)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 1. 타이틀 (팀명)
                val title = Paragraph("$finalTeamName 팀 분석 결과")
                    .setFontSize(24f).setBold().setTextAlignment(TextAlignment.CENTER)
                document.add(title)
                document.add(Paragraph("\n"))

                // 3. 평가 기준 및 배점 정보
                document.add(Paragraph("■ 평가 기준").setFontSize(16f).setBold())
                var maxTotal = 0
                for (item in resultList) {
                    document.add(Paragraph("• ${item.criterionName} (${item.maxScore}점)").setFontSize(12f))
                    maxTotal += item.maxScore
                }
                document.add(Paragraph("→ 총 배점 : ${maxTotal}점").setFontSize(12f))
                document.add(Paragraph("\n"))

                // 4. 상세 채점 결과
                document.add(Paragraph("■ 상세 피드백").setFontSize(16f).setBold())
                document.add(Paragraph("\n"))

                for (item in resultList) {
                    // 기준명 및 점수
                    val itemTitle = Paragraph("▶ ${item.criterionName}")
                        .setFontSize(14f).setBold()
                    document.add(itemTitle)

                    val scoreText = Paragraph("점수: ${item.actualScore} / ${item.maxScore}")
                        .setFontSize(12f).setFontColor(com.itextpdf.kernel.colors.ColorConstants.BLUE)
                    document.add(scoreText)

                    // 피드백 내용
                    document.add(Paragraph("피드백: ${item.feedback}").setFontSize(12f))
                    document.add(Paragraph("\n")) // 항목 간 간격
                }


                // 구분선
                val line = SolidLine(1f)
                line.color = com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY
                document.add(LineSeparator(line))
                document.add(Paragraph("\n"))

                // 2. 종합 평가 (요약) - ★★★ 추가된 부분 ★★★
                document.add(Paragraph("■ AI 종합 평가").setFontSize(16f).setBold())
                document.add(Paragraph(summaryText).setFontSize(12f))
                document.add(Paragraph("\n"))

                // 5. 최종 점수 (하단 강조)
                document.add(LineSeparator(line))
                document.add(Paragraph("\n"))
                val finalScore = Paragraph("최종 점수 : $totalScore 점")
                    .setFontSize(24f).setBold().setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.RED)
                document.add(finalScore)

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