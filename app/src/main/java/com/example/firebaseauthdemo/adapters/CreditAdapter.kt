package com.example.firebaseauthdemo.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.databinding.ItemCreditBinding
import com.example.firebaseauthdemo.databinding.ItemCreditClosedBinding
import com.example.firebaseauthdemo.models.Credit

class CreditAdapter(private val listener: OnItemClickListener)
        : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class CreditViewHolder(private val binding: ItemCreditBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(credit: Credit, listener: OnItemClickListener) {

            binding.typeTw.text = "Type:"
            binding.remainingTw.text = "Remaining amount:"
            binding.statusTw.text = "Credit status:"

            binding.monthsTw.text = "months"
            binding.creditTypeTw.text = credit.credit_type
            binding.remainingCreditBalanceTw.text = credit.credit_term_remain
            binding.creditStatusTw.text = credit.credit_status

            binding.root.setOnClickListener {
                listener.onItemClick(credit)
            }

        }
    }


    private var credits: List<Credit> = listOf()


    fun updateCredits(newCredits: List<Credit>) {
        credits = newCredits
        notifyDataSetChanged()
    }

    override fun getItemCount() = credits.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemCreditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CreditViewHolder(binding)
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val credit = credits[position]
//        holder.itemView.setOnClickListener {
//            onItemClick?.invoke(credit)
//        }
        (holder as CreditViewHolder).bind(credit, listener)
    }

    interface OnItemClickListener {
        fun onItemClick(credit: Credit)
    }

}
