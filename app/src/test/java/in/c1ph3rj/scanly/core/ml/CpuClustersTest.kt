package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuClustersTest {
    @Test
    fun `picks the little cores of a big little phone`() {
        // Snapdragon 865 (Galaxy S20 FE): 4 x Kryo 585 Silver + 4 x Cortex-A77.
        val cpuInfo = (0..7).joinToString("\n\n") { cpu ->
            val (variant, part) = if (cpu < 4) "0xd" to "0x805" else "0x1" to "0xd0d"
            processor(cpu, implementer = "0x51", variant = variant, part = part)
        }
        val frequencies = mapOf(0 to 1_804_800L, 1 to 1_804_800L, 2 to 1_804_800L, 3 to 1_804_800L) +
            (4..6).associateWith { 2_419_200L } + (7 to 2_841_600L)

        val mask = CpuClusters.littleCoreMask(cpuInfo) { frequencies[it] }

        assertEquals(0x0FL, mask)
    }

    @Test
    fun `separates the prime core type from the performance cores`() {
        // Tensor-style 2 x X1 + 2 x A76 + 4 x A55: only the A55s share a core type with the slowest.
        val cpuInfo = (0..7).joinToString("\n\n") { cpu ->
            val part = when (cpu) {
                in 0..3 -> "0xd05"
                in 4..5 -> "0xd0b"
                else -> "0xd44"
            }
            processor(cpu, implementer = "0x41", variant = "0x1", part = part)
        }
        val frequencies = (0..3).associateWith { 1_803_000L } + (4..5).associateWith { 2_253_000L } +
            (6..7).associateWith { 2_802_000L }

        assertEquals(0x0FL, CpuClusters.littleCoreMask(cpuInfo) { frequencies[it] })
    }

    @Test
    fun `does not pin when every core has the same type`() {
        val cpuInfo = (0..3).joinToString("\n\n") { processor(it, implementer = "0x41", variant = "0x0", part = "0xd03") }

        assertNull(CpuClusters.littleCoreMask(cpuInfo) { 1_500_000L })
    }

    @Test
    fun `does not pin when cpuinfo has no per core parts`() {
        val cpuInfo = """
            Processor	: AArch64 Processor rev 4 (aarch64)
            processor	: 0
            processor	: 1
            Hardware	: Qualcomm Technologies, Inc
        """.trimIndent()

        assertNull(CpuClusters.littleCoreMask(cpuInfo) { 1_500_000L })
    }

    @Test
    fun `cores with unknown frequencies are never picked as the slowest`() {
        val cpuInfo = (0..3).joinToString("\n\n") { cpu ->
            processor(cpu, implementer = "0x41", variant = "0x0", part = if (cpu < 2) "0xd05" else "0xd0d")
        }

        assertEquals(0x0CL, CpuClusters.littleCoreMask(cpuInfo) { if (it >= 2) 1_000_000L else null })
    }

    private fun processor(cpu: Int, implementer: String, variant: String, part: String): String = """
        processor	: $cpu
        BogoMIPS	: 38.40
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU implementer	: $implementer
        CPU architecture: 8
        CPU variant	: $variant
        CPU part	: $part
        CPU revision	: 0
    """.trimIndent()
}
