package cz.pshornicek.vyveska.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                if (userManager != null && !userManager.isUserUnlocked) {
                    // Zařízení ještě nebylo po restartu odemčeno; úkol naplánujeme přes USER_PRESENT / BOOT_COMPLETED
                    return
                }
                ObituarySyncWorker.schedulePeriodicSync(context, replaceExisting = true)
                ObituarySyncWorker.scheduleAlarmBackup(context)
                ObituarySyncWorker.enqueueImmediateSync(context)
            } catch (_: Exception) {
            }
        }
    }
}
