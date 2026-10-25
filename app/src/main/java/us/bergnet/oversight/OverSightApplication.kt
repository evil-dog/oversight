package us.bergnet.oversight

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import us.bergnet.oversight.data.repository.PersistenceManager

/**
 * Owns persistence for the whole process: state is loaded once and a single auto-save runs for the
 * process lifetime. The service and activity come and go (service restarts, activity recreation)
 * without reloading disk state over newer in-memory changes or cancelling pending saves.
 */
class OverSightApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()

    lateinit var persistence: PersistenceManager
        private set

    /** Completes once persisted state is in OverlayStateStore. */
    val stateLoaded: Deferred<Unit> get() = loaded

    override fun onCreate() {
        super.onCreate()
        persistence = PersistenceManager(this)
        appScope.launch {
            persistence.loadAll()
            loaded.complete(Unit)
            persistence.startAutoSave(appScope)
        }
    }

    companion object {
        fun from(context: Context): OverSightApplication = context.applicationContext as OverSightApplication
    }
}
