package dev.srsatt.keyboard.layout.qmk

enum class QmkDelivery(val text: String) {
    PAIRED("paired"),
    IMMEDIATE("immediate"),
}

data class QmkLayerEmission(
    val id: Int,
    val name: String,
    val tokens: List<String>,
)

data class QmkChordEmission(
    val id: String,
    val members: List<String>,
    val output: String,
    val delivery: QmkDelivery = QmkDelivery.PAIRED,
)

class QmkEmissionPlan(
    layers: Iterable<QmkLayerEmission>,
    chordRows: Iterable<QmkChordEmission>,
) {
    val layers = layers.toList().sortedBy(QmkLayerEmission::id)
    val chordRows = chordRows.map { row -> row.copy(members = row.members.sorted()) }.distinct().sortedBy(QmkChordEmission::id)

    init {
        require(this.layers.distinctBy(QmkLayerEmission::id).size == this.layers.size) { "QMK layer IDs must be unique" }
        require(this.layers.distinctBy(QmkLayerEmission::name).size == this.layers.size) { "QMK layer names must be unique" }
        require(this.layers.all { it.name.isNotBlank() && it.tokens.isNotEmpty() && it.tokens.none(String::isBlank) }) {
            "QMK layers require a name and at least one non-blank token"
        }
        require(this.chordRows.distinctBy(QmkChordEmission::id).size == this.chordRows.size) {
            "QMK chord IDs must be unique after identical legacy rows are deduplicated"
        }
        require(this.chordRows.all { it.id.isNotBlank() && it.output.isNotBlank() && it.members.isNotEmpty() }) {
            "QMK chord rows require an ID, output, and at least one member"
        }
    }

    fun renderText(): String = buildString {
        layers.forEach { layer ->
            append("layer ").append(layer.id).append(' ').append(layer.name).append(": ")
            appendLine(layer.tokens.joinToString(" "))
        }
        chordRows.forEach { row ->
            append(if (row.members.size == 1) "fallback " else "chord ")
            append(row.id).append(" [").append(row.members.joinToString("+")).append("] -> ")
            append(row.output).append(' ').appendLine(row.delivery.text)
        }
    }
}
