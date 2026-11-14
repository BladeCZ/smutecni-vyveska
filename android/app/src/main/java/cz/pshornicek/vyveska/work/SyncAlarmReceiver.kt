package cz.pshornicek.vyveska.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            ObituarySyncWorker.schedulePeriodicSync(context)
            ObituarySyncWorker.scheduleAlarmBackup(context)
            ObituarySyncWorker.enqueueImmediateSync(context)
        } catch (_: Exception) {
        }
    }
}
