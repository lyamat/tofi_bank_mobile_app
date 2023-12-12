package com.example.firebaseauthdemo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.models.Transaction

class TransactionAdapter(private val userUid: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var transactions: List<Transaction> = listOf()

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    override fun getItemCount() = transactions.size

    override fun getItemViewType(position: Int): Int {
        val transaction = transactions[position]
        return if (transaction.transactionReceiverId == userUid) {
            R.layout.item_history_income
        } else {
            R.layout.item_history_outcome
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return TransactionViewHolder(userUid, view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val transaction = transactions[position]
        (holder as TransactionViewHolder).bind(transaction)
    }

    class TransactionViewHolder(private val userUid: String, itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameTv: TextView = itemView.findViewById(R.id.name_tv)
        private val amountTv: TextView = itemView.findViewById(R.id.amount_tv)
        private val currencyTv: TextView = itemView.findViewById(R.id.currency_tv)
        private val dateTv: TextView = itemView.findViewById(R.id.date_tv)

        fun bind(transaction: Transaction) {
            nameTv.text = if (transaction.transactionSenderId == userUid) {
                transaction.transactionReceiverId
            } else {
                transaction.transactionSenderId
            }
            amountTv.text = transaction.transactionAmount
            currencyTv.text = transaction.transactionCurrency
            dateTv.text = transaction.transactionDate
        }
    }
}
