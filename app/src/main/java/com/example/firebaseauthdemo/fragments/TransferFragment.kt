package com.example.firebaseauthdemo.fragments

import android.content.ContentValues.TAG
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import com.example.firebaseauthdemo.utils.CreditCardFormatWatcher
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

class TransferFragment : Fragment(R.layout.fragment_transfer) {
    private var rootView: View? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        rootView = inflater.inflate(R.layout.fragment_transfer, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        val auth: FirebaseAuth = FirebaseAuth.getInstance()

        val userUid = auth.currentUser!!.uid

        val recieverPan: EditText = requireView().findViewById(R.id.recieverPan)
        val totalSum: EditText = requireView().findViewById(R.id.totalSum)

        val confirmBtn: Button = requireView().findViewById(R.id.confirm_btn)
        val backButton: Button = requireView().findViewById(R.id.back_btn)

        val loadingPb: ProgressBar = requireView().findViewById(R.id.loading_pb)
        val cancelBtn: TextView = requireView().findViewById(R.id.cancel_transfer)

        cancelBtn.setOnClickListener {
            replaceFragment(DashboardFragment())

            recieverPan.setText("")
            totalSum.setText("")
        }

        loadingPb.isVisible = false


        backButton.setOnClickListener {
            replaceFragment(DashboardFragment())
        }

        recieverPan.addTextChangedListener(CreditCardFormatWatcher())

        confirmBtn.isEnabled = false
        confirmBtn.isClickable = false

        recieverPan.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pan = s?.filter { it.isDigit() }
                if (pan != null) {
                    if (pan.length == 16) {
                        Toast.makeText(requireContext(), "Card is ready", Toast.LENGTH_SHORT).show()
                        confirmBtn.isEnabled = true
                        confirmBtn.isClickable = true
                    } else {
                        confirmBtn.isEnabled = false
                        confirmBtn.isClickable = false
                    }
                }
            }
        })

        suspend fun fetchUserMainUid(userUid: String) = withContext(Dispatchers.IO) {
            db.collection("users_sec").document(userUid).get().await().getString("user_main_uid").toString()
        }

        suspend fun fetchCardBalance(userMainUid: String) = withContext(Dispatchers.IO) {
            val cardDocument = db.collection("cards")
                .whereEqualTo("user_uid", userMainUid)
                .get()
                .await()
                .documents
                .firstOrNull()

            cardDocument?.getString("card_balance")!!.toDouble()
        }

        suspend fun transferMoney(userMainUid: String, receiverCardNumber: String, amountToSend: Double) = withContext(Dispatchers.IO) {
            val senderCardDocument = db.collection("cards")
                .whereEqualTo("user_uid", userMainUid)
                .get()
                .await()
                .documents
                .firstOrNull()

            val receiverCardDocument = db.collection("cards")
                .whereEqualTo("card_number", receiverCardNumber)
                .get()
                .await()
                .documents
                .firstOrNull()

            if (senderCardDocument != null && receiverCardDocument != null) {
                val senderCardBalance = senderCardDocument.getString("card_balance")!!.toDouble()

                senderCardDocument.reference.update("card_balance", (senderCardBalance - amountToSend).toString())
                receiverCardDocument.reference.update("card_balance", (senderCardBalance + amountToSend).toString())

                val transactionSenderId = senderCardDocument.getString("user_uid").toString()
                val transactionReceiverId = receiverCardDocument.getString("user_uid").toString()
                val transactionCurrency = senderCardDocument.getString("card_currency").toString()

                val sdf = SimpleDateFormat("dd.MM.yyyy")
                val currentDate = sdf.format(Date())

                val transaction = hashMapOf(
                    "transaction_sender_id" to transactionSenderId,
                    "transaction_receiver_id" to transactionReceiverId,
                    "transaction_amount" to amountToSend.toString(),
                    "transaction_date" to currentDate.toString(),
                    "transaction_currency" to transactionCurrency
                )

                db.collection("transactions").add(transaction).await()
            } else {
                throw Exception("One of the cards does not exist")
            }
        }

        confirmBtn.setOnClickListener {
            if (totalSum.text.toString().isNotEmpty()) {
                loadingPb.isVisible = true

                val amountToSend = totalSum.text.toString().filter { it.isDigit() }.toDouble() // сумма для отправки
                val receiverCardNumber = recieverPan.text.toString().trim().filter { it.isDigit() } // номер карточки для отправки

                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        val userMainUid = fetchUserMainUid(userUid)
                        val cardBalance = fetchCardBalance(userMainUid)
//
                        if (cardBalance >= amountToSend) {
                            transferMoney(userMainUid, receiverCardNumber, amountToSend)
                            Toast.makeText(requireContext(), "Transfer successful", Toast.LENGTH_SHORT).show()


                            replaceFragment(DashboardFragment())
                            recieverPan.setText("")
                            totalSum.setText("")

                        } else {
                            Toast.makeText(requireContext(), "Insufficient balance", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error: ", e)
                        Toast.makeText(requireContext(), "Transfer failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        loadingPb.isVisible = false
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Enter the amount of money", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}

