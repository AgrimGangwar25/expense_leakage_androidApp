package com.example.expensetracker

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CategoryActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        val amount = intent.getStringExtra("amount") ?: "0.0"
        val sender = intent.getStringExtra("sender") ?: "Unknown"
        val message = intent.getStringExtra("message") ?: ""

        val tvDetails = findViewById<TextView>(R.id.tvTransactionDetails)
        tvDetails.text = "Amount: Rs. $amount"

        val rgEssential = findViewById<RadioGroup>(R.id.rgEssential)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        btnSave.setOnClickListener {
            val isEssential = rgEssential.checkedRadioButtonId == R.id.rbEssential
            val category = spinnerCategory.selectedItem.toString()

            saveExpenseLocally(amount, isEssential, category)
            sendToServer(sender, message, amount, isEssential, category)
        }
    }

    private fun saveExpenseLocally(amount: String, isEssential: Boolean, category: String) {
        val prefs = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val existingData = prefs.getString("all_expenses", "[]")
        val expenseArray = org.json.JSONArray(existingData)
        
        val newExpense = JSONObject().apply {
            put("amount", amount.toDoubleOrNull() ?: 0.0)
            put("isEssential", isEssential)
            put("category", category)
            put("timestamp", System.currentTimeMillis())
        }
        
        expenseArray.put(newExpense)
        prefs.edit().putString("all_expenses", expenseArray.toString()).apply()
    }

    private fun sendToServer(sender: String, message: String, amount: String, isEssential: Boolean, category: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("sender", sender)
                    put("message", message)
                    put("amount", amount)
                    put("isEssential", isEssential)
                    put("category", category)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("http://192.168.1.116:5000/sms")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    android.util.Log.d("ExpenseSync", "Server response: ${response.code}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpenseSync", "Failed to send: ${e.message}", e)
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@CategoryActivity, "Expense Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
