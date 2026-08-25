package com.titanfortune.game

enum class GameEdition {
    V1_SIMPLE, V2_STANDARD, V3_COMPLETE;

    val title get() = "TITAN FORTUNE"
    val advanced get() = this != V1_SIMPLE
    val complete get() = this == V3_COMPLETE
    val waves get() = when (this) {
        V1_SIMPLE -> 5
        V2_STANDARD -> 8
        V3_COMPLETE -> 10
    }
}
