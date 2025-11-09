package com.minyook.overnight.ui.FirstScrean

import android.app.Activity
import android.content.Context
import android.content.Intent // ⭐️ Intent 사용을 위한 import
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.minyook.overnight.R
import com.minyook.overnight.databinding.FragmentLoginBinding
import com.minyook.overnight.ui.mainscrean.OvernightActivity // ⭐️ OvernightActivity 임포트

class LoginFragment : Fragment() {

    // SharedPreferences 설정을 위한 상수
    private val PREFS_FILE_NAME = "OvernightAppPrefs"
    private val USER_UID_KEY = "user_uid" // 사용자의 UID를 저장할 때 사용할 키(key)

    private val ACTION_TO_SIGN_UP = R.id.action_loginFragment_to_signUpFragment

    // ViewBinding 설정
    // 1. _binding: 뷰가 파괴될 때(onDestroyView) null로 초기화되어야 하므로 '?'(nullable)로 선언
    private var _binding: FragmentLoginBinding? = null
    // 2. binding: 뷰가 살아있는 동안(onCreateView ~ onDestroyView)에는 _binding을 null이 아니라고 보장(!!)하고 편하게 사용하기 위한 변수
    private val binding get() = _binding!!

    // Firebase Authentication 객체 선언
    private lateinit var auth: FirebaseAuth

    // (추가) Google 로그인을 위한 클라이언트 객체
    private lateinit var googleSignInClient: GoogleSignInClient

    // (추가) Google 로그인 결과를 처리할 ActivityResultLauncher
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase Auth 인스턴스 초기화
        auth = FirebaseAuth.getInstance()

        // Google 로그인 결과 처리기 초기화
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // 1. 로그인 결과가 'OK'(성공)인지 확인
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    // 2. Google 로그인 시도 및 성공
                    val account = task.getResult(ApiException::class.java)!!

