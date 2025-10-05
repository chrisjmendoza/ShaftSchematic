package com.android.shaftschematic.model

// Unify diameter access for renderers.
// Body: use diaMm; ThreadSpec: use majorDiaMm; Liner already has odMm.

val Body.odMm: Float
    get() = this.diaMm

val Threads.odMm: Float
    get() = this.majorDiaMm
