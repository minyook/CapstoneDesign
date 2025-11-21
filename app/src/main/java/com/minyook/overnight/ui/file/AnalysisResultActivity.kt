package com.minyook.overnight.ui.file

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.UnitValue
import com.minyook.overnight.R
import com.minyook.overnight.databinding.ActivityAnalysisResultBinding
import com.minyook.overnight.ui.mypage.MyPageFragment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding

    // 데이터 변수
    private val teamName = "테스트팀"
    private val totalScore = 85
    private val scoreGrade = "B+"
    private val totalSummary = "전반적으로 목소리 톤이 안정적이며 청중과의 아이컨택이 훌륭합니다. 다만, 초반 도입부에서 속도가 다소 빠른 편입니다."

    private val allCriteriaList = ArrayList<CriteriaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initData()
        initView()
        setupPieChart(totalScore)
        setupRecyclerViews()
        setupButtons()
    }

    private fun initData() {
        allCriteriaList.add(CriteriaItem("목소리 크기", "적절함", "20", "20", "성량이 풍부하여 전달력이 좋습니다."))
        allCriteriaList.add(CriteriaItem("발음 정확도", "매우 좋음", "19", "20", "자음과 모음의 발음이 매우 명확합니다."))
        allCriteriaList.add(CriteriaItem("창의성", "보통", "15", "20", "주제 선정은 좋으나 전개 방식이 다소 평이합니다."))
        allCriteriaList.add(CriteriaItem("휴지(Pause)", "부족함", "10", "20", "문장 간의 쉼이 부족하여 급해 보입니다."))
    }

    private fun initView() {
        binding.tvTotalScore.text = "$totalScore / 100"
        binding.tvCenterScoreValue.text = scoreGrade
        binding.tvTotalSummary.text = totalSummary
        binding.cardFeedback.visibility = View.GONE

        binding.cardSummary.setOnClickListener {
            if (binding.cardFeedback.visibility == View.VISIBLE) {
                binding.cardFeedback.visibility = View.GONE
            } else {
                binding.cardFeedback.visibility = View.VISIBLE
                binding.tvFeedbackArea.text = "[ 전체 요약 ]\n\n$totalSummary"
            }
        }
    }

    // --- 버튼 설정 (마이페이지 이동 추가됨) ---
    private fun setupButtons() {
        // 1. 엑셀 다운로드
        binding.btnDownloadExcel.setOnClickListener { saveExcel() }

        // 2. PDF 다운로드
        binding.btnDownloadPdf.setOnClickListener { savePdf() }

        // 3. 마이페이지 이동 (수정된 부분)
        binding.btnMyPage.setOnClickListener {
            val intent = Intent(this, MyPageFragment::class.java)
            startActivity(intent)
            // 필요하다면 finish()를 추가하여 뒤로가기 시 분석 화면이 안 나오게 할 수 있음
        }
    }

    // --- 엑셀 다운로드 ---
    private fun saveExcel() {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Analysis Result")

            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("팀명")
            for (i in allCriteriaList.indices) {
                headerRow.createCell(i + 1).setCellValue(allCriteriaList[i].title)
            }
            headerRow.createCell(allCriteriaList.size + 1).setCellValue("총점")

            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue(teamName)
            for (i in allCriteriaList.indices) {
                dataRow.createCell(i + 1).setCellValue(allCriteriaList[i].actualScore.toDouble())
            }
            dataRow.createCell(allCriteriaList.size + 1).setCellValue(totalScore.toDouble())

            val fileName = "Analysis_${System.currentTimeMillis()}.xlsx"
            saveFileToDownloads(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { outputStream ->
                workbook.write(outputStream)
                workbook.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "엑셀 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- PDF 다운로드 ---
    private fun savePdf() {
        try {
            val fileName = "Report_${System.currentTimeMillis()}.pdf"
            saveFileToDownloads(fileName, "application/pdf") { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
                val document = Document(pdfDoc)

                document.add(Paragraph("OvernightAI Presentation Report").setBold().setFontSize(20f))
                document.add(Paragraph("Team: $teamName  |  Date: ${getCurrentDate()}"))
                document.add(Paragraph("\n"))

                document.add(Paragraph("Total Score: $totalScore / 100 ($scoreGrade)").setBold().setFontSize(16f))
                document.add(Paragraph("Summary:"))
                document.add(Paragraph(totalSummary).setFontSize(11f))
                document.add(Paragraph("\n"))

                val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1f, 4f)))
                table.setWidth(UnitValue.createPercentValue(100f))
                table.addHeaderCell(Cell().add(Paragraph("Category").setBold()))
                table.addHeaderCell(Cell().add(Paragraph("Score").setBold()))
                table.addHeaderCell(Cell().add(Paragraph("Feedback / Detail").setBold()))

                for (item in allCriteriaList) {
                    table.addCell(Paragraph(item.title))
                    table.addCell(Paragraph("${item.actualScore} / ${item.maxScore}"))
                    table.addCell(Paragraph("${item.result}\n- ${item.feedbackDetail}"))
                }
                document.add(table)
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
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    writeAction(outputStream)
                }
                Toast.makeText(this, "다운로드 완료: $fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "오류 발생", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "파일 생성 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
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

    private fun setupRecyclerViews() {
        val topList = allCriteriaList.take(2)
        val topAdapter = ResultAdapter(topList) { showDetailFeedback(it) }
        binding.recyclerCriteriaTop.layoutManager = LinearLayoutManager(this)
        binding.recyclerCriteriaTop.adapter = topAdapter

        val bottomList = if (allCriteriaList.size > 2) allCriteriaList.drop(2) else emptyList()
        val bottomAdapter = ResultAdapter(bottomList) { showDetailFeedback(it) }
        binding.recyclerCriteriaBottom.layoutManager = LinearLayoutManager(this)
        binding.recyclerCriteriaBottom.adapter = bottomAdapter
    }

    private fun showDetailFeedback(item: CriteriaItem) {
        binding.cardFeedback.visibility = View.VISIBLE
        binding.tvFeedbackArea.text = "[ ${item.title} 분석 결과 ]\n\n${item.feedbackDetail}"
    }

    data class CriteriaItem(
        val title: String,
        val result: String,
        val actualScore: String,
        val maxScore: String,
        val feedbackDetail: String
    )

    inner class ResultAdapter(
        private val items: List<CriteriaItem>,
        private val onItemClick: (CriteriaItem) -> Unit
    ) : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_criteria_title)
            val tvResult: TextView = view.findViewById(R.id.tv_criteria_content)
            val tvScore: TextView = view.findViewById(R.id.tv_criteria_score)

            fun bind(item: CriteriaItem) {
                tvTitle.text = item.title
                tvResult.text = item.result
                tvScore.text = "${item.actualScore}/${item.maxScore}"
                itemView.setOnClickListener { onItemClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_criteria_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }
}