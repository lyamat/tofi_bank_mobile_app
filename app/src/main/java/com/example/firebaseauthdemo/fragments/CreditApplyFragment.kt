package com.example.firebaseauthdemo.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.example.firebaseauthdemo.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.example.firebaseauthdemo.models.Credit

class CreditApplyFragment : Fragment(R.layout.fragment_apply_credit) {
    private var rootView: View? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var userMainUid: String


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        rootView = inflater.inflate(R.layout.fragment_apply_credit, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth =  FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()


        val creditAmountEt: EditText = requireView().findViewById(R.id.credit_amount_et)
        val userUid = auth.currentUser!!.uid


        val creditTypeRg: RadioGroup = requireView().findViewById(R.id.credit_type_rg)
        val monthsRg: RadioGroup = requireView().findViewById(R.id.months_rg)

        val fetchCreditBtn: Button = requireView().findViewById(R.id.fetch_credit_btn)
        val backButton: Button = requireView().findViewById(R.id.back_btn)

        val loadingPb: ProgressBar = requireView().findViewById(R.id.loading_pb)
        val cancelCreditTw: TextView = requireView().findViewById(R.id.cancel_credit_tw)

        cancelCreditTw.setOnClickListener {
            creditAmountEt.setText("")
            activity?.onBackPressed()
        }

        backButton.setOnClickListener {
            creditAmountEt.setText("")
            activity?.onBackPressed()
        }

        loadingPb.isVisible = false


        fetchCreditBtn.setOnClickListener {
            if (creditAmountEt.text.toString().isNotEmpty()) {
                loadingPb.isVisible = true

                val creditAmount = creditAmountEt.text.toString().filter { it.isDigit() }
                val creditTerm = when (monthsRg.checkedRadioButtonId) {
                    R.id.radioButton3Months -> "3"
                    R.id.radioButton6Months -> "6"
                    R.id.radioButton12Months -> "12"
                    else -> "0"
                }
                val creditType = when (creditTypeRg.checkedRadioButtonId) {
                    R.id.radioButtonAnnuity -> "annuity"
                    R.id.radioButtonDifferentiated -> "differentiated"
                    else -> ""
                }

                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        userMainUid = fetchUserMainUid(userUid)
                        createCredit(userMainUid, creditAmount, creditTerm, creditType)
                        Toast.makeText(
                            requireContext(),
                            "The credit has been successfully processed",
                            Toast.LENGTH_SHORT
                        ).show()

                        addCreditAmountToUserTransaction(userMainUid, creditAmount)
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Credit processing error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        loadingPb.isVisible = false
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Enter the amount of the credit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchUserMainUid(userUid: String) = suspendCoroutine<String> { continuation ->
        db.collection("users_sec").document(userUid).get().addOnSuccessListener { document ->
            continuation.resume(document.getString("user_main_uid").toString())
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }

    private suspend fun createCredit(userUid: String, creditAmount: String, creditTerm: String, creditType: String) = withContext(Dispatchers.IO) {
        val credit = Credit("", userUid, "active", creditAmount, creditAmount, creditType, creditTerm, "0.14", creditTerm)
        db.collection("credits").add(credit).await()
    }


    private suspend fun addCreditAmountToUserTransaction(userMainUid: String, amount: String) = withContext(Dispatchers.IO) {
        val receiverCardDocument = db.collection("cards")
            .whereEqualTo("user_uid", userMainUid)
            .get()
            .await()
            .documents
            .firstOrNull()

        if (receiverCardDocument != null) {
            val senderCardBalance = receiverCardDocument.getString("card_balance")!!.toDouble()

            receiverCardDocument.reference.update("card_balance", (senderCardBalance + amount.toDouble()).toString())


            val sdf = SimpleDateFormat("dd.MM.yyyy")
            val currentDate = sdf.format(Date())

            val transaction = hashMapOf(
                "transaction_sender_id" to "TOFI Bank Credit",
                "transaction_receiver_id" to userMainUid,
                "transaction_amount" to amount,
                "transaction_date" to currentDate.toString(),
                "transaction_currency" to "BYN"
            )

            db.collection("transactions").add(transaction).await()
        }

    }


    private fun replaceFragment(fragment: Fragment) {
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}

