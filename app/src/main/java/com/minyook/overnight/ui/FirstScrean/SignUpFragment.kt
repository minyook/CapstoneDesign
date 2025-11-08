package com.minyook.overnight.ui.FirstScrean
//test test test kyuchan
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log // Firebase 로그용 import 추가
import com.google.firebase.firestore.ktx.firestore // Firebase import 추가
import com.google.firebase.ktx.Firebase // Firebase import 추가
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.minyook.overnight.R
import com.minyook.overnight.databinding.FragmentSignUpBinding
import java.util.Calendar

class SignUpFragment : Fragment() {

    // ViewBinding 설정
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupLoginPromptText() // HTML 텍스트 설정
    }

    private fun setupClickListeners() {
        // 1. 툴바 뒤로가기 버튼
        binding.toolbar.setNavigationOnClickListener {
            // NavController를 사용해 이전 화면(로그인 프래그먼트)으로 돌아감
            findNavController().popBackStack()
        }

        // 2. 생년월일 입력 필드 클릭 시
        binding.etDate.setOnClickListener {
            showDatePickerDialog()
        }

        // 3. 생년월일 캘린더 아이콘 클릭 시
        binding.tilBirthdate.setEndIconOnClickListener {
            showDatePickerDialog()
        }

        // 4. 회원가입 버튼 클릭 시
        binding.btnSubmit.setOnClickListener {
            // TODO: 입력값 유효성 검사 (Validation) 로직 추가
            // TODO: ViewModel을 통해 실제 회원가입 로직 호출
            //Toast.makeText(requireContext(), "회원가입 시도", Toast.LENGTH_SHORT).show()
            saveUserDataToFirestore()
        }

        // 5. 로그인 프롬프트 텍스트 클릭 시
        binding.tvLoginPrompt.setOnClickListener {
            // TODO: Navigation Graph에 정의된 action ID로 변경해야 함
            // 예: findNavController().navigate(R.id.action_signUpFragment_to_loginFragment)

            // 우선 뒤로가기(popBackStack)로 로그인 화면으로 돌아가게 처리
            findNavController().popBackStack()
        }
    }

    private fun saveUserDataToFirestore() {
        // XML ID는 이전에 합의한대로 사용합니다.
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val date = binding.etDate.text.toString().trim() // 생년월일 (birth)

        // 유효성 검사 (필수 항목 확인)
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "이름, 이메일, 비밀번호는 필수 입력 항목입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Firestore 인스턴스 가져오기
        val db = Firebase.firestore

        // 2. 전송할 데이터 맵 생성 (DB 필드명과 일치시켜야 함)
        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "password" to password,
            "birth" to date, // Timestamp 타입으로 변환 없이 String으로 일단 저장합니다.
            "tel" to phone // tel 필드도 추가
        )

        // 3. "user" 컬렉션에 데이터 추가 (자동 ID 생성)
        db.collection("user")
            .add(userData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "회원가입 성공! DB에 저장되었습니다.", Toast.LENGTH_LONG).show()
                Log.d("SignUpFragment", "회원가입 데이터 추가 성공: $name")

                // 성공 후 다음 화면으로 이동하거나 (현재는 popBackStack)
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "DB 저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                Log.w("SignUpFragment", "회원가입 DB 추가 오류", e)
            }
    }

    /**
     * "이미 계정이 있으신가요? <b>로그인</b>" 텍스트를 HTML로 변환하여 설정
     */
    private fun setupLoginPromptText() {
        val text = getString(R.string.prompt_login)
        binding.tvLoginPrompt.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
    }

    /**
     * DatePicker 다이얼로그를 표시
     */
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                // 날짜 포맷 (예: 18/03/2024)
                val selectedDate = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etDate.setText(selectedDate)
            },
            year,
            month,
            day
        )
        // 미래 날짜는 선택 못하게 설정 (선택 사항)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    // 메모리 누수 방지를 위해 onDestroyView에서 바인딩 해제
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}