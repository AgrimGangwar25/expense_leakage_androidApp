package com.example.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "transaction_alerts"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "SMS Received Broadcast triggered")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                
                Log.d("SmsReceiver", "Message from $sender: $messageBody")

                if (isTransaction(messageBody)) {
                    Log.d("SmsReceiver", "Transaction detected!")
                    val amount = extractAmount(messageBody)
                    
                    showNotification(context, amount, sender, messageBody)
                    
                    val popupIntent = Intent(context, CategoryActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        putExtra("amount", amount)
                        putExtra("sender", sender)
                        putExtra("message", messageBody)
                    }
                    context.startActivity(popupIntent)
                } else {
                    Log.d("SmsReceiver", "Not a transaction message.")
                }
            }
        }
    }

      private fun isTransaction(message: String): Boolean {
        val msg = message.lowercase()
        val keywords = listOf("debited", "spent", "paid", "sent", "withdrawn", "deducted", "transaction", "vpa", "upi", "purchased")
        val hasKeyword = keywords.any { msg.contains(it) }
        val hasCurrency = msg.contains("rs") || msg.contains("inr") || msg.contains("₹") || msg.contains("$")
        
        return hasKeyword && hasCurrency
    }

    private fun extractAmount(message: String): String {
        val pattern = Pattern.compile("(?i)(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        val matcher = pattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1)?.replace(",", "") ?: "0.0"
        } else {
            "0.0"
        }
    }

    private fun showNotification(context: Context, amount: String, sender: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transaction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows alerts for detected transaction SMS"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, CategoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("amount", amount)
            putExtra("sender", sender)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Transaction Detected")
            .setContentText("Amount: Rs. $amount from $sender")
            .setPriority(NotificationCompat.PRIORITY_MAX) // Max priority for Heads-up
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // Sound and vibration
            .setFullScreenIntent(pendingIntent, true)      // Critical for immediate pop-up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
