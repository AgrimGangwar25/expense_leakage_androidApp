package com.example.expensetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private val OVERLAY_PERMISSION_CODE = 102
    private val CHANNEL_ID = "budget_alerts"
    
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvMoneyWasted: TextView
    private lateinit var tvRemainingBudget: TextView
    private lateinit var pieChart: PieChart
    private lateinit var rvRecentExpenses: RecyclerView
    private lateinit var btnReset: MaterialButton
    private lateinit var btnSetBudget: MaterialButton
    private lateinit var cardRecentExpenses: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        createNotificationChannel()

        // Initialize UI components
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvMoneyWasted = findViewById(R.id.tvMoneyWasted)
        tvRemainingBudget = findViewById(R.id.tvRemainingBudget)
        pieChart = findViewById(R.id.pieChart)
        rvRecentExpenses = findViewById(R.id.rvRecentExpenses)
        btnReset = findViewById(R.id.btnReset)
        btnSetBudget = findViewById(R.id.btnSetBudget)
        cardRecentExpenses = findViewById(R.id.cardRecentExpenses)

        rvRecentExpenses.layoutManager = LinearLayoutManager(this)

        btnReset.setOnClickListener {
            resetData()
        }

        btnSetBudget.setOnClickListener {
            showSetBudgetDialog()
        }

        cardRecentExpenses.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        setupChart()
        checkPermissions()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Budget Alerts"
            val descriptionText = "Notifications when spending exceeds budget"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSetBudgetDialog() {
        val prefs = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val currentBudget = prefs.getFloat("budget_limit", 10000f)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Monthly Budget")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        // Remove .0 if it's a whole number
        val budgetText = if (currentBudget % 1.0f == 0.0f) currentBudget.toInt().toString() else currentBudget.toString()
        input.setText(budgetText)
        builder.setView(input)

        builder.setPositiveButton("Set") { _, _ ->
            val newBudget = input.text.toString().toFloatOrNull() ?: 10000f
            prefs.edit().putFloat("budget_limit", newBudget).apply()
            updateDashboard()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun setupChart() {
        pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setCenterText("")
            setCenterTextSize(16f)
            
            // Fix: Hide labels on slices (the words)
            setDrawEntryLabels(false) 
            
            legend.isEnabled = true
            legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
            legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            legend.textSize = 12f
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), PERMISSION_CODE)
        }

        // Overlay permission for the popup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
        }
    }

    private fun resetData() {
        getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE).edit().putString("all_expenses", "[]").apply()
        updateDashboard()
        Toast.makeText(this, "Data Reset Successfully", Toast.LENGTH_SHORT).show()
    }

    private fun updateDashboard() {
        val prefs = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val dataString = prefs.getString("all_expenses", "[]")
        val jsonArray = JSONArray(dataString)
        val budgetLimit = prefs.getFloat("budget_limit", 10000f).toDouble()

        var total = 0.0
        var essential = 0.0
        var nonEssential = 0.0
        val expenseList = mutableListOf<JSONObject>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            total += amount
            if (obj.optBoolean("isEssential", true)) {
                essential += amount
            } else {
                nonEssential += amount
            }
            expenseList.add(obj)
        }

        // Sort list by timestamp (newest first)
        expenseList.sortByDescending { it.optLong("timestamp", 0L) }

        val remaining = budgetLimit - total

        // Check for over-budget
        if (total > budgetLimit && budgetLimit > 0) {
            sendOverBudgetNotification(total, budgetLimit)
        }

        // Update Summary Cards
        tvTotalExpenses.text = "₹ ${String.format("%.2f", total)}"
        tvMoneyWasted.text = "₹ ${String.format("%.2f", nonEssential)}"
        
        tvRemainingBudget.text = "₹ ${String.format("%.2f", remaining)}"
        if (remaining < 0) {
            tvRemainingBudget.setTextColor(ContextCompat.getColor(this, R.color.leakage_red))
        } else {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            tvRemainingBudget.setTextColor(typedValue.data)
        }

        updateChartData(essential, nonEssential, remaining)
        
        // Update List - only show top 5 for dashboard preview
        val previewList = if (expenseList.size > 5) expenseList.take(5) else expenseList
        rvRecentExpenses.adapter = ExpenseAdapter(previewList)
    }

    private fun sendOverBudgetNotification(total: Double, limit: Double) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Budget Alert!")
            .setContentText("Your expenses (₹${String.format("%.2f", total)}) have exceeded your budget (₹${String.format("%.2f", limit)}).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(1, builder.build())
            }
        }
    }

    private fun updateChartData(essential: Double, nonEssential: Double, remaining: Double) {
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        val context = pieChart.context

        if (essential > 0) {
            entries.add(PieEntry(essential.toFloat(), "Essential"))
            colors.add(ContextCompat.getColor(context, R.color.essential_green))
        }
        if (nonEssential > 0) {
            entries.add(PieEntry(nonEssential.toFloat(), "Wasted"))
            colors.add(ContextCompat.getColor(context, R.color.leakage_red))
        }
        if (remaining > 0) {
            entries.add(PieEntry(remaining.toFloat(), "Unspent"))
            colors.add(ContextCompat.getColor(context, R.color.unspent_gray))
        }

        if (entries.isEmpty() && remaining >= 0) {
            pieChart.clear()
            pieChart.setNoDataText("No data available")
            return
        }

        val textColor = if ((context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        pieChart.setCenterTextColor(textColor)
        pieChart.legend.textColor = textColor

        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 3f
        }

        pieChart.data = PieData(dataSet)
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    // RecyclerView Adapter for Expense Table
    class ExpenseAdapter(private val list: List<JSONObject>) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        
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
