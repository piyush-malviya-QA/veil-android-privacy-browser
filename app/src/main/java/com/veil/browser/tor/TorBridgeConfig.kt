package com.veil.browser.tor

enum class BridgeTransport { DIRECT, OBFS4, SNOWFLAKE }

object TorBridgeConfig {
    fun buildTorrcSnippet(transport: BridgeTransport): String = when (transport) {
        BridgeTransport.DIRECT -> "# Direct Tor guard connection (single recommended default)"
        BridgeTransport.OBFS4 -> """
            UseBridges 1
            ClientTransportPlugin obfs4 exec /data/app/libobfs4proxy.so
        """.trimIndent()
        BridgeTransport.SNOWFLAKE -> """
            UseBridges 1
            ClientTransportPlugin snowflake exec /data/app/libsnowflake.so
        """.trimIndent()
    }
}
