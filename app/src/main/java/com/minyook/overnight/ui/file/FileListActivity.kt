package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
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
        // 바인딩 객체 초기화
        binding = ActivityFileListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val folderId = intent.getStringExtra("FOLDER_ID")
        val folderName = intent.getStringExtra("FOLDER_NAME") ?: "파일 목록"

        // binding 객체를 통해 뷰에 접근
        binding.tvFolderTitle.text = folderName
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()

        if (folderId != null) fetchFiles(folderId)
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

    private fun fetchFiles(folderId: String) {
        fileList.clear()
        db.collection("contents").document(folderId).collection("files")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val title = doc.getString("fileName") ?: "제목 없음"
                    val score = doc.getLong("score")?.toInt() ?: 0
                    val summary = doc.getString("summary") ?: ""
                    val dateStr = getDateString(doc.getTimestamp("createdAt"))
                    fileList.add(PresentationFile(doc.id, title, dateStr, score, summary))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "파일 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getDateString(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it)
        } ?: "-"
    }
}