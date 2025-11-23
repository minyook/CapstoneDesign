package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.minyook.overnight.R
import com.minyook.overnight.data.model.PresentationFile
import com.minyook.overnight.data.model.SubjectFolder
import com.minyook.overnight.databinding.ActivitySubjectFolderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SubjectFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectFolderBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val folderList = ArrayList<SubjectFolder>()
    private val fileList = ArrayList<PresentationFile>()
    private var selectedFolderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        fetchFolders()

        // 1. 폴더 선택 클릭
        binding.etFolderPath.setOnClickListener {
            if (folderList.isEmpty()) {
                Toast.makeText(this, "폴더가 없거나 로딩 중입니다.", Toast.LENGTH_SHORT).show()
                fetchFolders()
            } else {
                showFolderSelectionSheet()
            }
        }

        // 2. 파일 선택 클릭
        binding.etFilePath.setOnClickListener {
            if (selectedFolderId == null) {
                Toast.makeText(this, "먼저 폴더를 선택해주세요.", Toast.LENGTH_SHORT).show()
            } else if (fileList.isEmpty()) {
                Toast.makeText(this, "파일이 없거나 로딩 중입니다.", Toast.LENGTH_SHORT).show()
            } else {
                showFileSelectionSheet()
            }
        }
    }

    private fun showFolderSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_selection_list, null)

        view.findViewById<TextView>(R.id.tv_selection_title).text = "과목 폴더 선택"
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_selection_list)

        val adapter = SubjectFolderAdapter(folderList) { selectedFolder ->
            binding.etFolderPath.setText(selectedFolder.title)
            selectedFolderId = selectedFolder.id

            binding.layoutFileInput.visibility = View.VISIBLE
            binding.etFilePath.setText("파일을 선택해주세요")
            fetchFiles(selectedFolder.id)

            dialog.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showFileSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_selection_list, null)

        view.findViewById<TextView>(R.id.tv_selection_title).text = "발표 파일 선택"
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_selection_list)

        val adapter = FileListAdapter(fileList) { selectedFile ->
            binding.etFilePath.setText(selectedFile.title)
            dialog.dismiss()
            moveToAnalysisResult(selectedFile)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        dialog.setContentView(view)
        dialog.show()
    }

    private fun moveToAnalysisResult(file: PresentationFile) {
        val intent = Intent(this, AnalysisResultActivity::class.java)
        intent.putExtra("FILE_TITLE", file.title)
        intent.putExtra("FILE_SCORE", file.score)
        intent.putExtra("FILE_DATE", file.date)
        intent.putExtra("FILE_SUMMARY", file.summary)
        startActivity(intent)
    }

    private fun fetchFolders() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("contents")
            .whereEqualTo("userId", uid)
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                folderList.clear()
                for (doc in result) {
                    val name = doc.getString("contentName") ?: "제목 없음"
                    val dateStr = getDateString(doc.getTimestamp("createdAt"))
                    folderList.add(SubjectFolder(doc.id, name, dateStr))
                }
            }
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
            }
    }

    private fun getDateString(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it)
        } ?: "-"
    }
}