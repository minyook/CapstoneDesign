package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.minyook.overnight.data.model.PresentationFile
import com.minyook.overnight.databinding.ActivityFileListBinding
import java.text.SimpleDateFormat
import java.util.Locale

class FileListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileListBinding
    private val db = FirebaseFirestore.getInstance()
    private val fileList = ArrayList<PresentationFile>()
    private lateinit var adapter: FileListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val folderId = intent.getStringExtra("FOLDER_ID")
        // NOTE: FolderFragment의 '전체 노트' 클릭 시 ChildNotesFragment.newInstance(childName)이 호출되므로,
        // 이곳으로 오는 인텐트가 폴더 ID와 이름을 제대로 전달하는지 확인 필요
        val folderName = intent.getStringExtra("FOLDER_NAME") ?: "파일 목록"

        binding.tvFolderTitle.text = folderName
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()

        // 폴더 ID가 전달되면, 해당 폴더와 연결된 presentations 문서를 로드합니다.
        if (folderId != null) fetchPresentations(folderId)

        // ★ '전체 노트' 로직을 위한 처리 (FolderFragment의 ID가 '전체 노트'에 해당할 경우)
        // 전체 노트를 구현하려면, 모든 폴더 ID의 파일을 가져와야 하므로 별도의 로직이 필요합니다.
        // 현재는 전달된 folderId가 '전체 노트' 폴더의 ID라고 가정하고 진행합니다.
    }

    private fun setupRecyclerView() {
        adapter = FileListAdapter(fileList) { selectedFile ->
            val intent = Intent(this, AnalysisResultActivity::class.java)
            intent.putExtra("FILE_TITLE", selectedFile.title)
            intent.putExtra("FILE_SCORE", selectedFile.score)
            intent.putExtra("FILE_DATE", selectedFile.date)
            intent.putExtra("FILE_SUMMARY", selectedFile.summary)
            startActivity(intent)
        }
        binding.rvFileList.adapter = adapter
        binding.rvFileList.layoutManager = LinearLayoutManager(this)
    }

    /**
     * ★ 수정된 부분: contents/topics 대신 presentations 컬렉션을 쿼리합니다.
     * folderId(contentId)를 사용하여 해당 폴더에 속한 분석 결과(파일)를 가져옵니다.
     */
    private fun fetchPresentations(contentId: String) {
        fileList.clear()

        // Presentations 컬렉션을 쿼리하여 contentId 필터링
        db.collection("presentations")
            .whereEqualTo("contentId", contentId)
            .orderBy("gradeAt", Query.Direction.DESCENDING) // 채점 시간 기준 정렬
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    binding.rvFileList.visibility = View.GONE
                    Toast.makeText(this, "선택된 폴더에 분석된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    binding.rvFileList.visibility = View.VISIBLE
                }

                for (doc in result) {
                    // 1. topicId 추출 (추가됨)
                    val topicId = doc.getString("topicId") ?: ""

                    // presentation 문서에서 데이터 가져오기
                    val title = doc.getString("topicName") ?: "제목 없음"

                    // scores 필드에서 scoreValue 추출 (이전 코드에서 사용된 복합 추출 로직)
                    val score = doc.get("scores")?.let { scores ->
                        if (scores is List<*>) {
                            (scores.firstOrNull() as? Map<*, *>)?.get("scoreValue") as? Long
                        } else {
                            null
                        }
                    }?.toInt() ?: 0

                    val summary = doc.getString("overallFeedback") ?: "요약 정보 없음"
                    val dateStr = getDateString(doc.getTimestamp("gradeAt"))

                    fileList.add(PresentationFile(
                        id = doc.id,
                        topicId = topicId,
                        title = title,
                        date = dateStr,
                        score = score,
                        summary = summary
                    ))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("FileListActivity", "Presentation 로드 실패: ${e.message}", e)
                Toast.makeText(this, "파일 로드 실패. 인덱스 또는 네트워크 확인 필요.", Toast.LENGTH_LONG).show()
            }
    }

    private fun getDateString(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it)
        } ?: "-"
    }

    // NOTE: fetchFiles 메서드는 더 이상 사용되지 않음
    // private fun fetchFiles(folderId: String) { ... }
}