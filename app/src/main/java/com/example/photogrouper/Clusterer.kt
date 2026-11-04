package com.example.photogrouper

import kotlin.math.sqrt

object Clusterer {

    private fun cosine(a: Map<String, Float>, b: Map<String, Float>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var dot = 0f
        for ((k, v) in a) b[k]?.let { dot += v * it }
        val na = sqrt(a.values.sumOf { (it * it).toDouble() }).toFloat()
        val nb = sqrt(b.values.sumOf { (it * it).toDouble() }).toFloat()
        return if (na == 0f || nb == 0f) 0f else dot / (na * nb)
    }

    private class Cluster(val sum: MutableMap<String, Float>, val members: MutableList<Photo>)

    /**
     * تجميع بسيط: كل صورة تنضم لأقرب مجموعة لو التشابه >= threshold،
     * وإلا تبدأ مجموعة جديدة. المجموعات اللي فيها صورة واحدة بتتشال.
     */
    fun cluster(
        photos: List<Photo>,
        vectors: Map<Long, Map<String, Float>>,
        threshold: Float,
        minSize: Int = 2
    ): List<Group> {
        val clusters = ArrayList<Cluster>()
        for (p in photos) {
            val v = vectors[p.id] ?: continue
            if (v.isEmpty()) continue
            var best: Cluster? = null
            var bestSim = 0f
            for (c in clusters) {
                val s = cosine(v, c.sum)
                if (s > bestSim) { bestSim = s; best = c }
            }
            if (best != null && bestSim >= threshold) {
                best.members += p
                for ((k, x) in v) best.sum[k] = (best.sum[k] ?: 0f) + x
            } else {
                clusters += Cluster(v.toMutableMap(), mutableListOf(p))
            }
        }
        return clusters
            .filter { it.members.size >= minSize }
            .sortedByDescending { it.members.size }
            .mapIndexed { i, c ->
                val label = c.sum.maxByOrNull { it.value }?.key ?: "Group"
                val safe = label.replace(Regex("[^\\p{L}\\p{N}]+"), "_")
                Group("${i + 1}_$safe", c.members)
            }
    }
}
