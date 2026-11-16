/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.subscriber

/** 不支持当前运行环境时返回的订阅端空实现。 */
class EmptyLyriconSubscriber(
    override val subscriberInfo: SubscriberInfo
) : LyriconSubscriber {
    override fun addConnectionListener(listener: ConnectionListener) {}
    override fun removeConnectionListener(listener: ConnectionListener) {}
    override fun subscribeActivePlayer(listener: ActivePlayerListener) = false
    override fun unsubscribeActivePlayer(listener: ActivePlayerListener) = false
    override fun register() {}
    override fun unregister() {}
    override fun destroy() {}
}