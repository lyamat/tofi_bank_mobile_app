package com.example.firebaseauthdemo.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.firebaseauthdemo.R
import com.example.firebaseauthdemo.fragments.CreditApplyFragment
import com.example.firebaseauthdemo.fragments.DashboardFragment
import com.example.firebaseauthdemo.fragments.TransferFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var dashboardFragment: DashboardFragment
    private lateinit var transferFragment: TransferFragment
    private lateinit var creditFragment: CreditApplyFragment
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        dashboardFragment = DashboardFragment()
        transferFragment = TransferFragment()
        creditFragment = CreditApplyFragment()

        bottomNav = findViewById(R.id.bottom_navigation)

        replaceFragment(dashboardFragment, R.id.ic_dashboard)

        bottomNav.setOnItemSelectedListener { item ->
            handleNavigationItemSelected(item)
        }
    }

    private fun handleNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ic_logout -> showDialogLogOut()
            R.id.ic_dashboard -> replaceFragment(dashboardFragment, R.id.ic_dashboard)
            R.id.ic_operations -> replaceFragment(transferFragment, R.id.ic_operations)
            R.id.ic_credits -> replaceFragment(creditFragment, R.id.ic_credits)
            else -> replaceFragment(dashboardFragment, R.id.ic_dashboard)
        }
        return true
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is DashboardFragment) {
            showDialogLogOut()
        } else {
            replaceFragment(dashboardFragment, R.id.ic_dashboard)
        }
    }

    private fun showDialogLogOut() {
        AlertDialog.Builder(this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> logout(auth) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout(auth: FirebaseAuth) {
        auth.signOut()
        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
        finish()
    }

    private fun replaceFragment(fragment: Fragment, menuItemId: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
        bottomNav.menu.findItem(menuItemId).isChecked = true
    }
}
