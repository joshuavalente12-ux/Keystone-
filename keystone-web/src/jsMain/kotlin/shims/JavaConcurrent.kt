package java.util.concurrent

/**
 * Browsers run the game on one thread, so the JVM's thread-safe collections become plain
 * ones, and background work is queued and run in small slices between frames (see [WebTasks]).
 */
fun interface Runnable {
    fun run()
}

fun interface ThreadFactory {
    fun newThread(r: Runnable): Any?
}

interface ExecutorService {
    fun execute(command: Runnable)
    fun shutdown() {}
    fun shutdownNow(): List<Runnable> = emptyList()
}

object Executors {
    fun newSingleThreadExecutor(factory: ThreadFactory? = null): ExecutorService = object : ExecutorService {
        override fun execute(command: Runnable) = WebTasks.post { command.run() }
    }

    fun newFixedThreadPool(n: Int, factory: ThreadFactory? = null): ExecutorService = newSingleThreadExecutor(factory)
}

/** Work queued by "background threads", run a few milliseconds at a time by the frame loop. */
object WebTasks {
    private val queue = ArrayDeque<() -> Unit>()

    fun post(task: () -> Unit) {
        queue.addLast(task)
    }

    val pending: Int get() = queue.size

    /** Runs queued work until [budgetMs] has passed. Returns how many tasks ran. */
    fun runFor(budgetMs: Double, now: () -> Double): Int {
        val start = now()
        var ran = 0
        while (queue.isNotEmpty()) {
            val task = queue.removeFirst()
            try {
                task()
            } catch (t: Throwable) {
                console.error("Background task failed", t)
            }
            ran++
            if (now() - start >= budgetMs) break
        }
        return ran
    }
}

open class ConcurrentHashMap<K, V> : HashMap<K, V>() {
    companion object {
        fun <K> newKeySet(): MutableSet<K> = HashSet()
    }
}

class ConcurrentLinkedQueue<E>(private val items: ArrayDeque<E> = ArrayDeque()) : MutableCollection<E> by items {
    fun poll(): E? = items.removeFirstOrNull()
    fun peek(): E? = items.firstOrNull()
    fun offer(e: E): Boolean = items.add(e)
}

/** Iterating sees a snapshot, so the list can change while someone loops over it. */
class CopyOnWriteArrayList<E> : AbstractMutableList<E>() {
    private var items = ArrayList<E>()

    override val size: Int get() = items.size
    override fun get(index: Int): E = items[index]

    override fun set(index: Int, element: E): E {
        val copy = ArrayList(items)
        val old = copy.set(index, element)
        items = copy
        return old
    }

    override fun add(index: Int, element: E) {
        val copy = ArrayList(items)
        copy.add(index, element)
        items = copy
    }

    override fun removeAt(index: Int): E {
        val copy = ArrayList(items)
        val old = copy.removeAt(index)
        items = copy
        return old
    }

    override fun iterator(): MutableIterator<E> {
        val snap = items.iterator()
        return object : MutableIterator<E> {
            override fun hasNext() = snap.hasNext()
            override fun next() = snap.next()
            override fun remove() = throw UnsupportedOperationException()
        }
    }
}
