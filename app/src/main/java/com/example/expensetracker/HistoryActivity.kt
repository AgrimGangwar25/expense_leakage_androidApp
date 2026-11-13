package com.example.expensetracker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Build
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        hideSystemUI()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val dataString = prefs.getString("all_expenses", "[]")
        val jsonArray = JSONArray(dataString)
        val expenseList = mutableListOf<JSONObject>()

        for (i in 0 until jsonArray.length()) {
            expenseList.add(jsonArray.getJSONObject(i))
        }

        expenseList.sortByDescending { it.optLong("timestamp", 0L) }
        rvHistory.adapter = HistoryAdapter(expenseList)
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    class HistoryAdapter(private val list: List<JSONObject>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategory: TextView = view.findViewById(R.id.tvExpenseCategory)
            val tvDate: TextView = view.findViewById(R.id.tvExpenseDate)
            val tvAmount: TextView = view.findViewById(R.id.tvExpenseAmount)
            val tvType: TextView = view.findViewById(R.id.tvExpenseType)
            val cardType: MaterialCardView = view.findViewById(R.id.cardExpenseType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val isEssential = item.optBoolean("isEssential", true)
            val amount = item.optDouble("amount", 0.0)
            val timestamp = item.optLong("timestamp", 0L)

            holder.tvCategory.text = item.optString("category", "General")
            holder.tvAmount.text = "₹ ${String.format("%.2f", amount)}"

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(timestamp))

            val context = holder.itemView.context
            if (isEssential) {
                holder.tvType.text = "Essential"
                holder.tvType.setTextColor(ContextCompat.getColor(context, R.color.essential_green))
                holder.cardType.setCardBackgroundColor(ContextCompat.getColor(context, R.color.essential_bg))
            } else {
                holder.tvType.text = "Leakage"
                holder.tvType.setTextColor(ContextCompat.getColor(context, R.color.leakage_red))
                holder.cardType.setCardBackgroundColor(ContextCompat.getColor(context, R.color.leakage_bg))
            }
        }

        override fun getItemCount() = list.size
    }
}