                    // 3. (10단계에서 수정할 부분) Google 인증 성공! Firebase로 넘김
                    Log.d("Auth", "Google 인증 성공 (1/2단계), Firebase로 넘김: ${account.email}")
                    firebaseAuthWithGoogle(account)

                } catch (e: ApiException) {
                    // 4. Google 로그인 실패
                    Log.w("Auth", "Google sign in failed", e)
                    Toast.makeText(requireContext(), "Google 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 5. 사용자가 팝업을 그냥 닫음
                Log.w("Auth", "Google sign in cancelled or failed")
                Toast.makeText(requireContext(), "Google 로그인을 취소했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        //(로그인 상태인지 세션 확인 : 로그인 상태면 로그인 안하고 바로 메인화면)
        // checkLoginStatusAndNavigate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Google 로그인 옵션 설정 (웹 클라이언트 ID 필요)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // ⭐️ 중요!
            .requestEmail()
            .build()

        // GoogleSignInClient
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        setupClickListeners()  // 1. 각종 버튼 클릭 리스너 설정
        setupSignUpPromptText() // 2. '회원가입' 텍스트에 스타일(굵게) 적용
    }

    /**
     * 앱을 켰을 때 이미 로그인 상태인지(세션이 남아있는지) 확인하고
     * 로그인 상태라면 바로 메인 화면으로 넘겨주는 '자동 로그인' 함수
     */
    private fun checkLoginStatusAndNavigate() {
        // 1. 기기에 저장된 SharedPreferences 파일 열기
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        // 2. 저장된 "user_uid"가 있는지 확인합니다. 없으면 null을 반환합니다.
        val userUid = sharedPrefs.getString(USER_UID_KEY, null)

        // 3. Firebase에 현재 로그인된 사용자(auth.currentUser)가 있고,
        //    기기에도 저장된 UID(userUid)가 있다면, 자동 로그인 성공으로 간주
        if (auth.currentUser != null && userUid != null) {
            Log.d("Auth", "자동 로그인 성공. UID: $userUid")
            // 메인 화면로 이동
            navigateToOvernightActivity()
        }
    }

    /**
     * 화면에 있는 여러 버튼들의 클릭 이벤트를 한 곳에서 관리합니다.
     */
    private fun setupClickListeners() {
        // 1. 로그인 버튼
        binding.btnLogin.setOnClickListener {
            performLogin()  // 로그인 실행 함수 호출
        }

        // 2. Google 로그인 버튼
        binding.btnGoogleLogin.setOnClickListener {
            // 2-1. GoogleSignInClient에서 로그인 Intent(팝업)를 가져옵니다.
            val signInIntent = googleSignInClient.signInIntent

            // 2-2. 8단계에서 만든 '결과 처리기'로 해당 Intent를 실행(launch)합니다.
            googleSignInLauncher.launch(signInIntent)
        }

        // 3. Microsoft 로그인 버튼
        binding.btnMicrosoftLogin.setOnClickListener {
            // TODO: Microsoft 로그인 로직 구현
            Toast.makeText(requireContext(), "Microsoft 로그인 시도", Toast.LENGTH_SHORT).show()
        }

        // 4. "계정이 없으신가요? 회원가입" 텍스트
        binding.tvSignupPrompt.setOnClickListener {
            // Navigation Component를 사용해 '회원가입' 프래그먼트로 화면을 전환
            findNavController().navigate(ACTION_TO_SIGN_UP)
        }

        // 5. 비밀번호 찾기
        binding.tvForgotPassword.setOnClickListener {
            // TODO: 비밀번호 찾기 화면/다이얼로그 구현
            // FindPasswordActivity를 시작하기 위한 Intent 생성
            val intent = Intent(requireContext(), FindPasswordActivity::class.java)

            // Intent를 실행하여 새 Activity를 띄웁니다.
            startActivity(intent)
        }
    }

    /**
     * 사용자가 입력한 이메일과 비밀번호로 실제 로그인을 시도하는 핵심 함수
     */
    private fun performLogin() {
        // 1. 사용자가 입력한 이메일과 비밀번호 값을 가져옵니다. (trim()으로 양쪽 공백 제거)
        val email = binding.etEmail.text?.toString()?.trim()
        val password = binding.etPassword.text?.toString()?.trim()

        // 2. (유효성 검사) 이메일이나 비밀번호가 비어있는지 확인
        if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. 버튼 비활성화 (중복 클릭 방지)
        binding.btnLogin.isEnabled = false

        // 4. Firebase Auth에 이메일/비밀번호로 로그인을 시도하라고 요청
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->  // 5. 요청 결과(task)를 비동기적으로 받습니다.
                // 6. (결과 도착) 성공이든 실패든 버튼을 다시 활성화
                binding.btnLogin.isEnabled = true

                if (task.isSuccessful) {
                    // --- 7. 로그인 성공 ---
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener // UID 가져오기

                    // 7-1. (세션 유지) 자동 로그인을 위해 사용자 UID를 기기에 저장
                    saveUserUid(uid)

                    Toast.makeText(requireContext(), "로그인 성공: ${user.email}", Toast.LENGTH_LONG).show()
                    Log.d("Auth", "로그인 성공, UID 저장됨: $uid")

                    // 7-2. 메인 화면으로 이동
                    navigateToOvernightActivity()

                } else {
                    // --- 8. 로그인 실패 ---
                    Log.w("Auth", "로그인 실패", task.exception)  // 실패 원인을 로그에 기록
                    Toast.makeText(requireContext(), "로그인 실패: 아이디/비밀번호를 확인하세요.", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Google 로그인 성공 후, 받은 계정 정보(account)를
     * Firebase Authentication에 넘겨서 최종 로그인을 완료하는 함수
     */
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        // 1. Google 계정 정보(account.idToken)로 Firebase용 '인증서(credential)' 생성
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        // 2. 이 '인증서'를 사용해 Firebase에 로그인
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // --- 3. Firebase 로그인 성공! ---
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener

                    // 4. (세션 유지) 사용자 UID를 기기에 저장
                    saveUserUid(uid)

                    Toast.makeText(requireContext(), "Firebase 로그인 성공: ${user.email}", Toast.LENGTH_LONG).show()
                    Log.d("Auth", "Firebase 로그인 성공, UID 저장됨: $uid")

                    // 5. 메인 화면으로 이동
                    navigateToOvernightActivity()

                } else {
                    // --- 6. Firebase 로그인 실패 ---
                    Log.w("Auth", "Firebase signInWithCredential 실패", task.exception)
                    Toast.makeText(requireContext(), "Firebase 로그인에 실패했습니다.", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * 사용자 고유 ID(UID)를 SharedPreferences에 저장하여 세션처럼 유지
     */
    private fun saveUserUid(uid: String) {
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()  // 저장 모드 시작
            .putString(USER_UID_KEY, uid) // "user_uid"라는 키로 uid 값을 저장
            .apply() // 비동기적으로 저장
    }

    /**
     * 로그인 성공 후 메인 화면(OvernightActivity)으로 이동하고
     * 현재 액티비티(로그인/회원가입 화면)를 종료합니다.
     */
    private fun navigateToOvernightActivity() {
        // 1. OvernightActivity를 실행하기 위한 Intent 생성
        val intent = Intent(requireContext(), OvernightActivity::class.java)

        // 2. Activity 스택을 정리하는 플래그 설정
        // -> 뒤로 가기 버튼을 눌러도 다시 로그인 화면으로 돌아가지 않도록 이전 기록(스택)을 모두 삭제
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // 3. 메인 화면으로 이동
        startActivity(intent)

        // 4.  현재 프래그먼트를 호스팅하는 액티비티(AuthActivity)를 완전히 종료
        requireActivity().finish()
    }

    /**
     * "계정이 없으신가요? <b>회원가입</b>" 텍스트를 HTML로 변환하여 설정
     */
    @Suppress("DEPRECATION")
    private fun setupSignUpPromptText() {
        val text = getString(R.string.prompt_signup)
        binding.tvSignupPrompt.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
    }

    // 메모리 누수 방지를 위해 onDestroyView에서 바인딩 해제
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}