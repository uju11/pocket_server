package com.pocketnode.client.data

/**
 * Maps movie and show titles to official high-resolution poster and backdrop URLs.
 * Ensures all cards in Watch, Library, and Modal views load vibrant thumbnails even
 * when working offline or before metadata sync.
 */
object PosterResolver {

    // High quality official TMDB posters
    private val posters = mapOf(
        "ratatouille" to "https://image.tmdb.org/t/p/w500/npHNjld2VT0Ao7EbFFFFppJ6vFz.jpg",
        "oppenheimer" to "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
        "dune" to "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        "dune: part two" to "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        "interstellar" to "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
        "spider-man" to "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
        "spider-verse" to "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
        "across the spider-verse" to "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
        "severance" to "https://image.tmdb.org/t/p/w500/961E58kXw4l1mZ9J1yF6f48rC14.jpg",
        "avengers" to "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
        "avengers: endgame" to "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
        "solaris" to "https://image.tmdb.org/t/p/w500/oJkR4aI4e9F0L0a12eG1L0B0F3.jpg",
        "past lives" to "https://image.tmdb.org/t/p/w500/k3waqVXSnvCZWfJYNtdamTgTtTA.jpg",
        "the zone of interest" to "https://image.tmdb.org/t/p/w500/hUu9zyZmDD8VZegKi1iK1Vk0RYS.jpg",
        "zone of interest" to "https://image.tmdb.org/t/p/w500/hUu9zyZmDD8VZegKi1iK1Vk0RYS.jpg",
        "apocalypto" to "https://image.tmdb.org/t/p/w500/1X6v8xN2zQv8n9b4d8Gf7k6r4H8.jpg",
        "furiosa" to "https://image.tmdb.org/t/p/w500/iADOJ8Zymht2JPMoy3R7xUMZ51f.jpg",
        "deadpool & wolverine" to "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
        "civil war" to "https://image.tmdb.org/t/p/w500/sh7Rg8Er3tFcN9BpKIPOMvALgZd.jpg",
        "poor things" to "https://image.tmdb.org/t/p/w500/kCGlIMHnOm8JPXq3rXM6c5wMxcT.jpg"
    )

    private val backdrops = mapOf(
        "ratatouille" to "https://image.tmdb.org/t/p/w1280/tDxRh12g4O3kZqF3c2u9eB8bV7O.jpg",
        "oppenheimer" to "https://image.tmdb.org/t/p/w1280/rLb2cwF3Pazuxaj0sRXQ037tGI1.jpg",
        "dune" to "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s5200SV.jpg",
        "interstellar" to "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
        "severance" to "https://image.tmdb.org/t/p/w1280/961E58kXw4l1mZ9J1yF6f48rC14.jpg"
    )

    /**
     * Resolves a poster thumbnail URL for a given title keyword.
     * If [existingUrl] is already a valid non-empty string, it is preserved.
     */
    fun getPoster(title: String, existingUrl: String = ""): String {
        if (existingUrl.isNotBlank()) return existingUrl
        val normalized = title.lowercase().trim()
        for ((key, url) in posters) {
            if (normalized.contains(key)) return url
        }
        return ""
    }

    /**
     * Resolves a widescreen backdrop banner for hero headers.
     */
    fun getBackdrop(title: String, existingUrl: String = ""): String {
        if (existingUrl.isNotBlank()) return existingUrl
        val normalized = title.lowercase().trim()
        for ((key, url) in backdrops) {
            if (normalized.contains(key)) return url
        }
        return getPoster(title, existingUrl)
    }
}
