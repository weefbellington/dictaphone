package com.davidstemmer.dictaphone.switchboard

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

fun interface ActionDispatcher {
    fun dispatch(message: SwitchboardMessage)
}

fun interface OutputDispatcher {
    fun output(output: SwitchboardOutput)
}

interface RouterScope: CoroutineScope, ActionDispatcher, OutputDispatcher

fun CoroutineScope.createRouterScope(dispatchCallback: (SwitchboardMessage) -> Unit) = object:
    RouterScope {
    override val coroutineContext: CoroutineContext
        get() = this@createRouterScope.coroutineContext

    override fun dispatch(message: SwitchboardMessage) {
        dispatchCallback(message)
    }

    override fun output(output: SwitchboardOutput) {
        dispatch(SendOutput(output))
    }
}

interface SwitchboardRouter {
    fun RouterScope.tryHandle(state: Switchboard.State, message: SwitchboardMessage)
}

interface EffectRouter<in T: SwitchboardMessage>: SwitchboardRouter {
    fun canHandle(message: SwitchboardMessage): Boolean
    fun RouterScope.handle(state: Switchboard.State, action: T)
    override fun RouterScope.tryHandle(state: Switchboard.State, message: SwitchboardMessage) {
        if (canHandle(message)) {
            @Suppress("UNCHECKED_CAST")
            handle(state, message as T)
        }
    }
}