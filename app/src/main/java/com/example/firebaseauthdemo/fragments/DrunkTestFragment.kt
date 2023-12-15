package com.example.firebaseauthdemo.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.firebaseauthdemo.R
import java.util.*

class DrunkTestFragment : Fragment() {

    interface DrunkTestCompleteListener {
        fun onDrunkTestComplete(isDrunk: Boolean)
    }

    private var onDrunkTestComplete: DrunkTestCompleteListener? = null

    fun setOnDrunkTestCompleteListener(listener: DrunkTestCompleteListener) {
        onDrunkTestComplete = listener
    }

    private lateinit var timerTv: TextView
    private lateinit var curNumberTv: TextView
    private lateinit var gridLayout: GridLayout
    private lateinit var startTestBtn: Button
    private lateinit var cancelTestBtn: TextView
    private lateinit var resultMsTv: TextView
    private lateinit var errorPercentTv: TextView

    private var testStartTime: Long = 0L
    private var digitStartTime: Long = 0L
    private var totalClicks = 0
    private var correctClicks = 0
    private var reactionTimes = mutableListOf<Long>()

    private val handler = Handler(Looper.getMainLooper()) // Initialize handler here


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drunk_test, container, false)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null) // Stop any pending timer tasks
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reactionTimes.clear()
        testStartTime = 0L
        totalReactionTime = 0L
        curNumberTv.text = ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timerTv = view.findViewById(R.id.timer_tw)
        curNumberTv = view.findViewById(R.id.cur_number_tw)
        gridLayout = view.findViewById(R.id.grid_layout)
        startTestBtn = view.findViewById(R.id.start_test_btn)
        cancelTestBtn = view.findViewById(R.id.cancel_test_tw)
        resultMsTv = view.findViewById(R.id.result_ms_tw)
        errorPercentTv = view.findViewById(R.id.error_precent_tw)


        setupGrid()
        startTestBtn.setOnClickListener { startTest() }
        cancelTestBtn.setOnClickListener { activity?.onBackPressed() }

        updateErrorPercent(0.0) // Initially no errors
        updateReactionTime(0) // Initially no reaction time

        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.isClickable = false
        }
    }

    private fun setupGrid() {
        val random = Random()
        val numbers = (1..9).shuffled()

        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.text = numbers[i].toString()
            button.setOnClickListener { handleButtonClick(it as Button) }
        }
        digitStartTime = System.currentTimeMillis()
    }

    private fun startTest() {
        totalReactionTime = 0L
        resultMsTv.text = ""
        errorPercentTv.text = ""

        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.isClickable = true
        }
        testStartTime = System.currentTimeMillis()
        digitStartTime = System.currentTimeMillis()
        totalClicks = 0
        correctClicks = 0
        reactionTimes.clear()

        curNumberTv.text = getRandomNumberText()
        refreshTimer(10)

        startTestBtn.isEnabled = false
        gridLayout.isClickable = true

        val handler = this.handler // Use the fragment's handler
        val runnable = object : Runnable {
            override fun run() {
                val remainingTime = 10 - (System.currentTimeMillis() - testStartTime) / 1000
                if (remainingTime > 0) {
                    refreshTimer(remainingTime)
                    handler.postDelayed(this, 1000)
                } else {
                    stopTest()
                }
            }
        }
        handler.post(runnable)
    }

    private var totalReactionTime: Long = 0L

    private fun handleButtonClick(button: Button) {
        totalClicks++

        val clickedNumber = button.text.toString().toInt()
        val displayedNumber = getDisplayedNumberInt()

        if (clickedNumber == displayedNumber) {
            correctClicks++
            val reactionTime = System.currentTimeMillis() - digitStartTime
            reactionTimes.add(reactionTime)
            totalReactionTime += reactionTime
            updateReactionTime(calculateAverageReactionTime()) // Update average after each correct click
            curNumberTv.text = getRandomNumberText()
            setupGrid()
        }
        updateErrorPercent(calculateErrorPercent())
    }

    private fun refreshTimer(seconds: Long) {
        val minutes = seconds / 60
        val secondsLeft = seconds % 60
        timerTv.text = String.format("%02d:%02d", minutes, secondsLeft)
    }

    private fun stopTest() {
        gridLayout.isClickable = false
        startTestBtn.isEnabled = true

        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.isClickable = false
        }

        if (calculateAverageReactionTime() > 2500) {
            Toast.makeText(requireContext(), "Please sober up before making this transaction", Toast.LENGTH_SHORT).show()
        }
        else {
            onDrunkTestComplete?.onDrunkTestComplete(false)
            activity?.onBackPressed()
        }
        // Send result back to TransferFragment

    }

    private fun getRandomNumberText(): String {
        val numbers = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
        return numbers[Random().nextInt(numbers.size)]
    }

    private fun getDisplayedNumberInt(): Int {
        val numberText = curNumberTv.text.toString()
        val numbersMap = mapOf(
            "One" to 1,
            "Two" to 2,
            "Three" to 3,
            "Four" to 4,
            "Five" to 5,
            "Six" to 6,
            "Seven" to 7,
            "Eight" to 8,
            "Nine" to 9,
        )

        return numbersMap[numberText] ?: throw IllegalArgumentException("Unexpected displayed number: $numberText")
    }

    private fun calculateAverageReactionTime(): Long {
        if (correctClicks == 0) {
            return 0
        }
        return totalReactionTime / correctClicks
    }

    private fun calculateErrorPercent(): Double {
        return (totalClicks - correctClicks).toDouble() / totalClicks * 100
    }

    private fun updateReactionTime(averageReactionTime: Long) {
        resultMsTv.text = String.format("%03d ms", averageReactionTime)
    }

    private fun updateErrorPercent(errorPercent: Double) {
        errorPercentTv.text = String.format("%.2f%%", errorPercent)
    }

}

