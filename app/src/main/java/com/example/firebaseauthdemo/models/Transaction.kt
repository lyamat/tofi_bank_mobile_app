package com.example.firebaseauthdemo.models

data class Transaction(
    var transactionAmount: String?,
    var transactionCurrency: String?,
    var transactionDate: String?,
    var transactionReceiverId: String?,
    var transactionSenderId: String?,
)