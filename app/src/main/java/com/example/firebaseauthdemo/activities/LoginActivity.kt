package com.example.firebaseauthdemo.activities

import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import android.graphics.Color
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.firebaseauthdemo.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    private var auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnRegister: TextView = findViewById(R.id.go_to_register)
        btnRegister.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            finish()
        }

        val emailInput: TextInputEditText = findViewById(R.id.email_et)

        val btnLogin: Button = findViewById(R.id.login_btn)
        val passwordInput: TextInputEditText = findViewById(R.id.password_tet)

        btnLogin.setOnClickListener {
            val email: String = emailInput.text.toString().trim { it <= ' ' }
            val passwordInputText: String = passwordInput.text.toString().trim { it <= ' ' }
            val usersCollection = db.collection("users")
            val query: Query = usersCollection.whereEqualTo("user_email", email)

            query.get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val querySnapshot = task.result

                    if (querySnapshot != null && !querySnapshot.isEmpty) {
                        val user = querySnapshot.documents[0]
                        val storedPasswordHash = user.getString("user_password_hash")

                        if ((storedPasswordHash != null) && checkPassword(passwordInputText, storedPasswordHash)) {
                            val is2FAEnabled = user.getBoolean("user_auth_2fa")

                            if (is2FAEnabled == true) {
                                // 2FA is enabled, initiate SMS verification
                                val phoneNumber = user.getString("user_phone")
                                if (phoneNumber != null) {
                                    startPhoneNumberVerification(phoneNumber)
                                }
                            } else {
                                // 2FA is disabled, proceed with email/password authentication

                                auth.signInWithEmailAndPassword(email, passwordInputText)
                                    .addOnCompleteListener(this) { signInTask ->
                                        if (signInTask.isSuccessful) {
                                            // Authentication successful, navigate to MainActivity
                                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                            finish()
                                        } else {
                                            // Authentication failed, show error message
                                            Toast.makeText(this@LoginActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                            }
                        } else {
                            Toast.makeText(this@LoginActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.d(TAG, "User with email $email doesn't exist")
                        Toast.makeText(this@LoginActivity, "User not registered", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, task.exception.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }


        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // Show error message
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token

                val dialog = BottomSheetDialog(this@LoginActivity)
                dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
                val view = layoutInflater.inflate(R.layout.bottomsheet_verify, null)
                dialog.setCancelable(true)
                dialog.setContentView(view)

                val smsCode = dialog.findViewById<EditText>(R.id.smscode)!!
                val verificationTv = dialog.findViewById<TextView>(R.id.verifytext)!!
                val numberTv = dialog.findViewById<TextView>(R.id.phone_number_bottom_sheet)!!
                val resendTv = dialog.findViewById<TextView>(R.id.resend_btn)!!

                dialog.show()

                object : CountDownTimer(30000, 1000) {

                    override fun onTick(millisUntilFinished: Long) {
                        resendTv.text = "${getString(R.string.resend_code)} : " + millisUntilFinished / 1000
                    }

                    override fun onFinish() {
                        resendTv.isEnabled = true
                        resendTv.text = getString(R.string.resend_code)
                    }
                }.start()

                smsCode.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        if (s?.length == 6) {
                            val enteredCode = s.toString()
                            val credential = PhoneAuthProvider.getCredential(storedVerificationId, enteredCode)
                            signInWithPhoneAuthCredential(credential)
                        }
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    }
                })
            }
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        task.exception.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun checkPassword(password: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        return result.verified
    }
}
