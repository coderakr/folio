package com.sandboxuser.pdfviewer.data

/** Cycle-safe navigation also tolerates missing parents in older or recovered libraries. */
fun List<Folder>.folderTrail(id: String?): List<Folder> {
    val byId = associateBy { it.id }
    val seen = mutableSetOf<String>()
    val path = mutableListOf<Folder>()
    var next = id
    while(next != null && seen.add(next)) {
        val folder = byId[next] ?: break
        path += folder; next = folder.parentId
    }
    return path.asReversed()
}
fun List<Folder>.folderPath(id: String?): String = folderTrail(id).joinToString(" / ") { it.name }.ifEmpty { "Unfiled" }
fun List<Folder>.subtreeIds(id: String): Set<String> {
    val children = groupBy { it.parentId }
    val result = mutableSetOf<String>()
    val pending = ArrayDeque<String>().apply { add(id) }
    while(pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if(result.add(current)) children[current].orEmpty().forEach { pending.add(it.id) }
    }
    return result
}
