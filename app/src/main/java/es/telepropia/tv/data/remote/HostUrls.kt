package es.telepropia.tv.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object HostUrls {

    // Puerto siempre explícito para que el mismo host escrito de dos formas no entre dos veces.
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        return "${url.scheme}://$host:${url.port}"
    }

    // El resolver firma playableUrl con localhost aunque corra en otra máquina; en el stick eso es el propio stick.
    // Se trabaja con encodedQuery: el `url` interno va codificado y decodificarlo lo partiría en el primer `&`.
    fun rewritePlayable(playableUrl: String, baseUrl: String): String? {
        val playable = playableUrl.toHttpUrlOrNull() ?: return null
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        return HttpUrl.Builder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(playable.encodedPath)
            .encodedQuery(playable.encodedQuery)
            .build()
            .toString()
    }

    // §5 no da nombre de fuente; el host:puerto es lo único que distingue un resolver de otro.
    fun label(baseUrl: String): String = baseUrl.substringAfter("://")

    fun endpoint(baseUrl: String, path: String, query: List<Pair<String, String>>): HttpUrl? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder().encodedPath("/").addPathSegments(path)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }
}
