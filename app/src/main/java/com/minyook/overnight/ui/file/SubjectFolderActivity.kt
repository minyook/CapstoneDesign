
package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import java.util.Date
import java.util.Locale
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
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

        // 1. 바텀시트용 폴더 목록은 항상 미리 가져옵니다
        fetchFolders()

        // 외부(FolderFragment)에서 전달받은 폴더 정보 확인
        val intentFolderId = intent.getStringExtra("folderId")
        val intentFolderName = intent.getStringExtra("folderName")

        if (intentFolderId != null && intentFolderName != null) {
            // 2-A. 정보가 넘어왔다면 -> 변수에 저장하고 화면에 표시 & 파일 로드 시작
            selectedFolderId = intentFolderId
            binding.etFolderPath.setText(intentFolderName)

            // 해당 폴더의 파일들을 바로 긁어옵니다.
            fetchPresentations(intentFolderId)
        } else {
            // 2-B. 정보가 없다면 -> "선택해주세요" 표시
            binding.etFolderPath.setText("폴더를 선택해주세요")
        }

        // 3. 폴더 선택 영역 클릭 (직접 변경할 때)
        binding.etFolderPath.setOnClickListener {
            if (folderList.isEmpty()) {
                Toast.makeText(this, "폴더가 없거나 로딩 중입니다. 다시 시도합니다.", Toast.LENGTH_SHORT).show()
                fetchFolders()
            } else {
                showFolderSelectionSheet()
            }
        }

        // 4. 파일 목록 보기 클릭
        binding.etFilePath.setOnClickListener {
            if (selectedFolderId == null) {
                Toast.makeText(this, "먼저 폴더를 선택해주세요.", Toast.LENGTH_SHORT).show()
            } else if (fileList.isEmpty()) {
                Toast.makeText(this, "선택된 폴더에 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                showFileSelectionSheet()
            }
        }
    }

    private fun showFolderSelectionSheet() {
        val dialog = BottomSheetDialog(this)

        // 1. 새 레이아웃 인플레이트
        val view = layoutInflater.inflate(R.layout.layout_folder_bottom_sheet, null)

        // 2. 새 RecyclerView ID 사용
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_folder_selection)

        // NOTE: 새 레이아웃에는 tv_selection_title이 없으므로, title 설정 부분은 제거합니다.
        // view.findViewById<TextView>(R.id.tv_selection_title).text = "과목 폴더 선택"

        val adapter = SubjectFolderAdapter(folderList) { selectedFolder ->
            binding.etFolderPath.setText(selectedFolder.title)
            selectedFolderId = selectedFolder.id
            binding.layoutFileInput.visibility = View.VISIBLE
            binding.etFilePath.setText("파일을 선택해주세요")
            fetchPresentations(selectedFolder.id)
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
        val uid = auth.currentUser?.uid ?: return

        if (selectedFolderId == null) {
            Toast.makeText(this, "폴더 정보가 없습니다. 다시 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("MoveToResult", "User: $uid, Folder: $selectedFolderId, Topic: ${file.topicId}, Doc: ${file.id}")
        val intent = Intent(this, AnalysisResultActivity::class.java)
        intent.putExtra("FILE_TITLE", file.title)
        intent.putExtra("FILE_SCORE", file.score)
        intent.putExtra("FILE_DATE", file.date)
        intent.putExtra("FILE_SUMMARY", file.summary)
        startActivity(intent)
    }

    /**
     * ★★★ 최종 수정: FolderFragment와 동일한 경로 및 필드명 사용 ★★★
     * 경로: user/{uid}/folders
     * 필드: name, isDeleted, created_at
     */
    private fun fetchFolders() {
        val uid = auth.currentUser?.uid ?: return

        // 경로 변경: contents -> user/{uid}/folders
        db.collection("user").document(uid).collection("folders")
            .orderBy("created_at", Query.Direction.DESCENDING) // 'created_at' 기준으로 정렬
            .get()
            .addOnSuccessListener { result ->
                folderList.clear()
                for (doc in result) {
                    // 필드명 변경: contentName -> name
                    val name = doc.getString("name") ?: "제목 없음"
                    val isDeleted = doc.getBoolean("isDeleted") ?: false

                    if (!isDeleted) {
                        // 타임스탬프 필드명 변경: createdAt -> created_at
                        // 타입 안전성 확보: Timestamp와 Long 모두 시도
                        val timestamp = try {
                            doc.getTimestamp("created_at")
                        } catch (e: Exception) {
                            val longValue = doc.getLong("created_at")
                            if (longValue != null) com.google.firebase.Timestamp(Date(longValue)) else null
                        }

                        val dateStr = getDateString(timestamp)

                        folderList.add(SubjectFolder(doc.id, name, dateStr))
                    }
                }
                if (folderList.isEmpty()) {
                    Log.d("FolderDebug", "No folders loaded for UID: $uid")
                    Toast.makeText(this, "등록된 폴더가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // 이 코드가 실행될 경우, user/{uid}/folders 경로에 대한 인덱스가 없는 것임.
                Log.e("FolderDebug", "Error loading folders: ${e.message}")
                Toast.makeText(this, "폴더 로드에 실패했습니다. (인덱스/네트워크 확인)", Toast.LENGTH_LONG).show()
            }
    }

    //모든 하위 주제와 발표 파일을 긁어모으고 이름 정리
    private fun fetchPresentations(folderId: String) {
        val uid = auth.currentUser?.uid ?: return

        fileList.clear()

        // 1. 먼저 해당 폴더의 모든 Topic(주제)들을 가져옵니다.
        // (주제 이름과 팀 이름을 알기 위해서입니다)
        db.collection("user").document(uid)
            .collection("folders").document(folderId)
            .collection("topics")
            .get()
            .addOnSuccessListener { topicDocs ->
                if (topicDocs.isEmpty) {
                    Toast.makeText(this, "저장된 주제가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Topic ID를 키로 하여 "주제명/팀명" 정보를 저장해둘 맵
                val topicInfoMap = HashMap<String, String>()
                val tasks = ArrayList<Task<QuerySnapshot>>()

                for (topic in topicDocs) {
                    val tName = topic.getString("topicName") ?: "무제"
                    val tTeam = topic.getString("teamInfo") ?: "팀 없음"

                    // 나중에 Presentation을 가져왔을 때 매칭하기 위해 저장
                    topicInfoMap[topic.id] = "$tName / $tTeam"

                    // 각 Topic의 presentations 컬렉션을 가져오는 작업 예약
                    val task = topic.reference.collection("presentations")
                        .orderBy("gradeAt", Query.Direction.DESCENDING)
                        .get()
                    tasks.add(task)
                }

                // 2. 모든 Topic의 발표 결과들을 병렬로 가져옵니다.
                Tasks.whenAllSuccess<QuerySnapshot>(tasks).addOnSuccessListener { results ->
                    val tempFiles = ArrayList<PresentationFile>()

                    // 모든 결과물을 하나로 합칩니다.
                    for (querySnapshot in results) {
                        for (doc in querySnapshot.documents) {
                            // 이 파일이 속한 Topic ID를 경로에서 추출 (user/.../topics/{topicId}/presentations/{docId})
                            val parentTopicId = doc.reference.parent.parent?.id

                            if (parentTopicId != null) {
                                val baseTitle = topicInfoMap[parentTopicId] ?: "알 수 없음"

                                // 점수 추출 로직
                                val score = doc.get("scores")?.let { scores ->
                                    if (scores is List<*>) {
                                        (scores.firstOrNull() as? Map<*, *>)?.get("scoreValue") as? Long
                                    } else null
                                }?.toInt() ?: 0

                                val summary = doc.getString("overallFeedback") ?: ""
                                val dateStr = getDateString(doc.getTimestamp("gradeAt"))

                                // 임시 리스트에 추가 (제목은 중복 처리 전 상태)
                                tempFiles.add(PresentationFile(
                                    id = doc.id,
                                    topicId = parentTopicId, // 1단계에서 추가한 필드
                                    title = baseTitle,
                                    date = dateStr,
                                    score = score,
                                    summary = summary
                                ))
                            }
                        }
                    }

                    if (tempFiles.isEmpty()) {
                        Toast.makeText(this, "분석 완료된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        // 3. 이름 중복 처리 로직 (주제/팀 (1), 주제/팀 (2)...)
                        val nameCountMap = HashMap<String, Int>() // 각 이름이 몇 번 나왔는지 카운트
                        val finalFiles = ArrayList<PresentationFile>()

                        // 날짜 최신순 정렬 (필요시)
                        tempFiles.sortByDescending { it.date }

                        // 중복된 이름이 있는지 확인하기 위해 리스트를 역순으로 돌거나,
                        // 혹은 단순히 리스트를 순회하며 카운팅을 합니다.
                        // 여기서는 "같은 이름 그룹"끼리 묶어서 번호를 매겨줍니다.

                        val groupedFiles = tempFiles.groupBy { it.title }

                        for ((title, list) in groupedFiles) {
                            if (list.size > 1) {
                                // 중복이 있으면 (1), (2) 붙이기
                                list.forEachIndexed { index, file ->
                                    val newTitle = "$title (${index + 1})"
                                    finalFiles.add(file.copy(title = newTitle))
                                }
                            } else {
                                // 중복 없으면 그대로
                                finalFiles.add(list[0])
                            }
                        }

                        // 4. 최종 리스트 업데이트 (날짜순 재정렬)
                        finalFiles.sortByDescending { it.date }
                        fileList.addAll(finalFiles)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SubjectFolder", "Error: ${e.message}")
                Toast.makeText(this, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getDateString(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it)
        } ?: "-"
    }
}