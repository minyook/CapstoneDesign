package com.minyook.overnight.ui.FirstScrean

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.databinding.FragmentLoginBinding
import com.minyook.overnight.ui.mainscrean.OvernightActivity

class LoginFragment : Fragment() {

    // 상수 정의
    private val PREFS_FILE_NAME = "OvernightAppPrefs"
    private val USER_UID_KEY = "user_uid"
    // Navigation Graph에서 정의된 회원가입 액션 ID
    private val ACTION_TO_SIGN_UP = R.id.action_loginFragment_to_signUpFragment

    // ViewBinding
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Firebase 객체
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Google Login 관련
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d("Auth", "Google 계정 인증 성공: ${account.email}")
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    Log.w("Auth", "Google 로그인 실패", e)
                    Toast.makeText(requireContext(), "구글 로그인 실패", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("Auth", "Google 로그인 취소됨")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        checkLoginStatusAndNavigate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 시스템 UI 숨기기 적용
        hideSystemUI()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        setupClickListeners()
        setupSignUpPromptText()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // --- [핵심 기능] Google 로그인 후 Firestore 연동 ---
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener

                    val userDocRef = db.collection("user").document(uid)

                    userDocRef.get().addOnSuccessListener { document ->
                        if (document.exists()) {
                            Log.d("Firestore", "기존 회원 로그인: $uid")
                            saveUserUid(uid)
                            Toast.makeText(requireContext(), "환영합니다! ${user.displayName}님", Toast.LENGTH_SHORT).show()
                            navigateToOvernightActivity()
                        } else {
                            // 신규 유저 -> DB 형식에 맞춰 데이터 생성
                            val newUserInfo = hashMapOf(
                                "email" to (user.email ?: ""),
                                "name" to (user.displayName ?: "Google User"),
                                "user_docid" to uid,
                                "birth" to "",
                                "phone" to ""
                            )

                            userDocRef.set(newUserInfo)
                                .addOnSuccessListener {
                                    Log.d("Firestore", "신규 구글 회원 DB 생성 완료")
                                    saveUserUid(uid)
                                    Toast.makeText(requireContext(), "구글 계정으로 가입되었습니다.", Toast.LENGTH_SHORT).show()
                                    navigateToOvernightActivity()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firestore", "DB 생성 실패", e)
                                    saveUserUid(uid)
                                    navigateToOvernightActivity()
                                }
                        }
                    }
                } else {
                    Log.w("Auth", "Firebase 인증 실패", task.exception)
                    Toast.makeText(requireContext(), "인증에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- 일반 이메일/비번 로그인 ---
    private fun performLogin() {
        val email = binding.etEmail.text?.toString()?.trim()
        val password = binding.etPassword.text?.toString()?.trim()

        if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.btnLogin.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener

                    saveUserUid(uid)
                    Toast.makeText(requireContext(), "로그인 성공", Toast.LENGTH_SHORT).show()
                    navigateToOvernightActivity()
                } else {
                    Toast.makeText(requireContext(), "로그인 실패: 정보를 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- 유틸리티 함수들 ---

    private fun checkLoginStatusAndNavigate() {
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        val userUid = sharedPrefs.getString(USER_UID_KEY, null)

        if (auth.currentUser != null && userUid != null) {
            navigateToOvernightActivity()
        }
    }

    private fun saveUserUid(uid: String) {
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(USER_UID_KEY, uid).apply()
    }

    private fun navigateToOvernightActivity() {
        val intent = Intent(requireContext(), OvernightActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener { performLogin() }

        binding.btnGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.btnMicrosoftLogin.setOnClickListener {
            Toast.makeText(requireContext(), "준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        // ⭐ 복구된 회원가입 클릭 리스너
        binding.tvSignupPrompt.setOnClickListener {
            findNavController().navigate(ACTION_TO_SIGN_UP)
        }

        binding.tvForgotPassword.setOnClickListener {
            val intent = Intent(requireContext(), FindPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupSignUpPromptText() {
        // XML에서 텍스트가 이미 설정되어 있거나, 리소스 파일을 통해 설정됨
        val text = getString(R.string.prompt_signup)
        binding.tvSignupPrompt.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun hideSystemUI() {
        val window = requireActivity().window
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}