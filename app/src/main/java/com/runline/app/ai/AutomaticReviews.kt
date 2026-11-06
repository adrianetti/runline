package com.runline.app.ai

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.runline.app.MainActivity
import com.runline.app.R
import com.runline.app.core.RunSession
import com.runline.app.data.RunStore
import com.runline.app.i18n.AppLanguage
import java.io.File

object AutomaticReviews {
    const val EXTRA_RUN = "runline.review.run"
    private const val JOB_ID = 12012
    private const val CHANNEL = "run-reviews"
    fun queue(context: Context) = ReviewQueue(File(context.filesDir, "review-queue"))
    fun enqueue(context: Context, run: RunSession) {
        val config = AiSettings.load(context)
        if (!config.enabled || !config.automaticReviews || !AiSettings.hasKey(context) || AiReviewStore(context).get(run.id) != null) return
        queue(context).enqueue(run.id, System.currentTimeMillis())
        schedule(context)
    }
    fun reconcile(context: Context) {
        val q = queue(context)
        q.all().filter { it.state == "running" && !AiWork.isReviewRunning(it.runId) }.forEach { q.finish(it.runId, "INTERRUPTED") }
        schedule(context)
    }
    fun delete(context: Context, id: String) { queue(context).delete(id); NotificationManagerCompat.from(context).cancel(id, 12); schedule(context) }
    fun schedule(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java)
        val config = AiSettings.load(context)
        if (!config.enabled || !config.automaticReviews || !AiSettings.hasKey(context)) {
            jobs.cancel(JOB_ID)
            queue(context).all().filter { it.state == "pending" }.forEach { queue(context).finish(it.runId, "DISABLED") }
            return
        }
        val next = queue(context).all().filter { it.state == "pending" }.minByOrNull { it.dueAt } ?: return
        // Do not replace a running job when the app opens or another activity finishes.
        if (jobs.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, ReviewJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setMinimumLatency((next.dueAt - System.currentTimeMillis()).coerceAtLeast(0))
            .setExtras(PersistableBundle().apply { putString("runId", next.runId) }).build()
        if (jobs.schedule(job) == JobScheduler.RESULT_FAILURE) queue(context).finish(next.runId, "SCHEDULER")
    }
    fun notifyReady(context: Context, id: String, review: RunAiReview) {
        val config = AiSettings.load(context)
        if (!config.enabled || !config.automaticReviews) return
        val local = AppLanguage.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, local.getString(R.string.review_v12_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, MainActivity::class.java).setAction("com.runline.app.REVIEW.$id")
            .putExtra(EXTRA_RUN, id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 12, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val card = review.card
        val title = card?.headline ?: local.getString(R.string.ai_review_title)
        val text = card?.nextSession ?: review.text.take(200)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_runline)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, 12, notification) }
    }
}

/** Android keeps pending work across process death/reboot; HTTP attempts are not automatically replayed. */
class ReviewJobService : JobService() {
    private var stopped = false
    private fun continueLater(): Boolean {
        android.os.Handler(mainLooper).postDelayed({ AutomaticReviews.schedule(applicationContext) }, 1000)
        return false
    }
    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val id = params.extras.getString("runId") ?: return false
        val config = AiSettings.load(this)
        val q = AutomaticReviews.queue(this)
        if (!config.enabled || !config.automaticReviews || !AiSettings.hasKey(this)) { q.finish(id, "DISABLED"); return continueLater() }
        val run = runCatching { RunStore(this).get(id) }.getOrNull()
        if (run == null) { q.delete(id); return continueLater() }
        AiReviewStore(this).get(id)?.let { q.finish(id); AutomaticReviews.notifyReady(this, id, it); return continueLater() }
        if (!q.claim(id, System.currentTimeMillis())) return continueLater()
        AiWork.review(this, run, automatic = true) { result ->
            q.finish(id, result.exceptionOrNull()?.let { (it as? AiFailure)?.reason?.name ?: "RESPONSE" })
            result.getOrNull()?.let { AutomaticReviews.notifyReady(this, id, it) }
            fun done() {
                if (!stopped) jobFinished(params, false)
                // The completed job must leave the scheduler before scheduling the next one.
                android.os.Handler(mainLooper).postDelayed({ AutomaticReviews.schedule(applicationContext) }, 1000)
            }
            // Still inside this network-constrained job: refresh the AI-maintained profile.
            if (!stopped && RunnerProfileStore(this).load().aiManaged) AiWork.updateProfile(this) { done() } else done()
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        params.extras.getString("runId")?.let { AutomaticReviews.queue(this).finish(it, "INTERRUPTED") }
        return false
    }
}
