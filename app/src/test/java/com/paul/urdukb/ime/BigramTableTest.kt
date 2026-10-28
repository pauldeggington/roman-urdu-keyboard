package com.paul.urdukb.ime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BigramTableTest {
    @Test
    fun tableIsWellFormed() {
        val lines = File("src/main/assets/dict/bigrams.tsv").readLines().filter { it.isNotBlank() }
        assertTrue(lines.size > 100)
        val seen = HashSet<String>()
        for (ln in lines) {
            val parts = ln.split('\t')
            assertTrue("bad line: $ln", parts.size == 2 && parts[1].split(' ').isNotEmpty())
            assertTrue("duplicate: ${parts[0]}", seen.add(parts[0]))
        }
        assertTrue("sentence start missing", "<s>" in seen && "thank" in seen)
    }
}
