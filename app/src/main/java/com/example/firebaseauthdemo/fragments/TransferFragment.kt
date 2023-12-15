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
import androidx.core.widget.addTextChangedListener
import com.example.firebaseauthdemo.utils.CreditCardFormatWatcher
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.utils.DecimalDigitsInputFilter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.RoundingMode
import java.text.DecimalFormat
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

        val recieverPan: EditText = requireView().findViewById(R.id.reciever_pan)
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

        totalSum.filters = arrayOf(DecimalDigitsInputFilter(8, 2))

        recieverPan.addTextChangedListener(CreditCardFormatWatcher())

        confirmBtn.isEnabled = false
        confirmBtn.isClickable = false

        recieverPan.addTextChangedListener(object : TextWatcher {
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

            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        suspend fun fetchUserMainUid(userUid: String) = withContext(Dispatchers.IO) {
            db.collection("users_sec").document(userUid).get().await().getString("user_main_uid").toString()
        }

        suspend fun performTransaction(amount: Double, receiverCardNumber: String, senderMainUid: String): PerformTransactionResult {
            val senderCardDocument = db.collection("cards")
                .whereEqualTo("user_uid", senderMainUid)
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

                if (senderCardBalance >= amount) {
                    // Обновление баланса отправившего
                    senderCardDocument.reference.update("card_balance",
                        (senderCardBalance - amount).toBigDecimal().setScale(2, RoundingMode.UP)
                            .toString()
                    )

                    // Обновление баланса получателя
                    receiverCardDocument.reference.update("card_balance",
                        (senderCardBalance + amount).toBigDecimal().setScale(2, RoundingMode.UP)
                            .toString()
                    )

                    // Создание транзакции
                    val transactionSenderId = senderCardDocument.getString("user_uid").toString()
                    val transactionReceiverId =
                        receiverCardDocument.getString("user_uid").toString()
                    val transactionCurrency =
                        senderCardDocument.getString("card_currency").toString()

                    val sdf = SimpleDateFormat("dd.MM.yyyy")
                    val currentDate = sdf.format(Date())

                    val transaction = hashMapOf(
                        "transaction_sender_id" to transactionSenderId,
                        "transaction_receiver_id" to transactionReceiverId,
                        "transaction_amount" to amount.toBigDecimal().setScale(2, RoundingMode.UP)
                            .toString(),
                        "transaction_date" to currentDate.toString(),
                        "transaction_currency" to transactionCurrency
                    )

                    db.collection("transactions").add(transaction).await()

//                    replaceFragment(TransferFragment())

                    return PerformTransactionResult.Success
                } else {
                    return PerformTransactionResult.Error("Not enough money for transaction\nYour balance: $senderCardBalance")
                }
            } else {
                return PerformTransactionResult.Error("One of the cards does not exist")
            }
        }


        confirmBtn.setOnClickListener {
            if (totalSum.text.toString().isNotEmpty() && isValidAmount(totalSum.text.toString())) {
                val amount = totalSum.text.toString().toDouble()
                val receiverCardNumber = recieverPan.text.toString().trim().filter { it.isDigit() }

                GlobalScope.launch(Dispatchers.Main) {
                    val userMainUid = try {
                        fetchUserMainUid(userUid)
                    } catch (e: Exception) {
                        Log.d(TAG, "Error fetching user main uid", e)
                        Toast.makeText(requireContext(), "Error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val senderCardDocument = try {
                        db.collection("cards")
                            .whereEqualTo("user_uid", userMainUid)
                            .get()
                            .await()
                            .documents
                            .firstOrNull()
                    } catch (e: Exception) {
                        Log.d(TAG, "Error fetching sender card document", e)
                        Toast.makeText(requireContext(), "Error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    if (senderCardDocument != null) {
                        val senderCardBalance = senderCardDocument.getString("card_balance")!!.toDouble()

                        if (senderCardBalance >= amount) {
                            if (amount > 5000) {
                                // Запустить тест на трезвость
                                val drunkTestFragment = DrunkTestFragment()
                                drunkTestFragment.setOnDrunkTestCompleteListener(object : DrunkTestFragment.DrunkTestCompleteListener {
                                    override fun onDrunkTestComplete(isDrunk: Boolean) {
                                        if (isDrunk) {
//                                            Toast.makeText(requireContext(), "Please sober up before making this transaction", Toast.LENGTH_SHORT).show()
                                        } else {
                                            GlobalScope.launch(Dispatchers.Main) {
                                                performTransaction(amount, receiverCardNumber, userMainUid)
                                            }
                                        }
                                    }
                                })
                                replaceFragment(drunkTestFragment)
                            } else {
                                performTransaction(amount, receiverCardNumber, userMainUid)
                            }
                        } else {
                            Toast.makeText(requireContext(), "Not enough money for transaction\nYour balance: $senderCardBalance", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "One of the cards does not exist", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Enter the amount of money", Toast.LENGTH_SHORT).show()
            }
        }
    }

    sealed class PerformTransactionResult {
        object Success : PerformTransactionResult()
        data class Error(val message: String) : PerformTransactionResult()
    }

    private fun isValidAmount(amountText: String): Boolean {
        val amount = amountText.toDoubleOrNull() ?: return false
        return amount > 0
    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}

