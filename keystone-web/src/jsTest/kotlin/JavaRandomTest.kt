import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Treasure, quests and villagers are placed with seeded java.util.Random, so the web
 * port must produce exactly the JVM's numbers. Expected values come from a real JVM.
 */
class JavaRandomTest {
    @Test
    fun matchesTheJvm() {
        val r = java.util.Random(4242)
        val got = buildList {
            repeat(3) { add(r.nextInt().toString()) }
            repeat(3) { add(r.nextInt(100).toString()) }
            repeat(2) { add(r.nextInt(64).toString()) }
            repeat(3) { add(r.nextFloat().toString()) }
            add(r.nextBoolean().toString())
            add(r.nextLong().toString())
            add(r.nextDouble().toString())
            val q = java.util.Random(150150L * 31L + 7L)
            add(q.nextInt(1000).toString())
            add(q.nextFloat().toString())
            add(q.nextLong(1000000L).toString())
        }
        val jvm = "476620258,-1731841883,142624746,50,44,45,7,34,0.16408134,0.0939284,0.8965683,true," +
            "5938194579902447209,0.7736435343095579,98,0.75364107,302071"
        val expected = jvm.split(",")
        for (i in expected.indices) {
            val e = expected[i]
            val g = got[i]
            if (e.contains('.')) assertEquals(e.toDouble(), g.toDouble(), 1e-7, "value $i") else assertEquals(e, g, "value $i")
        }
    }
}
