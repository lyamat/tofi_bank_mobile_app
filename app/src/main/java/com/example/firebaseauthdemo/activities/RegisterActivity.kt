package com.example.firebaseauthdemo.activities

import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.firebaseauthdemo.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


class RegisterActivity : AppCompatActivity() {
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    private var storedVerificationId: String? = ""
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    private var auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private lateinit var phoneInput: TextInputEditText
    private lateinit var firstnameInput: TextInputEditText
    private lateinit var lastnameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnRegister: Button = findViewById(R.id.register_btn)

        passwordInput = findViewById(R.id.password_tet)

        phoneInput = findViewById(R.id.phone_number_et)

        firstnameInput = findViewById(R.id.name_et)
        lastnameInput = findViewById(R.id.last_name_et)
        emailInput = findViewById(R.id.email_et)

        val db: FirebaseFirestore = FirebaseFirestore.getInstance()

        val btnLogin: TextView = findViewById(R.id.go_to_login_tv)
        btnLogin.setOnClickListener {
            startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
            finish()
        }

        btnRegister.setOnClickListener {
            val phoneInputText: String = phoneInput.text.toString().trim { it <= ' '}
            val phone: String = "+375$phoneInputText"

            startPhoneNumberVerification(phone)
        }

        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @RequiresApi(Build.VERSION_CODES.O)
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

                val dialog = BottomSheetDialog(this@RegisterActivity)
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
                    @RequiresApi(Build.VERSION_CODES.O)
                    override fun afterTextChanged(s: Editable?) {
                        if (s?.length == 6) {
                            val enteredCode = s.toString()
                            val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, enteredCode)
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val phoneInputText: String = phoneInput.text.toString().trim { it <= ' '}
                    val phone = "+375$phoneInputText"
                    val user = hashMapOf(
                        "user_phone" to phone,
                        "user_email" to emailInput.text.toString(),
                        "user_firstName" to firstnameInput.text.toString(),
                        "user_lastName" to lastnameInput.text.toString(),
                        "user_auth_2fa" to false,
                        "user_password_hash" to getPasswordHash(passwordInput.text.toString())
                    )


                    val card = hashMapOf(
                        "user_uid" to auth.currentUser?.uid.toString(),
                        "card_number" to generateRandomCardNumber(),
                        "card_balance" to "150.12",
                        "card_expiry_date" to getCardExpiryDate(),
                        "card_currency" to "BYN",
                        "card_cvv" to generateRandomCardCvv()
                    )

                    db.collection("users")
                        .document(task.result?.user?.uid!!)
                        .set(user)
                        .addOnSuccessListener {
                            Log.d(TAG, "User profile created for ID: ${task.result?.user?.uid}")

                            db.collection("cards")
                                .add(card)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(TAG, "Card created with ID: ${documentReference.id}")
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error adding card", e)
                                }

                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error adding user", e)
                        }

                    val auth = FirebaseAuth.getInstance()
                    val mainUserUid = auth.currentUser!!.uid

                    auth.createUserWithEmailAndPassword(emailInput.text.toString(), passwordInput.text.toString())
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {

                                val userSec = hashMapOf(
                                    "user_main_uid" to mainUserUid
                                )

                                db.collection("users_sec")
                                    .document(task.result?.user?.uid!!)
                                    .set(userSec)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "User_sec profile created for ID: ${task.result?.user?.uid}")

                                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w(TAG, "Error adding user", e)
                                    }
                            } else {
                                Toast.makeText(baseContext, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }



                } else {
                    // Обработка неудачной аутентификации
                }
            }
    }


    private fun getPasswordHash(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun generateRandomCardNumber(): String {
        val cardNumber = StringBuilder("4") // Начинаем с "4"

        // Генерируем оставшиеся 15 цифр
        repeat(15) {
            val digit = (0..9).random()
            cardNumber.append(digit)
        }

        return cardNumber.toString()
    }

    private fun generateRandomCardCvv(): String {
        val cardCvv = StringBuilder("")

        repeat(3) {
            val digit = (0..9).random()
            cardCvv.append(digit)
        }

        return cardCvv.toString()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCardExpiryDate(): String {
        val today = LocalDate.now()
        val futureDate = today.plusYears(5)
        val futureMonthAndYear = futureDate.format(DateTimeFormatter.ofPattern("MM/yy"))

        return futureMonthAndYear
    }

}

