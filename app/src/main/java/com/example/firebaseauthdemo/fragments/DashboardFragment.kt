package com.example.firebaseauthdemo.fragments

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.adapters.CreditAdapter
import com.example.firebaseauthdemo.models.Transaction
import com.example.firebaseauthdemo.adapters.TransactionAdapter
import com.example.firebaseauthdemo.models.Card
import com.example.firebaseauthdemo.models.Credit
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DashboardFragment : Fragment() {

    private lateinit var transactionRecyclerview : RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter

    private lateinit var creditRecyclerview : RecyclerView
    private lateinit var creditAdapter: CreditAdapter

    private lateinit var db : FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userMainUid: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val userUid = auth.currentUser!!.uid
        val userFirstnameTw: TextView = requireView().findViewById(R.id.userFirstname)

        val cardNameTw: TextView = requireView().findViewById(R.id.cardName)
        val cardPanTw: TextView = requireView().findViewById(R.id.cardPan)

        val cardBalanceTw: TextView = requireView().findViewById(R.id.cardBalance)
        val cardOwnerTw: TextView = requireView().findViewById(R.id.cardOwner)
        val cardExpTw: TextView = requireView().findViewById(R.id.cardExp)
        val cardCurrencyTw: TextView = requireView().findViewById((R.id.cardCurrency))


        val transactionsTextTw: TextView = requireView().findViewById((R.id.transactions_text))
        val creditsTextTw: TextView = requireView().findViewById((R.id.credits_text))

        transactionRecyclerview = requireView().findViewById(R.id.trans_list)
        creditRecyclerview = requireView().findViewById(R.id.credit_list)

        creditRecyclerview.isVisible = false

        transactionsTextTw.setOnClickListener{
            transactionRecyclerview.isVisible = true
            creditRecyclerview.isVisible = false
        }

        creditsTextTw.setOnClickListener {
            creditRecyclerview.isVisible = true
            transactionRecyclerview.isVisible = false
        }

        cardNameTw.text = "TOFI Bank"

        suspend fun fetchUserMainUid(userUid: String) = suspendCoroutine<String> { continuation ->
            db.collection("users_sec").document(userUid).get().addOnSuccessListener { document ->
                continuation.resume(document.getString("user_main_uid").toString())
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
        }

        suspend fun fetchUserDetails(userMainUid: String) = suspendCoroutine<Pair<String, String>> { continuation ->
            db.collection("users").document(userMainUid).get().addOnSuccessListener { userDocument ->
                if (userDocument != null) {
                    val userFirstName = userDocument.getString("user_firstName")
                    val userLastName = userDocument.getString("user_lastName")
                    continuation.resume(Pair(userFirstName, userLastName) as Pair<String, String>)
                } else {
                    continuation.resumeWithException(Exception("No such document in users_sec"))
                }
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
        }

        suspend fun fetchCards(userMainUid: String) = suspendCoroutine<List<Card>> { continuation ->
            db.collection("cards")
                .whereEqualTo("user_uid", userMainUid)
                .get()
                .addOnSuccessListener { documents ->
                    val cards = documents.map { document ->
                        Card(
                            cardBalance = document.getString("card_balance"),
                            cardCurrency = document.getString("card_currency"),
                            cardExpiryDate = document.getString("card_expiry_date"),
                            cardNumber = document.getString("card_number")
                        )
                    }
                    continuation.resume(cards)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                userMainUid = fetchUserMainUid(userUid)
                val (userFirstName, userLastName) = fetchUserDetails(userMainUid)
                val cards = fetchCards(userMainUid)

                userFirstnameTw.text = userFirstName
                cardOwnerTw.text = "$userFirstName $userLastName".uppercase()

                cardPanTw.text = cards[0].cardNumber!!.chunked(4).joinToString(" ")
                cardBalanceTw.text = cards[0].cardBalance.toString()
                cardCurrencyTw.text = cards[0].cardCurrency
                cardExpTw.text = cards[0].cardExpiryDate

                transactionAdapter = TransactionAdapter(userMainUid)
                transactionRecyclerview.adapter = transactionAdapter
                transactionRecyclerview.layoutManager = LinearLayoutManager(requireContext())

                creditAdapter = CreditAdapter(viewBindingOnItemClickListener())

                creditRecyclerview.adapter = creditAdapter
                creditRecyclerview.layoutManager = LinearLayoutManager(requireContext())


                getUserData(userMainUid)

            } catch (e: Exception) {
                Log.d(TAG, "Error: ", e)
                // Показать сообщение об ошибке пользователю
            }
        }

    }

    private fun getUserData(userMainUid: String) {
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()

        val receivedTransactionsQuery = db.collection("transactions")
            .whereEqualTo("transaction_receiver_id", userMainUid)
            .get()

        val sentTransactionsQuery = db.collection("transactions")
            .whereEqualTo("transaction_sender_id", userMainUid)
            .get()

        val creditsQuery = db.collection("credits")
            .whereEqualTo("credit_user_uid", userMainUid)
            .get()

        val allTransactionsTask = Tasks.whenAllComplete(receivedTransactionsQuery, sentTransactionsQuery)
            .continueWithTask { task ->
                val allTransactions = ArrayList<Transaction>()
                task.result?.forEach { querySnapshotTask ->
                    val transactions = (querySnapshotTask.result as QuerySnapshot).documents.map { document ->
                        Transaction(
                            transactionSenderId = document.getString("transaction_sender_id").toString(),
                            transactionReceiverId = document.getString("transaction_receiver_id").toString(),
                            transactionAmount = document.getString("transaction_amount").toString(),
                            transactionDate = document.getString("transaction_date").toString(),
                            transactionCurrency = document.getString("transaction_currency").toString(),
                        )
                    }
                    allTransactions.addAll(transactions)
                }
                Tasks.forResult(allTransactions)
            }

        allTransactionsTask.addOnSuccessListener { transactions ->
            transactionAdapter.updateTransactions(transactions)
        }

        val allCreditsTask = creditsQuery.continueWithTask { task ->
            val allCredits = ArrayList<Credit>()
            val credits = (task.result as QuerySnapshot).documents.map { document ->
                Credit(
                    credit_uid = document.id,
                    credit_user_uid = document.getString("credit_user_uid"),
                    credit_status = document.getString("credit_status"),
                    credit_amount = document.getString("credit_amount") ?: "not get",
                    remaining_balance = document.getString("remaining_balance") ?: "not get",
                    credit_type = document.getString("credit_type"),
                    credit_term = document.getString("credit_term")?: "not get",
                    interest_rate = document.getString("interest_rate")?: "0.14",
                    credit_term_remain = document.getString("credit_term_remain")?: "not get",
                )
            }
            allCredits.addAll(credits)
            Tasks.forResult(allCredits)
        }

        allCreditsTask.addOnSuccessListener { credits ->
            creditAdapter.updateCredits(credits)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }

    private fun viewBindingOnItemClickListener(): CreditAdapter.OnItemClickListener = object : CreditAdapter.OnItemClickListener {
        override fun onItemClick(credit: Credit) {
            if (credit.credit_status == "active") {
                val fragment = CreditPayFragment.newInstance(credit)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

    }

}