package com.example.firebaseauthdemo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.databinding.FragmentPayCreditBinding
import com.example.firebaseauthdemo.models.Credit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow
import kotlin.math.round

class CreditPayFragment : Fragment() {
    private var _binding: FragmentPayCreditBinding? = null
    private val binding get() = _binding!!

    private lateinit var db : FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userMainUid: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPayCreditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val userUid = auth.currentUser!!.uid

        val credit = arguments?.getParcelable<Credit>("credit")
        val creditUid = credit?.credit_uid

        binding.backBtn.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.cancelCreditTw.setOnClickListener {
            activity?.onBackPressed()
        }

        credit?.let {
            binding.loadingPb.isVisible = false
            binding.creditTypeTw.text = it.credit_type
            binding.creditRemainingBalanceTw.text = it.remaining_balance.toString()
            binding.creditTermTw.text = it.credit_term.toString()
            binding.creditAmountTw.text = it.credit_amount.toString()
            binding.interestRateTw.text = it.interest_rate.toString()
            binding.creditTermRemainTw.text = it.credit_term_remain.toString()

            val monthlyInterestRate = it.interest_rate!!.toDouble() / 12
            val monthlyPayment: Double

            if (it.credit_type == "annuity") {
                val annuityCoefficient = monthlyInterestRate * (1 + monthlyInterestRate).pow(it.credit_term!!.toDouble()) / ((1 + monthlyInterestRate).pow(
                    it.credit_term.toDouble()
                ) - 1)
                monthlyPayment = annuityCoefficient * it.credit_amount!!.toDouble()
            } else { // differentiated
                val principalPayment = it.credit_amount!!.toDouble() / it.credit_term!!.toDouble()
                val interestPayment = it.remaining_balance!!.toDouble() * monthlyInterestRate
                monthlyPayment = principalPayment + interestPayment
            }

            val roundedMonthlyPayment = round(monthlyPayment * 100) / 100
            binding.monthPaymentAmountTw.text = roundedMonthlyPayment.toString()

            binding.payCreditBtn.setOnClickListener {
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        binding.loadingPb.isVisible = true
                        updateCreditAndTransaction(userUid, creditUid!!, roundedMonthlyPayment)
                        Toast.makeText(
                            requireContext(),
                            "The credit has been successfully processed",
                            Toast.LENGTH_SHORT
                        ).show()
                        replaceFragment(DashboardFragment())

                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Credit processing error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        binding.loadingPb.isVisible = false
                    }
                }
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(credit: Credit) = CreditPayFragment().apply {
            arguments = Bundle().apply {
                putParcelable("credit", credit)
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

    private suspend fun updateCreditAndTransaction(userUid: String, creditUid: String, monthlyPayment: Double) = withContext(Dispatchers.IO) {
        val userMainUid = fetchUserMainUid(userUid)


        val creditDocument = db.collection("credits")
            .document(creditUid)
            .get()
            .await()


        val cardDocument = db.collection("cards")
            .whereEqualTo("user_uid", userMainUid)
            .get()
            .await()
            .documents
            .firstOrNull()

        if (creditDocument != null && cardDocument != null) {
            val cardBalance = cardDocument.getString("card_balance")!!.toDouble()
            if (cardBalance > monthlyPayment) {

                val remainingBalance = creditDocument.getString("remaining_balance")!!.toDouble()
                val creditTermRemain = creditDocument.getString("credit_term_remain")!!.toInt()
                val creditStatus = if (creditTermRemain - 1 == 0) "closed" else "active"

                creditDocument.reference.update(
                    mapOf(
                        "remaining_balance" to (round((remainingBalance - monthlyPayment) * 100) / 100).toString(),
                        "credit_term_remain" to (creditTermRemain - 1).toString(),
                        "credit_status" to creditStatus
                    )
                )

                cardDocument.reference.update("card_balance", (round((cardBalance - monthlyPayment) * 100) / 100).toString())

                val sdf = SimpleDateFormat("dd.MM.yyyy")
                val currentDate = sdf.format(Date())

                val transaction = hashMapOf(
                    "transaction_sender_id" to userMainUid,
                    "transaction_receiver_id" to "TOFI Bank Month payment",
                    "transaction_amount" to monthlyPayment.toString(),
                    "transaction_date" to currentDate.toString(),
                    "transaction_currency" to "BYN"
                )

                db.collection("transactions").add(transaction).await()
            }
            else {
                throw Exception("Card balance < Monthly payment")
            }
        }
    }


    private fun replaceFragment(fragment: Fragment) {
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}

