package com.example.firebaseauthdemo.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Credit(
    val credit_uid: String? = "",
    val credit_user_uid: String?,
    var credit_status: String?,
    val credit_amount: String?,
    var remaining_balance: String?,
    val credit_type: String?,
    val credit_term: String?,
    val interest_rate: String? = "0.14",
    val credit_term_remain: String?
): Parcelable
